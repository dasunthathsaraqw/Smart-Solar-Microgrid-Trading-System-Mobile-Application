package com.example.smartmicrogrid.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * File: CommonDtos.kt
 * Purpose: Small helper DTOs shared by several endpoints that don't belong to a
 *          single feature file (QR token wrapper, slot update body).
 * Author: Mobile Team
 * Date: 2026
 */

// ==================== QR ====================

/**
 * Small helper DTO for the two endpoints that return { qrToken: "..." }:
 * - GET /api/reservations/my/{id}/qr (prosumer)
 * - GET /api/reservations/{id}/qr    (management, not used on mobile)
 */
data class QrTokenResponse(
    @SerializedName("qrToken") val qrToken: String
)

// ==================== SLOTS ====================

/**
 * Request body for PUT /api/slots/{id}.
 * All fields optional — send only what you're changing.
 */
data class UpdateSlotRequest(
    @SerializedName("startTime")  val startTime: String? = null,
    @SerializedName("endTime")    val endTime: String? = null,
    @SerializedName("capacityKw") val capacityKw: Double? = null
)
