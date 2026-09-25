package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.data.remote.dto.StationResponse

/**
 * File: StationRepository.kt
 * Purpose: Fetches charging/transfer stations and their bookable slots. Wraps every Retrofit
 *          call in safeApiCall so callers only ever deal with ApiResult.
 * Author: Mobile Team
 * Date: 2026
 */
class StationRepository(context: Context) {

    private val api: ApiService = RetrofitClient.getApiService(context)

    // ==================== STATIONS ====================

    /** GET /api/stations — all active stations (no status filter; the server decides). */
    suspend fun getStations(): ApiResult<List<StationResponse>> =
        safeApiCall { api.getStations() }

    // ==================== SLOTS ====================

    /** GET /api/slots/station/{stationId}/available — unbooked, future slots within 7 days. */
    suspend fun getAvailableSlots(stationId: String): ApiResult<List<SlotResponse>> =
        safeApiCall { api.getAvailableSlots(stationId) }
}
