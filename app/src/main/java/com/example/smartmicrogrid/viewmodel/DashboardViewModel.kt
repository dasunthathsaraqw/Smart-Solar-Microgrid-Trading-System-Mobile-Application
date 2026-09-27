package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse
import com.example.smartmicrogrid.data.repository.CachedResult
import com.example.smartmicrogrid.data.repository.DashboardRepository
import com.example.smartmicrogrid.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * File: DashboardViewModel.kt
 * Purpose: Drives the Prosumer dashboard. Loads GET /api/reports/my-dashboard through
 *          DashboardRepository and exposes the outcome as LiveData<DashboardState>, plus the
 *          signed-in user's name/email/NIC for the header.
 * Author: Mobile Team
 * Date: 2026
 *
 * Unlike the auth screens there is no Idle state or reset: the dashboard is always either
 * loading, showing data, or showing an error, and nothing is a one-shot event. The state
 * has no value until loadDashboard() is first called.
 */

// ==================== STATE ====================

sealed class DashboardState {
    /** A request is in flight — show spinner, hide content/error. */
    object Loading : DashboardState()

    /**
     * Dashboard loaded. [lastSyncedAt] is null for fresh data; when the server couldn't be
     * reached it is the epoch-millisecond time of the fetch the cached copy came from.
     */
    data class Success(
        val data: ProsumerDashboardResponse,
        val lastSyncedAt: Long? = null
    ) : DashboardState() {
        /** True when this is the offline cache's copy, not a fresh fetch. */
        val isCached: Boolean get() = lastSyncedAt != null
    }

    /**
     * Load failed with a user-readable [message]. [code] is the HTTP status (401, 403, 500, …)
     * or null for a network failure, so the Activity can treat an expired session (401)
     * differently from a retryable error.
     */
    data class Error(val message: String, val code: Int? = null) : DashboardState()
}

// ==================== VIEWMODEL ====================

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DashboardRepository(application.applicationContext)
    private val session = SessionManager(application.applicationContext)

    private val _dashboardState = MutableLiveData<DashboardState>()
    val dashboardState: LiveData<DashboardState> = _dashboardState

    // ==================== SESSION (HEADER) ====================
    // Plain getters — these don't change while the screen is open, so no LiveData.

    val userName: String get() = session.getName().orEmpty()
    val userEmail: String get() = session.getEmail().orEmpty()
    val userNic: String? get() = session.getNic()

    // ==================== LOAD ====================

    /**
     * Fetches the dashboard. Ignored while a request is already in flight, so a double-tap on
     * Retry can't fire two calls. Also used for Retry after an Error.
     */
    fun loadDashboard() {
        if (_dashboardState.value is DashboardState.Loading) return

        _dashboardState.value = DashboardState.Loading
        viewModelScope.launch {
            when (val result = repo.getMyDashboard()) {
                is CachedResult.Fresh -> _dashboardState.value = DashboardState.Success(result.data)
                is CachedResult.Cached -> _dashboardState.value =
                    DashboardState.Success(result.data, result.lastSyncedAt)
                is CachedResult.Failed -> _dashboardState.value =
                    DashboardState.Error(result.error.message, result.error.code)
            }
        }
    }
}
