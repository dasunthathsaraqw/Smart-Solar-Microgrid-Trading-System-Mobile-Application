package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.data.repository.CachedResult
import com.example.smartmicrogrid.data.repository.StationRepository
import kotlinx.coroutines.launch

/**
 * File: StationPickerViewModel.kt
 * Purpose: Loads the station list for the first step of the Create Booking flow and exposes
 *          the outcome as LiveData<StationListState>.
 * Author: Mobile Team
 * Date: 2026
 *
 * Like DashboardViewModel there is no Idle state or reset: the list is always loading,
 * loaded, or failed. The state has no value until loadStations() is first called.
 */

// ==================== STATE ====================

sealed class StationListState {
    /** A request is in flight — show spinner, hide list/error. */
    object Loading : StationListState()

    /**
     * Stations loaded (may be empty). [lastSyncedAt] is null for fresh data; when the server
     * couldn't be reached it is the epoch-millisecond time of the fetch the cached copy came from.
     */
    data class Success(
        val stations: List<StationResponse>,
        val lastSyncedAt: Long? = null
    ) : StationListState() {
        /** True when this is the offline cache's copy, not a fresh fetch. */
        val isCached: Boolean get() = lastSyncedAt != null
    }

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : StationListState()
}

// ==================== VIEWMODEL ====================

class StationPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = StationRepository(application.applicationContext)

    private val _state = MutableLiveData<StationListState>()
    val state: LiveData<StationListState> = _state

    // ==================== LOAD ====================

    /** Fetches stations. Ignored while a request is in flight. Also used for Retry. */
    fun loadStations() {
        if (_state.value is StationListState.Loading) return

        _state.value = StationListState.Loading
        viewModelScope.launch {
            when (val result = repo.getStations()) {
                is CachedResult.Fresh -> _state.value = StationListState.Success(result.data)
                is CachedResult.Cached -> _state.value =
                    StationListState.Success(result.data, result.lastSyncedAt)
                is CachedResult.Failed -> _state.value =
                    StationListState.Error(result.error.message, result.error.code)
            }
        }
    }
}
