package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.local.AppDatabase
import com.example.smartmicrogrid.data.remote.dto.LoginResponse
import com.example.smartmicrogrid.data.remote.dto.RegisterProsumerRequest
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.AuthRepository
import com.example.smartmicrogrid.data.repository.quietly
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.SessionManager
import com.example.smartmicrogrid.utils.Validators
import kotlinx.coroutines.launch

/**
 * File: AuthViewModel.kt
 * Purpose: Drives the Login and Register screens. Validates input, calls AuthRepository,
 *          persists the session on successful login, and exposes the outcome as
 *          LiveData<AuthState> for the Activities to observe.
 * Author: Mobile Team
 * Date: 2026
 *
 * Note for Activities: LiveData replays its last value (e.g. after rotation), so call
 * resetLoginState() / resetRegisterState() once a Success or Error has been handled,
 * otherwise the same toast/navigation fires again.
 */

// ==================== STATE ====================

sealed class AuthState {
    /** Nothing in flight, nothing to show. Initial value, and the value after a reset. */
    object Idle : AuthState()

    /** A request is in flight — show spinner, disable inputs. */
    object Loading : AuthState()

    /**
     * Login: [role] is the backend role string (see Constants.ROLE_*), [name] the display name.
     * Register: [role] is always Constants.ROLE_PROSUMER, [name] the registered name.
     */
    data class Success(val role: String, val name: String) : AuthState()

    /** Validation or API failure with a user-readable [message]. */
    data class Error(val message: String) : AuthState()
}

// ==================== VIEWMODEL ====================

/**
 * The repository, session and cache-wipe step are constructor parameters that default to the real
 * ones, so the app behaves exactly as before while a unit test can pass fakes. @JvmOverloads keeps
 * the plain (Application) constructor that Android's default ViewModel factory looks for, so
 * `by viewModels()` in the Activities is unaffected.
 */
class AuthViewModel @JvmOverloads constructor(
    application: Application,
    private val repo: AuthRepository = AuthRepository(application.applicationContext),
    private val session: SessionManager = SessionManager(application.applicationContext),
    private val cacheCleaner: suspend () -> Unit = { AppDatabase.getInstance(application).clearCache() }
) : AndroidViewModel(application) {

    private val _loginState = MutableLiveData<AuthState>(AuthState.Idle)
    val loginState: LiveData<AuthState> = _loginState

    private val _registerState = MutableLiveData<AuthState>(AuthState.Idle)
    val registerState: LiveData<AuthState> = _registerState

    // ==================== LOGIN ====================

    /**
     * Validates, calls POST /api/auth/login, and on success stores the session.
     * For prosumers it then fetches the profile once to store the NIC (not in the login
     * response). A failed profile fetch does not fail the login — the NIC is display-only.
     */
    fun login(email: String, password: String) {
        if (_loginState.value is AuthState.Loading) return

        val cleanEmail = email.trim()
        validateLogin(cleanEmail, password)?.let {
            _loginState.value = AuthState.Error(it)
            return
        }

        _loginState.value = AuthState.Loading
        viewModelScope.launch {
            when (val result = repo.login(cleanEmail, password)) {
                is ApiResult.Success -> onLoginSuccess(result.data)
                is ApiResult.Error -> _loginState.value = AuthState.Error(result.message)
            }
        }
    }

    private suspend fun onLoginSuccess(login: LoginResponse) {
        // A new login starts with an empty offline cache, so it can never show the previous
        // user's bookings or profile (logout already clears it; this is the backstop, and it is
        // awaited so nothing can be written before it finishes).
        quietly { cacheCleaner() }

        // Save first: the AuthInterceptor reads the JWT from SessionManager for the next call.
        persistSession(login, nic = null)

        if (login.role == Constants.ROLE_PROSUMER) {
            val profile = repo.getMyProfile()
            if (profile is ApiResult.Success) {
                persistSession(login, nic = profile.data.nic)
            }
        }

        _loginState.value = AuthState.Success(role = login.role, name = login.name)
    }

    private fun persistSession(login: LoginResponse, nic: String?) {
        session.saveSession(
            jwt = login.token,
            userType = login.role,
            email = login.email,
            name = login.name,
            nic = nic,
            stationId = login.stationId,
            expiresAt = login.expiresAt
        )
    }

    fun resetLoginState() {
        _loginState.value = AuthState.Idle
    }

    // ==================== REGISTER ====================

    /**
     * Validates and calls POST /api/prosumers/register. No session is saved: the account is
     * created as "pending" and cannot log in until Backoffice approves it.
     */
    fun registerProsumer(request: RegisterProsumerRequest) {
        if (_registerState.value is AuthState.Loading) return

        val cleaned = request.copy(
            nic = request.nic.trim(),
            name = request.name.trim(),
            email = request.email.trim(),
            contactNumber = request.contactNumber.trim(),
            address = request.address.trim()
        )
        validateRegister(cleaned)?.let {
            _registerState.value = AuthState.Error(it)
            return
        }

        _registerState.value = AuthState.Loading
        viewModelScope.launch {
            when (val result = repo.registerProsumer(cleaned)) {
                is ApiResult.Success -> _registerState.value =
                    AuthState.Success(role = Constants.ROLE_PROSUMER, name = result.data.name)
                is ApiResult.Error -> _registerState.value = AuthState.Error(result.message)
            }
        }
    }

    fun resetRegisterState() {
        _registerState.value = AuthState.Idle
    }

    // ==================== VALIDATION ====================
    // Basic shape checks only — every real rule is enforced by the backend.

    private fun validateLogin(email: String, password: String): String? = when {
        email.isEmpty() -> "Email is required."
        !Validators.isValidEmail(email) -> "Enter a valid email address."
        password.isEmpty() -> "Password is required."
        else -> null
    }

    private fun validateRegister(request: RegisterProsumerRequest): String? = when {
        request.nic.isEmpty() -> "NIC is required."
        request.name.isEmpty() -> "Full name is required."
        request.email.isEmpty() -> "Email is required."
        !Validators.isValidEmail(request.email) -> "Enter a valid email address."
        request.contactNumber.isEmpty() -> "Contact number is required."
        request.address.isEmpty() -> "Address is required."
        request.panelCapacityKw <= 0.0 -> "Panel capacity must be greater than 0."
        request.password.length < Constants.MIN_PASSWORD_LENGTH ->
            "Password must be at least ${Constants.MIN_PASSWORD_LENGTH} characters."
        else -> null
    }
}
