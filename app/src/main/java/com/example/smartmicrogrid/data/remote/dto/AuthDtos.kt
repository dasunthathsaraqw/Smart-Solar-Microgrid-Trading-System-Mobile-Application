package com.example.smartmicrogrid.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * File: AuthDtos.kt
 * Purpose: Data Transfer Objects for authentication endpoints.
 *          Field names are mapped to the exact JSON keys returned by the C# backend
 *          via @SerializedName so Kotlin naming conventions can be used locally.
 *
 * Backend source of truth:
 * - AuthController.cs (Login, Me)
 * - AuthService.cs    (LoginAsync, GetByIdAsync)
 * - JwtService.cs     (claims embedded in the token)
 */

/**
 * Request body for POST /api/auth/login.
 *
 * The backend authenticates by EMAIL (not NIC, not username).
 * This applies to both Prosumers and GridOperators/Backoffice users.
 */
data class LoginRequest(
    @SerializedName("email")    val email: String,
    @SerializedName("password") val password: String
)

/**
 * Response body for POST /api/auth/login.
 *
 * Example JSON:
 * {
 *   "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
 *   "name": "Kamal Perera",
 *   "email": "kamal@example.com",
 *   "role": "Prosumer",
 *   "stationId": null,
 *   "expiresAt": "2026-09-25T14:00:00Z"
 * }
 *
 * Notes:
 * - stationId is only populated for GridOperator accounts.
 * - NIC is NOT included here; it lives inside the JWT as the "nic" claim.
 *   To display NIC in the app, call GET /api/prosumers/me after login.
 */
data class LoginResponse(
    @SerializedName("token")     val token: String,
    @SerializedName("name")      val name: String,
    @SerializedName("email")     val email: String,
    @SerializedName("role")      val role: String,
    @SerializedName("stationId") val stationId: String?,
    @SerializedName("expiresAt") val expiresAt: String
)

/**
 * Response body for GET /api/auth/me.
 *
 * Example JSON:
 * {
 *   "id": "6512f3a4c8d9e1b2f3a4c8d9",
 *   "name": "Kamal Perera",
 *   "email": "kamal@example.com",
 *   "role": "GridOperator",
 *   "stationId": "6512f3a4c8d9e1b2f3a4c8da"
 * }
 *
 * Notes:
 * - Backend reloads the account from MongoDB on every call, so the data is always live.
 * - stationId is null for non-operators.
 */
data class IdentityResponse(
    @SerializedName("id")        val id: String,
    @SerializedName("name")      val name: String,
    @SerializedName("email")     val email: String,
    @SerializedName("role")      val role: String,
    @SerializedName("stationId") val stationId: String?
)