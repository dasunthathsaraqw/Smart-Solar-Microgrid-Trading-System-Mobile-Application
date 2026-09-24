package com.example.smartmicrogrid.data.remote

import com.example.smartmicrogrid.utils.SessionManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * File: AuthInterceptor.kt
 * Purpose: OkHttp interceptor that automatically attaches the JWT to every
 *          outgoing HTTP request that requires authentication.
 *
 * Behavior:
 * - Reads the stored JWT from SessionManager
 * - Adds "Authorization: Bearer <JWT>" header if a token exists
 * - Skips adding the header if no token (anonymous endpoints still work)
 *
 * Anonymous endpoints (no JWT expected):
 * - POST /api/auth/login
 * - POST /api/prosumers/register
 * - GET  /api/health
 *
 * These endpoints work fine even when a stale token is present, because the
 * backend either ignores the header or overrides it for anonymous actions.
 *
 * The interceptor runs on EVERY request, so any endpoint that DOES need a JWT
 * (all authenticated endpoints) automatically gets one — no manual header
 * building anywhere in the codebase.
 */
class AuthInterceptor(
    private val sessionManager: SessionManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // If no token, send the request as-is (anonymous endpoints still work)
        val jwt = sessionManager.getJwt()
        if (jwt.isNullOrBlank()) {
            return chain.proceed(originalRequest)
        }

        // Attach the Authorization header
        val authenticatedRequest = originalRequest.newBuilder()
            .header("Authorization", "Bearer $jwt")
            .header("Accept", "application/json")
            .build()

        return chain.proceed(authenticatedRequest)
    }
}