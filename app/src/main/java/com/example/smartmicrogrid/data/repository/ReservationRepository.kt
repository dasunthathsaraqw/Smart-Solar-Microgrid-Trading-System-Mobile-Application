package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.CreateOwnReservationRequest
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse

/**
 * File: ReservationRepository.kt
 * Purpose: Prosumer reservation operations. Wraps every Retrofit call in safeApiCall so
 *          callers only ever deal with ApiResult. Currently create only; update, cancel,
 *          list, detail and QR belong here too and are added in the next round.
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
}
