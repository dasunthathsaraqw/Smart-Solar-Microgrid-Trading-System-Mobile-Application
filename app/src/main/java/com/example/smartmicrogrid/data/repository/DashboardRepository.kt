package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.local.AppDatabase
import com.example.smartmicrogrid.data.local.dao.DashboardDao
import com.example.smartmicrogrid.data.local.toEntity
import com.example.smartmicrogrid.data.local.toResponse
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.OperatorDashboardResponse
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse

/**
 * File: DashboardRepository.kt
 * Purpose: Fetches dashboard summaries for the signed-in user — the prosumer's own counts, or
 *          the grid operator's station-wide activity. Wraps every Retrofit call in
 *          safeApiCall so callers only ever deal with ApiResult.
 * Author: Mobile Team
 * Date: 2026
 */
class DashboardRepository(context: Context) {

    private val api: ApiService = RetrofitClient.getApiService(context)
    private val dashboardDao: DashboardDao = AppDatabase.getInstance(context).dashboardDao()

    // ==================== PROSUMER (CACHE-AWARE) ====================

    /**
     * GET /api/reports/my-dashboard — status counts + next approved reservation (Prosumer JWT).
     *
     * Network-first with the Room cache as fallback (returns a CachedResult): the last snapshot
     * is saved on every success and shown, marked as cached, when the server can't be reached.
     */
    suspend fun getMyDashboard(): CachedResult<ProsumerDashboardResponse> =
        networkFirst(
            fetch = { safeApiCall { api.getMyDashboard() } },
            save = { dashboardDao.upsert(it.toEntity(System.currentTimeMillis())) },
            readCache = { dashboardDao.get()?.let { it.toResponse() to it.lastSyncedAt } }
        )

    // ==================== OPERATOR (NEVER CACHED) ====================

    /**
     * GET /api/reports/operator-dashboard — today's pending/approved/completed counts, the
     * approved-future count, and up to 10 upcoming approved reservations (GridOperator JWT).
     * Scoped to the operator's own station by the server, so no stationId is sent.
     * Live data only, on purpose: an operator acting on a stale snapshot could complete the same
     * charge twice, so this is never cached and keeps returning a plain ApiResult.
     */
    suspend fun getOperatorDashboard(): ApiResult<OperatorDashboardResponse> =
        safeApiCall { api.getOperatorDashboard() }
}
