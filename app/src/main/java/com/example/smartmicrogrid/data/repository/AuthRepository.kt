package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.local.AppDatabase
import com.example.smartmicrogrid.data.local.dao.ProfileDao
import com.example.smartmicrogrid.data.local.toEntity
import com.example.smartmicrogrid.data.local.toResponse
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
    private val profileDao: ProfileDao = AppDatabase.getInstance(context).profileDao()

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
     * GET /api/prosumers/me — own profile, LIVE ONLY (a plain ApiResult, never the cache). Used
     * right after a prosumer logs in, because the login response does not include the NIC (it is
     * only a JWT claim server-side); that call must never be answered with someone's old data.
     * The Profile screen uses [getMyProfileCached] instead.
     */
    suspend fun getMyProfile(): ApiResult<ProsumerResponse> =
        safeApiCall { api.getMyProfile() }

    /**
     * GET /api/prosumers/me for the Profile screen: network-first with the Room cache as
     * fallback (returns a CachedResult). A successful fetch replaces the cached profile, so the
     * table only ever holds the signed-in user's.
     */
    suspend fun getMyProfileCached(): CachedResult<ProsumerResponse> =
        networkFirst(
            fetch = { safeApiCall { api.getMyProfile() } },
            save = { profileDao.replace(it.toEntity(System.currentTimeMillis())) },
            readCache = { profileDao.get()?.let { it.toResponse() to it.lastSyncedAt } }
        )

    // ==================== PROFILE (SELF-SERVICE) ====================

    /**
     * PUT /api/prosumers/me — updates the editable profile fields and returns the saved profile.
     * The server applies only the non-null fields of [request]; the NIC can't be changed and
     * isn't part of the request. The server's message (e.g. email already in use) comes back in
     * ApiResult.Error.
     */
    suspend fun updateProfile(request: UpdateOwnProfileRequest): ApiResult<ProsumerResponse> =
        safeApiCall { api.updateMyProfile(request) }.also { result ->
            // Write-through, so the offline copy shows the edit (the edit itself is never queued).
            if (result is ApiResult.Success) {
                quietly { profileDao.replace(result.data.toEntity(System.currentTimeMillis())) }
            }
        }

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
        safeApiCallUnit { api.requestDeactivation() }.also { result ->
            // Write-through: the cached profile must show the pending request too. Its sync time
            // is kept — nothing was re-fetched.
            if (result is ApiResult.Success) {
                quietly {
                    profileDao.get()?.let { profileDao.replace(it.copy(deactivationRequested = true)) }
                }
            }
        }
}
