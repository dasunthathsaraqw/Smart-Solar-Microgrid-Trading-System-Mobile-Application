package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.ChangePasswordRequest
import com.example.smartmicrogrid.data.remote.dto.IdentityResponse
import com.example.smartmicrogrid.data.remote.dto.LoginRequest
import com.example.smartmicrogrid.data.remote.dto.LoginResponse
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.RegisterProsumerRequest
import com.example.smartmicrogrid.data.remote.dto.UpdateOwnProfileRequest

/**
 * File: AuthRepository.kt
 * Purpose: Single entry point for the prosumer's own-account API calls: login and identity,
 *          self-registration, and the self-service account surface (view/edit profile, change
 *          password, request deactivation). Wraps every Retrofit call in safeApiCall (or
 *          safeApiCallUnit for the 204 No Content ones) so callers only ever deal with ApiResult.
 * Author: Mobile Team
 * Date: 2026
 */
class AuthRepository(context: Context) {

    private val api: ApiService = RetrofitClient.getApiService(context)

    // ==================== AUTH ====================

    /** POST /api/auth/login — authenticate by email + password. */
    suspend fun login(email: String, password: String): ApiResult<LoginResponse> =
        safeApiCall { api.login(LoginRequest(email, password)) }

    /** GET /api/auth/me — latest identity for the current JWT. */
    suspend fun getMe(): ApiResult<IdentityResponse> =
        safeApiCall { api.getMe() }

    // ==================== PROSUMER ====================

    /** POST /api/prosumers/register — anonymous; account is created as "pending". */
    suspend fun registerProsumer(request: RegisterProsumerRequest): ApiResult<ProsumerResponse> =
        safeApiCall { api.registerProsumer(request) }

    /**
     * GET /api/prosumers/me — own profile. Used right after a prosumer logs in, because
     * the login response does not include the NIC (it is only a JWT claim server-side), and
     * by the Profile screen.
     */
    suspend fun getMyProfile(): ApiResult<ProsumerResponse> =
        safeApiCall { api.getMyProfile() }

    // ==================== PROFILE (SELF-SERVICE) ====================

    /**
     * PUT /api/prosumers/me — updates the editable profile fields and returns the saved profile.
     * The server applies only the non-null fields of [request]; the NIC can't be changed and
     * isn't part of the request. The server's message (e.g. email already in use) comes back in
     * ApiResult.Error.
     */
    suspend fun updateProfile(request: UpdateOwnProfileRequest): ApiResult<ProsumerResponse> =
        safeApiCall { api.updateMyProfile(request) }

    /**
     * PUT /api/prosumers/me/password — changes the password. 204 No Content on success; a wrong
     * current password is rejected with the server's own message.
     */
    suspend fun changePassword(request: ChangePasswordRequest): ApiResult<Unit> =
        safeApiCallUnit { api.changePassword(request) }

    /**
     * PUT /api/prosumers/me/request-deactivation — asks Backoffice to deactivate the account.
     * 204 No Content, no body. The account is NOT deactivated by this: it is only flagged
     * (deactivationRequested = true) for Backoffice to act on.
     */
    suspend fun requestDeactivation(): ApiResult<Unit> =
        safeApiCallUnit { api.requestDeactivation() }
}
