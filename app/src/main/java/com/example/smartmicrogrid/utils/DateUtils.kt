package com.example.smartmicrogrid.utils

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale

/**
 * File: DateUtils.kt
 * Purpose: Parse the backend's ISO-8601 UTC timestamps and format them for display.
 * Author: Mobile Team
 * Date: 2026
 *
 * Why java.time and not SimpleDateFormat(Constants.DATE_FORMAT):
 * - The C# backend can emit "…:00Z", "…:00.123Z", or 7-digit ticks ("…:00.1234567Z"),
 *   and sometimes no zone at all. A single fixed pattern rejects most of these.
 * - java.time is available natively from minSdk 26 (no desugaring needed).
 */
object DateUtils {

    // ==================== PARSING ====================

    /**
     * Parses an ISO-8601 timestamp into an [Instant].
     *
     * Accepts a trailing Z or an explicit offset, with any fractional-second precision.
     * A timestamp with no zone at all is assumed to be UTC (backend stores UTC).
     *
     * @return the parsed instant, or null if [iso] is null, blank, or unparseable
     */
    fun parseIso(iso: String?): Instant? {
        if (iso.isNullOrBlank()) return null
        return try {
            OffsetDateTime.parse(iso).toInstant()
        } catch (e: DateTimeParseException) {
            try {
                LocalDateTime.parse(iso).toInstant(ZoneOffset.UTC)
            } catch (e2: DateTimeParseException) {
                null
            }
        }
    }

    // ==================== FORMATTING ====================

    /**
     * Formats an ISO-8601 timestamp for the UI using [Constants.DISPLAY_DATE_FORMAT]
     * in the device's time zone.
     *
     * @return the formatted string; the original [iso] if it can't be parsed;
     *         empty string if [iso] is null
     */
    fun formatForDisplay(iso: String?): String {
        val instant = parseIso(iso) ?: return iso.orEmpty()
        return DateTimeFormatter
            .ofPattern(Constants.DISPLAY_DATE_FORMAT, Locale.getDefault())
            .withZone(ZoneId.systemDefault())
            .format(instant)
    }
}
