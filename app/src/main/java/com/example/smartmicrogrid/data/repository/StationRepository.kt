package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.local.AppDatabase
import com.example.smartmicrogrid.data.local.dao.StationDao
import com.example.smartmicrogrid.data.local.toEntity
import com.example.smartmicrogrid.data.local.toResponse
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.NearbyStationResponse
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.data.remote.dto.UpdateSlotRequest

/**
 * File: StationRepository.kt
 * Purpose: Fetches charging/transfer stations (all, or those near a location) and works with
 *          their slots: reading the bookable ones (prosumer booking, operator slot list) and
 *          updating one (operator). Wraps every Retrofit call in safeApiCall so callers only
 *          ever deal with ApiResult.
 * Author: Mobile Team
 * Date: 2026
 */
class StationRepository(context: Context) {

    private val api: ApiService = RetrofitClient.getApiService(context)
    private val stationDao: StationDao = AppDatabase.getInstance(context).stationDao()

    // ==================== STATIONS (CACHE-AWARE) ====================

    /**
     * GET /api/stations — all active stations (no status filter; the server decides).
     *
     * Network-first with the Room cache as fallback (returns a CachedResult). The list is always
     * fetched whole, so a successful fetch replaces the whole cache. Only THIS call is cached:
     * nearby stations (location-dependent, stale would mislead) and slots (availability) are not.
     */
    suspend fun getStations(): CachedResult<List<StationResponse>> =
        networkFirst(
            fetch = { safeApiCall { api.getStations() } },
            save = { stations ->
                val syncedAt = System.currentTimeMillis()
                stationDao.replaceAll(stations.map { it.toEntity(syncedAt) })
            },
            readCache = {
                val rows = stationDao.getAll()
                if (rows.isEmpty()) null else (rows.map { it.toResponse() } to rows.maxOf { it.lastSyncedAt })
            }
        )

    // ==================== NEARBY ====================

    /**
     * GET /api/stations/nearby — active stations within [radiusKm] of the given point, closest
     * first, at most [limit]. Each result adds distanceKm and availableSlotCount.
     */
    suspend fun getNearbyStations(
        latitude: Double,
        longitude: Double,
        radiusKm: Double = 10.0,
        limit: Int = 20
    ): ApiResult<List<NearbyStationResponse>> =
        safeApiCall { api.getNearbyStations(latitude, longitude, radiusKm, limit) }

    // ==================== SLOTS ====================

    /** GET /api/slots/station/{stationId}/available — unbooked, future slots within 7 days. */
    suspend fun getAvailableSlots(stationId: String): ApiResult<List<SlotResponse>> =
        safeApiCall { api.getAvailableSlots(stationId) }

    /**
     * PUT /api/slots/{slotId} — changes a slot's window and/or capacity (GridOperator only, for
     * their own station's slots). Every field is optional: a null (or blank time) is left out of
     * the request body, so only what the operator actually changed is sent. The server enforces
     * the rules (e.g. a booked slot can't be modified) and its message comes back in
     * ApiResult.Error.
     */
    suspend fun updateSlot(
        slotId: String,
        startTime: String?,
        endTime: String?,
        capacityKw: Double?
    ): ApiResult<SlotResponse> =
        safeApiCall {
            api.updateSlot(
                slotId,
                UpdateSlotRequest(
                    startTime = startTime?.takeIf { it.isNotBlank() },
                    endTime = endTime?.takeIf { it.isNotBlank() },
                    capacityKw = capacityKw
                )
            )
        }
}
