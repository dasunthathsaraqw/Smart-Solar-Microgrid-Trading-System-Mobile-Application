package com.example.smartmicrogrid.data.repository

import android.content.Context
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

    // ==================== PROSUMER ====================

    /** GET /api/reports/my-dashboard — status counts + next approved reservation (Prosumer JWT). */
    suspend fun getMyDashboard(): ApiResult<ProsumerDashboardResponse> =
        safeApiCall { api.getMyDashboard() }

    // ==================== OPERATOR ====================

    /**
     * GET /api/reports/operator-dashboard — today's pending/approved/completed counts, the
     * approved-future count, and up to 10 upcoming approved reservations (GridOperator JWT).
     * Scoped to the operator's own station by the server, so no stationId is sent.
     */
    suspend fun getOperatorDashboard(): ApiResult<OperatorDashboardResponse> =
        safeApiCall { api.getOperatorDashboard() }
}
