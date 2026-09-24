package com.example.smartmicrogrid.utils

/**
 * Global constants used across the app.
 *
 * IMPORTANT — BASE_URL:
 * - Android Emulator:   10.0.2.2 = your PC's localhost
 * - Physical Device:    use your PC's LAN IP (e.g. 192.168.1.5)
 * - Both devices must be on the same Wi-Fi network.
 *
 * All endpoint paths are relative to BASE_URL.
 * They must match the C# Web API routes exactly.
 */
object Constants {

    // ==================== API ====================
    // Android Emulator: 10.0.2.2 = your PC's localhost (port 5151)
    // Physical Device: replace with your PC's LAN IP, e.g. "http://192.168.1.5:5151/"
    const val BASE_URL = "http://10.0.2.2:5151/"

    // ==================== AUTH ====================
    const val ENDPOINT_LOGIN = "api/auth/login"
    const val ENDPOINT_ME = "api/auth/me"

    // ==================== PROSUMER SELF-SERVICE ====================
    const val ENDPOINT_REGISTER = "api/prosumers/register"
    const val ENDPOINT_MY_PROFILE = "api/prosumers/me"
    const val ENDPOINT_CHANGE_PASSWORD = "api/prosumers/me/password"
    const val ENDPOINT_REQUEST_DEACTIVATION = "api/prosumers/me/request-deactivation"

    // ==================== DASHBOARDS ====================
    const val ENDPOINT_MY_DASHBOARD = "api/reports/my-dashboard"
    const val ENDPOINT_OPERATOR_DASHBOARD = "api/reports/operator-dashboard"
    const val ENDPOINT_PENDING_APPROVALS = "api/reports/pending-approvals"

    // ==================== RESERVATIONS (PROSUMER) ====================
    const val ENDPOINT_MY_RESERVATIONS = "api/reservations/my"
    const val ENDPOINT_MY_RESERVATIONS_SEARCH = "api/reservations/my/search"

    // ==================== RESERVATIONS (OPERATOR) ====================
    const val ENDPOINT_OPERATOR_HISTORY = "api/reservations/operator/history"
    const val ENDPOINT_VERIFY_QR = "api/reservations/verify-qr"
    const val ENDPOINT_SCAN_COMPLETE = "api/reservations/scan-complete"

    // ==================== STATIONS ====================
    const val ENDPOINT_STATIONS = "api/stations"
    const val ENDPOINT_NEARBY_STATIONS = "api/stations/nearby"

    // ==================== SLOTS ====================
    const val ENDPOINT_SLOTS = "api/slots"

    // ==================== ROLES ====================
    // These strings must EXACTLY match what the backend returns
    const val ROLE_BACKOFFICE = "Backoffice"
    const val ROLE_OPERATOR = "GridOperator"   // ← matches backend role string
    const val ROLE_PROSUMER = "Prosumer"

    /// ==================== SESSION PREFS ====================
    const val PREF_NAME = "solar_microgrid_prefs"
    const val KEY_JWT = "jwt_token"
    const val KEY_USER_TYPE = "user_type"
    const val KEY_EMAIL = "email"
    const val KEY_NAME = "name"
    const val KEY_NIC = "nic"
    const val KEY_STATION_ID = "station_id"
    const val KEY_EXPIRES_AT = "expires_at"
    // ==================== MISC ====================
    const val DATE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss"
    const val DISPLAY_DATE_FORMAT = "dd MMM yyyy, HH:mm"
}