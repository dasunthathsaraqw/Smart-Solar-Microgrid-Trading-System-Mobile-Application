package com.example.smartmicrogrid.utils

import android.content.Context
import android.content.SharedPreferences
import com.example.smartmicrogrid.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    private val appContext: Context = context.applicationContext

    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(Constants.PREF_NAME, Context.MODE_PRIVATE)

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

    /**
     * Refreshes the cached display info after the profile was loaded or edited, without touching
     * the JWT, role, station or expiry. The dashboard and home screens read name/email from here.
     * [nic] is only written when non-null (it can't change, but a failed profile fetch at login
     * can leave it unset).
     */
    fun updateProfileInfo(name: String, email: String, nic: String? = null) {
        prefs.edit().apply {
            putString(Constants.KEY_NAME, name)
            putString(Constants.KEY_EMAIL, email)
            if (nic != null) putString(Constants.KEY_NIC, nic)
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
     * Clears the session (on logout, expired token, or a role that can't use the app).
     *
     * Also empties the offline cache: it holds the departing user's bookings and profile, and must
     * never be shown to whoever signs in next on this device. Every path that ends a session goes
     * through here, so none can forget it. The wipe runs in the background (Room forbids the main
     * thread) and can never make logout fail; a fresh login clears the cache again as a backstop.
     */
    fun clear() {
        prefs.edit().clear().apply()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(appContext).clearCache()
            } catch (e: Exception) {
                // A failed cache wipe must not surface as a logout error; the next login wipes it.
            }
        }
    }
}