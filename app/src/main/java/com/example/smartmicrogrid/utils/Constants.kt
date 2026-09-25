package com.example.smartmicrogrid.utils

/**
 * Global constants used across the app.
 *
 * IMPORTANT — BASE_URL:
 * - Android Emulator:   10.0.2.2 = your PC's localhost
 * - Physical Device:    use your PC's LAN IP (e.g. 192.168.1.5)
 * - Both devices must be on the same Wi-Fi network.
 *
 * Endpoint paths are NOT kept here — ApiService is the single source of truth
 * for every route (they must match the C# Web API routes exactly).
 */
object Constants {

    // ==================== API ====================
    // Android Emulator: 10.0.2.2 = your PC's localhost (port 5151)
    // Physical Device: replace with your PC's LAN IP, e.g. "http://192.168.1.5:5151/"
    const val BASE_URL = "http://10.0.2.2:5151/"

    // ==================== ROLES ====================
    // These strings must EXACTLY match what the backend returns
    const val ROLE_BACKOFFICE = "Backoffice"
    const val ROLE_OPERATOR = "GridOperator"   // ← matches backend role string
    const val ROLE_PROSUMER = "Prosumer"

    // ==================== RESERVATION STATUS ====================
    // Case-sensitive: must EXACTLY match ReservationResponse.status from the backend
    const val STATUS_PENDING = "Pending"
    const val STATUS_APPROVED = "Approved"
    const val STATUS_COMPLETED = "Completed"
    const val STATUS_CANCELLED = "Cancelled"

    /// ==================== SESSION PREFS ====================
    const val PREF_NAME = "solar_microgrid_prefs"
    const val KEY_JWT = "jwt_token"
    const val KEY_USER_TYPE = "user_type"
    const val KEY_EMAIL = "email"
    const val KEY_NAME = "name"
    const val KEY_NIC = "nic"
    const val KEY_STATION_ID = "station_id"
    const val KEY_EXPIRES_AT = "expires_at"
    // ==================== VALIDATION ====================
    // Client-side sanity check only; the backend enforces the real password rules.
    const val MIN_PASSWORD_LENGTH = 6

    // ==================== MISC ====================
    const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"
    const val DISPLAY_DATE_FORMAT = "dd MMM yyyy, HH:mm"
    const val DISPLAY_TIME_FORMAT = "HH:mm"
}
