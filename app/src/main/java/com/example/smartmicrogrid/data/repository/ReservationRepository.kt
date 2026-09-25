package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.CancelReservationRequest
import com.example.smartmicrogrid.data.remote.dto.CreateOwnReservationRequest
import com.example.smartmicrogrid.data.remote.dto.QrTokenResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.remote.dto.UpdateReservationRequest

/**
 * File: ReservationRepository.kt
 * Purpose: Prosumer reservation operations — create, list, detail, update (move to a new slot),
 *          cancel, and fetch the QR token. Wraps every Retrofit call in safeApiCall so callers
 *          only ever deal with ApiResult. The server enforces every booking rule; its message
 *          comes back in ApiResult.Error.
 * Author: Mobile Team
 * Date: 2026
 */
class ReservationRepository(context: Context) {

    private val api: ApiService = RetrofitClient.getApiService(context)

    // ==================== CREATE ====================

    /**
     * POST /api/reservations/my — books [slotId] at [stationId] for the signed-in prosumer.
     * The server takes the NIC from the JWT and enforces every rule (active prosumer/station,
     * slot not booked, in the future, within 7 days); its message comes back in ApiResult.Error.
     */
    suspend fun createReservation(
        stationId: String,
        slotId: String
    ): ApiResult<ReservationActionResponse> =
        safeApiCall { api.createMyReservation(CreateOwnReservationRequest(stationId, slotId)) }

    // ==================== READ ====================

    /**
     * GET /api/reservations/my — own reservations. [status] is one of Pending / Approved /
     * Completed / Cancelled (case-sensitive); null or blank means all.
     */
    suspend fun getMyReservations(status: String? = null): ApiResult<List<ReservationResponse>> =
        safeApiCall { api.getMyReservations(status?.takeIf { it.isNotBlank() }) }

    /** GET /api/reservations/my/{id} — one own reservation. */
    suspend fun getReservationDetail(id: String): ApiResult<ReservationResponse> =
        safeApiCall { api.getMyReservationById(id) }

    /** GET /api/reservations/my/{id}/qr — the QR token; only available once Approved. */
    suspend fun getReservationQr(id: String): ApiResult<QrTokenResponse> =
        safeApiCall { api.getMyReservationQr(id) }

    // ==================== UPDATE ====================

    /**
     * PUT /api/reservations/my/{id} — moves a reservation to [newSlotId]. The server decides
     * whether it is allowed (status, 12-hour notice, slot availability).
     */
    suspend fun updateReservation(
        id: String,
        newSlotId: String
    ): ApiResult<ReservationActionResponse> =
        safeApiCall { api.updateMyReservation(id, UpdateReservationRequest(newSlotId)) }

    // ==================== CANCEL ====================

    /**
     * PUT /api/reservations/my/{id}/cancel — cancels a reservation. [reason] is optional; a
     * blank reason is sent as absent. The server enforces the 12-hour notice rule.
     */
    suspend fun cancelReservation(
        id: String,
        reason: String?
    ): ApiResult<ReservationActionResponse> =
        safeApiCall {
            api.cancelMyReservation(id, CancelReservationRequest(reason?.trim()?.takeIf { it.isNotEmpty() }))
        }
}
