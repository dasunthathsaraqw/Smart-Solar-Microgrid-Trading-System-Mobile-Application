package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.IdentityResponse
import com.example.smartmicrogrid.data.remote.dto.LoginRequest
import com.example.smartmicrogrid.data.remote.dto.LoginResponse
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.RegisterProsumerRequest

/**
 * File: AuthRepository.kt
 * Purpose: Single entry point for authentication-related API calls (login, identity,
 *          prosumer self-registration). Wraps every Retrofit call in safeApiCall so
 *          callers only ever deal with ApiResult.
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
     * the login response does not include the NIC (it is only a JWT claim server-side).
     */
    suspend fun getMyProfile(): ApiResult<ProsumerResponse> =
        safeApiCall { api.getMyProfile() }
}
