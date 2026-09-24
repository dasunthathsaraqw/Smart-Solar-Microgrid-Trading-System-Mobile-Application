package com.example.smartmicrogrid.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * File: ApiError.kt
 * Purpose: Unified error shape that the backend returns across different controllers.
 *
 * The C# backend is INCONSISTENT — three different shapes exist:
 *
 * 1. { "message": "Invalid email or password." }       ← AuthController, ProsumersController, etc.
 * 2. { "error": "Slot is already booked" }             ← ReservationsController, SlotsController, etc.
 * 3. { "title": "One or more validation errors...",    ← ASP.NET Core ProblemDetails (400 model validation)
 *      "status": 400,
 *      "detail": "..." }
 *
 * All three fields are nullable and optional. A helper (extractMessage) picks
 * whichever one is present, so the UI can always show something meaningful.
 *
 * Backend source of truth:
 * - All controllers (look for `new { message = ... }` or `new { error = ... }`)
 * - ProblemDetails (built into ASP.NET Core for [ApiController] model validation)
 */
data class ApiError(
    // Used by: AuthController 401/403, ProsumersController, ProsumerSelfServiceController,
    //          StationsController (sometimes), UsersController
    @SerializedName("message") val message: String? = null,

    // Used by: ReservationsController, SlotsController, ReportsController,
    //          StationsController (sometimes), UsersController (sometimes)
    @SerializedName("error") val error: String? = null,

    // ProblemDetails (RFC 7807) fields — returned by ASP.NET Core automatic model validation (400)
    @SerializedName("title")  val title: String? = null,
    @SerializedName("status") val status: Int? = null,
    @SerializedName("detail") val detail: String? = null,

    // Optional — for field-level validation errors (ProblemDetails "errors" object)
    // Example: { "email": ["The Email field is required."] }
    @SerializedName("errors") val errors: Map<String, List<String>>? = null
) {
    /**
     * Returns a human-readable error message, picking the first non-blank of:
     * 1. error     (most specific for reservations/slots)
     * 2. message   (used by auth and prosumer endpoints)
     * 3. detail    (ProblemDetails explanation)
     * 4. title     (ProblemDetails summary)
     * 5. field validation errors joined
     * 6. generic fallback
     */
    fun extractMessage(): String {
        error?.takeIf { it.isNotBlank() }?.let { return it }
        message?.takeIf { it.isNotBlank() }?.let { return it }
        detail?.takeIf { it.isNotBlank() }?.let { return it }
        title?.takeIf { it.isNotBlank() }?.let { return it }

        errors?.takeIf { it.isNotEmpty() }?.let { map ->
            return map.values.flatten().joinToString("\n").ifBlank { "Validation failed." }
        }

        return "Something went wrong. Please try again."
    }
}