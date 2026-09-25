package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.OperatorDashboardResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.DashboardRepository
import com.example.smartmicrogrid.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * File: OperatorDashboardViewModel.kt
 * Purpose: Drives the grid operator's dashboard. Loads GET /api/reports/operator-dashboard
 *          (scoped to the operator's own station by the server) through DashboardRepository and
 *          exposes the outcome as LiveData<OperatorDashboardState>, plus the signed-in
 *          operator's name, email and station id for the header.
 * Author: Mobile Team
 * Date: 2026
 *
 * Same shape as DashboardViewModel: no Idle state or reset, and no value until loadDashboard()
 * is first called.
 */

// ==================== STATE ====================

sealed class OperatorDashboardState {
    /** A request is in flight — show spinner, hide content/error. */
    object Loading : OperatorDashboardState()

    /** Dashboard loaded. */
    data class Success(val data: OperatorDashboardResponse) : OperatorDashboardState()

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : OperatorDashboardState()
}

// ==================== VIEWMODEL ====================

class OperatorDashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = DashboardRepository(application.applicationContext)
    private val session = SessionManager(application.applicationContext)

    private val _state = MutableLiveData<OperatorDashboardState>()
    val state: LiveData<OperatorDashboardState> = _state

    // ==================== SESSION (HEADER) ====================
    // Plain getters — these don't change while the screen is open, so no LiveData.

    val userName: String get() = session.getName().orEmpty()
    val userEmail: String get() = session.getEmail().orEmpty()
    val stationId: String? get() = session.getStationId()

    // ==================== LOAD ====================

    /** Fetches the dashboard. Ignored while a request is in flight. Also used for Retry. */
    fun loadDashboard() {
        if (_state.value is OperatorDashboardState.Loading) return

        _state.value = OperatorDashboardState.Loading
        viewModelScope.launch {
            when (val result = repo.getOperatorDashboard()) {
                is ApiResult.Success -> _state.value = OperatorDashboardState.Success(result.data)
                is ApiResult.Error -> _state.value =
                    OperatorDashboardState.Error(result.message, result.code)
            }
        }
    }
}
