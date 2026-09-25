package com.example.smartmicrogrid.utils

import android.content.Context
import android.content.SharedPreferences
import java.time.Instant

/**
 * Manages the logged-in user's session.
 *
 * Stores:
 * - JWT token (sent as Authorization: Bearer <token> on every API call)
 * - User type / role (Prosumer / GridOperator / Backoffice)
 * - NIC (for prosumers)
 * - Email (login identifier — backend uses email, not username)
 * - Name (display name from backend)
 * - Station ID (for operators, ties them to a station)
 * - Token expiry (for auto-logout)
 *
 * Uses SharedPreferences for simplicity.
 * For production, use EncryptedSharedPreferences (we have the dependency).
 */
class SessionManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

    /**
     * Save session after successful login.
     *
     * @param jwt Bearer token from LoginResponse.token
     * @param userType Role from LoginResponse.role (e.g. "Prosumer", "GridOperator")
     * @param email Login identifier — always present
     * @param name Display name — always present
     * @param nic Only for prosumers (fetch from /api/prosumers/me after login)
     * @param stationId Only for GridOperators (from LoginResponse.stationId)
     * @param expiresAt ISO-8601 string from LoginResponse.expiresAt
     */
    fun saveSession(
        jwt: String,
        userType: String,
        email: String,
        name: String,
        nic: String? = null,
        stationId: String? = null,
        expiresAt: String? = null
    ) {
        prefs.edit().apply {
            putString(Constants.KEY_JWT, jwt)
            putString(Constants.KEY_USER_TYPE, userType)
            putString(Constants.KEY_EMAIL, email)
            putString(Constants.KEY_NAME, name)
            putString(Constants.KEY_NIC, nic)
            putString(Constants.KEY_STATION_ID, stationId)
            putString(Constants.KEY_EXPIRES_AT, expiresAt)
            apply()
        }
    }

    fun getJwt(): String? = prefs.getString(Constants.KEY_JWT, null)
    fun getUserType(): String? = prefs.getString(Constants.KEY_USER_TYPE, null)
    fun getEmail(): String? = prefs.getString(Constants.KEY_EMAIL, null)
    fun getName(): String? = prefs.getString(Constants.KEY_NAME, null)
    fun getNic(): String? = prefs.getString(Constants.KEY_NIC, null)
    fun getStationId(): String? = prefs.getString(Constants.KEY_STATION_ID, null)
    fun getExpiresAt(): String? = prefs.getString(Constants.KEY_EXPIRES_AT, null)

    /**
     * Returns true if the stored expiry (LoginResponse.expiresAt) is in the future.
     * Fails closed: a missing or unparseable expiry counts as NOT valid.
     */
    fun isSessionValid(): Boolean {
        val expiry = DateUtils.parseIso(getExpiresAt()) ?: return false
        return expiry.isAfter(Instant.now())
    }

    /**
     * Returns true if a JWT is stored AND the session has not expired.
     * Does not clear an expired session — callers decide (e.g. LoginActivity shows login).
     */
    fun isLoggedIn(): Boolean = !getJwt().isNullOrEmpty() && isSessionValid()

    /**
     * Clears the session (on logout or expired token).
     */
    fun clear() {
        prefs.edit().clear().apply()
    }
}