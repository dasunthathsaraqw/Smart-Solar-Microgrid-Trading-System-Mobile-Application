package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ChangePasswordRequest
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.UpdateOwnProfileRequest
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.AuthRepository
import com.example.smartmicrogrid.data.repository.CachedResult
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * File: ProfileViewModel.kt
 * Purpose: Drives the prosumer's Profile screen: loads the profile (LiveData<ProfileState>) and
 *          runs the three account actions — edit profile, change password, request deactivation —
 *          reporting each outcome through one LiveData<ProfileActionState>.
 * Author: Mobile Team
 * Date: 2026
 *
 * Two streams, because the actions happen while the profile is already on screen:
 * - profileState: the profile itself, like DashboardViewModel (no Idle, no reset). It is the
 *   single source of truth for what the form shows: a successful edit or deactivation request
 *   updates it, so the Activity just re-renders it and never has to know which action ran.
 * - actionState:  a one-shot result, like CreateBookingViewModel — call resetActionState() once a
 *   Success or Error has been handled, or a rotation replays it (a second toast).
 *
 * All account rules are the server's. The only client checks are the ones the wire can't express:
 * the confirm-password match (there is no confirm field in the request) and a minimum length.
 */

// ==================== STATE ====================

sealed class ProfileState {
    /** The profile is being loaded — show spinner, hide content/error. */
    object Loading : ProfileState()

    /**
     * The current profile, as last loaded from or saved to the server. [lastSyncedAt] is null for
     * fresh data; when the server couldn't be reached it is the epoch-millisecond time of the
     * fetch the cached copy came from.
     */
    data class Success(
        val profile: ProsumerResponse,
        val lastSyncedAt: Long? = null
    ) : ProfileState() {
        /** True when this is the offline cache's copy, not a fresh fetch. */
        val isCached: Boolean get() = lastSyncedAt != null
    }

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : ProfileState()
}

sealed class ProfileActionState {
    /** Nothing in flight, nothing to show. Initial value, and the value after a reset. */
    object Idle : ProfileActionState()

    /** An action is in flight — disable the buttons. */
    object Loading : ProfileActionState()

    /** The action worked; [message] is the confirmation to show. */
    data class Success(val message: String) : ProfileActionState()

    /**
     * The action was refused or failed: the server's own [message] (wrong current password,
     * email already in use, …) or a client-side check message. [code] is the HTTP status, null
     * for a network failure or a client-side check.
     */
    data class Error(val message: String, val code: Int? = null) : ProfileActionState()
}

// ==================== VIEWMODEL ====================

class ProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = AuthRepository(application.applicationContext)
    private val session = SessionManager(application.applicationContext)

    private val _profileState = MutableLiveData<ProfileState>()
    val profileState: LiveData<ProfileState> = _profileState

    private val _actionState = MutableLiveData<ProfileActionState>(ProfileActionState.Idle)
    val actionState: LiveData<ProfileActionState> = _actionState

    // ==================== LOAD ====================

    /** Fetches the profile. Ignored while in flight. Also used for Retry. */
    fun loadProfile() {
        if (_profileState.value is ProfileState.Loading) return

        _profileState.value = ProfileState.Loading
        viewModelScope.launch {
            when (val result = repo.getMyProfileCached()) {
                is CachedResult.Fresh -> showProfile(result.data)
                is CachedResult.Cached -> showProfile(result.data, result.lastSyncedAt)
                is CachedResult.Failed -> _profileState.value =
                    ProfileState.Error(result.error.message, result.error.code)
            }
        }
    }

    // ==================== EDIT PROFILE ====================

    /**
     * Saves the profile fields set in [request] (null = leave as is; the NIC isn't editable).
     * On success the returned profile becomes the new [profileState] and the session's cached
     * name and email are refreshed, since the home screens read them from there.
     */
    fun updateProfile(request: UpdateOwnProfileRequest) = runAction {
        when (val result = repo.updateProfile(request)) {
            is ApiResult.Success -> {
                showProfile(result.data)
                ProfileActionState.Success(string(R.string.msg_profile_updated))
            }
            is ApiResult.Error -> ProfileActionState.Error(result.message, result.code)
        }
    }

    // ==================== CHANGE PASSWORD ====================

    /**
     * Checks the two things the request can't carry — the new password long enough, and typed
     * the same twice — then changes the password. Whether [oldPassword] is right is the
     * server's call. The passwords are never trimmed.
     */
    fun changePassword(oldPassword: String, newPassword: String, confirmPassword: String) {
        val problem = when {
            oldPassword.isEmpty() || newPassword.isEmpty() -> string(R.string.error_field_required)
            newPassword.length < Constants.MIN_PASSWORD_LENGTH ->
                getApplication<Application>()
                    .getString(R.string.error_password_too_short, Constants.MIN_PASSWORD_LENGTH)
            newPassword != confirmPassword -> string(R.string.error_passwords_dont_match)
            else -> null
        }
        if (problem != null) {
            // Only reachable when no action is running (the buttons are disabled otherwise).
            if (!isBusy()) _actionState.value = ProfileActionState.Error(problem)
            return
        }

        runAction {
            when (val result = repo.changePassword(ChangePasswordRequest(oldPassword, newPassword))) {
                is ApiResult.Success -> ProfileActionState.Success(string(R.string.msg_password_changed))
                is ApiResult.Error -> ProfileActionState.Error(result.message, result.code)
            }
        }
    }

    // ==================== REQUEST DEACTIVATION ====================

    /**
     * Asks Backoffice to deactivate the account. This does NOT deactivate it or log the user
     * out: the server only flags deactivationRequested, which we mirror into [profileState] so
     * the screen shows the "requested" state and the button can't be used twice.
     */
    fun requestDeactivation() {
        val currentState = _profileState.value as? ProfileState.Success
        val current = currentState?.profile
        if (current?.deactivationRequested == true) {
            if (!isBusy()) {
                _actionState.value =
                    ProfileActionState.Error(string(R.string.msg_deactivation_already_requested))
            }
            return
        }

        runAction {
            when (val result = repo.requestDeactivation()) {
                is ApiResult.Success -> {
                    // Keep the sync time: if this profile was the cached copy, it is still that.
                    current?.let {
                        showProfile(it.copy(deactivationRequested = true), currentState.lastSyncedAt)
                    }
                    ProfileActionState.Success(string(R.string.msg_deactivation_requested))
                }
                is ApiResult.Error -> ProfileActionState.Error(result.message, result.code)
            }
        }
    }

    fun resetActionState() {
        _actionState.value = ProfileActionState.Idle
    }

    // ==================== INTERNAL ====================

    /** True while an action is in flight, or its success hasn't been reset yet. */
    private fun isBusy(): Boolean {
        val current = _actionState.value
        return current is ProfileActionState.Loading || current is ProfileActionState.Success
    }

    /**
     * Runs one account action: Loading, then whatever [block] returns. Ignored while another
     * action is in flight or after a success that hasn't been reset, so a double-tap can never
     * submit twice.
     */
    private fun runAction(block: suspend () -> ProfileActionState) {
        if (isBusy()) return

        _actionState.value = ProfileActionState.Loading
        viewModelScope.launch { _actionState.value = block() }
    }

    /**
     * Makes [profile] the current one. Fresh data ([lastSyncedAt] null) is also mirrored into the
     * session's cached name/email; a cached copy is not — it can only be as old as the last sync,
     * so it must never overwrite what the session already holds.
     */
    private fun showProfile(profile: ProsumerResponse, lastSyncedAt: Long? = null) {
        if (lastSyncedAt == null) {
            session.updateProfileInfo(profile.name, profile.email, profile.nic)
        }
        _profileState.value = ProfileState.Success(profile, lastSyncedAt)
    }

    private fun string(resId: Int): String = getApplication<Application>().getString(resId)
}
