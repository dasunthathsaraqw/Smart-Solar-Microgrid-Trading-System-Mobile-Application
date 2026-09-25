package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.StationRepository
import com.example.smartmicrogrid.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * File: SlotManagementViewModel.kt
 * Purpose: Drives the operator's slot management: lists the available slots of the operator's
 *          own station (LiveData<SlotListState>) and edits one (LiveData<SlotUpdateState>). The
 *          station comes from SessionManager, never from the UI.
 * Author: Mobile Team
 * Date: 2026
 *
 * Two separate streams, because an edit happens while the list is already on screen:
 * - listState:   the load, like the other list screens (no Idle, no reset). SlotListState is the
 *                sealed class declared in SlotPickerViewModel.kt (same package, same
 *                Loading/Success(slots)/Error shape); it is reused, not redefined.
 * - updateState: a one-shot action, like CreateBookingViewModel — call resetUpdateState() once a
 *                Success or Error has been handled, or a rotation replays it.
 *
 * Only unbooked slots in the next 7 days are listed: there is no list-all or create endpoint.
 */

// ==================== STATE ====================

sealed class SlotUpdateState {
    /** Nothing in flight, nothing to show. Initial value, and the value after a reset. */
    object Idle : SlotUpdateState()

    /** The edit is in flight — disable the dialog's inputs. */
    object Loading : SlotUpdateState()

    /** Saved; [slot] is the server's updated version. */
    data class Success(val slot: SlotResponse) : SlotUpdateState()

    /** Rejected or failed: the server's own [message] (e.g. slot already booked). */
    data class Error(val message: String, val code: Int? = null) : SlotUpdateState()
}

// ==================== VIEWMODEL ====================

class SlotManagementViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = StationRepository(application.applicationContext)
    private val session = SessionManager(application.applicationContext)

    private val _listState = MutableLiveData<SlotListState>()
    val listState: LiveData<SlotListState> = _listState

    private val _updateState = MutableLiveData<SlotUpdateState>(SlotUpdateState.Idle)
    val updateState: LiveData<SlotUpdateState> = _updateState

    // ==================== LOAD ====================

    /** Loads the operator's station's available slots. Ignored while in flight. Also for Retry. */
    fun loadSlots() {
        if (_listState.value is SlotListState.Loading) return

        val stationId = session.getStationId()
        if (stationId.isNullOrBlank()) {
            _listState.value = SlotListState.Error(
                getApplication<Application>().getString(R.string.msg_operator_station_missing)
            )
            return
        }

        _listState.value = SlotListState.Loading
        viewModelScope.launch {
            when (val result = repo.getAvailableSlots(stationId)) {
                is ApiResult.Success -> _listState.value = SlotListState.Success(result.data)
                is ApiResult.Error -> _listState.value =
                    SlotListState.Error(result.message, result.code)
            }
        }
    }

    // ==================== UPDATE ====================

    /**
     * Saves changes to [slotId]. Pass null for anything unchanged — only the rest is sent. All
     * rules (booked slots are locked, times must make sense) are the server's; its message
     * comes back in SlotUpdateState.Error. Ignored while an edit is in flight, or after a
     * success that hasn't been reset yet, so a double-tap can't save twice.
     *
     * On success the edited slot is swapped into the loaded list in place (no reload, so no
     * spinner flash and no extra request), and the list keeps its order.
     */
    fun updateSlot(slotId: String, startTime: String?, endTime: String?, capacityKw: Double?) {
        val current = _updateState.value
        if (current is SlotUpdateState.Loading || current is SlotUpdateState.Success) return

        _updateState.value = SlotUpdateState.Loading
        viewModelScope.launch {
            when (val result = repo.updateSlot(slotId, startTime, endTime, capacityKw)) {
                is ApiResult.Success -> {
                    replaceInList(result.data)
                    _updateState.value = SlotUpdateState.Success(result.data)
                }
                is ApiResult.Error -> _updateState.value =
                    SlotUpdateState.Error(result.message, result.code)
            }
        }
    }

    fun resetUpdateState() {
        _updateState.value = SlotUpdateState.Idle
    }

    // ==================== INTERNAL ====================

    /** Swaps [updated] into the loaded list by id. A new list is emitted, never mutated. */
    private fun replaceInList(updated: SlotResponse) {
        val loaded = _listState.value as? SlotListState.Success ?: return
        _listState.value = SlotListState.Success(
            loaded.slots.map { if (it.id == updated.id) updated else it }
        )
    }
}
