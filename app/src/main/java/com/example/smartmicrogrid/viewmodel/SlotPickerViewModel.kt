package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.StationRepository
import kotlinx.coroutines.launch

/**
 * File: SlotPickerViewModel.kt
 * Purpose: Loads the available slots for one station (second step of the Create Booking flow)
 *          and exposes the outcome as LiveData<SlotListState>.
 * Author: Mobile Team
 * Date: 2026
 *
 * Like DashboardViewModel there is no Idle state or reset. An empty Success list is the
 * "no slots available" case — the Activity decides how to present it.
 */

// ==================== STATE ====================

sealed class SlotListState {
    /** A request is in flight — show spinner, hide list/empty/error. */
    object Loading : SlotListState()

    /** Slots loaded (may be empty). */
    data class Success(val slots: List<SlotResponse>) : SlotListState()

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : SlotListState()
}

// ==================== VIEWMODEL ====================

class SlotPickerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = StationRepository(application.applicationContext)

    private val _state = MutableLiveData<SlotListState>()
    val state: LiveData<SlotListState> = _state

    // ==================== LOAD ====================

    /** Fetches available slots for [stationId]. Ignored while in flight. Also used for Retry. */
    fun loadSlots(stationId: String) {
        if (_state.value is SlotListState.Loading) return

        _state.value = SlotListState.Loading
        viewModelScope.launch {
            when (val result = repo.getAvailableSlots(stationId)) {
                is ApiResult.Success -> _state.value = SlotListState.Success(result.data)
                is ApiResult.Error -> _state.value =
                    SlotListState.Error(result.message, result.code)
            }
        }
    }
}
