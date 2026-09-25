package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse

/**
 * File: DashboardRepository.kt
 * Purpose: Fetches dashboard data for the signed-in user. Wraps every Retrofit call in
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
}
