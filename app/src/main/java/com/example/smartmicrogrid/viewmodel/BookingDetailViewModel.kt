package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import kotlinx.coroutines.launch

/**
 * File: BookingDetailViewModel.kt
 * Purpose: Loads one reservation for the detail screen (LiveData<BookingDetailState>) and runs
 *          the update (move to a new slot) and cancel actions (LiveData<BookingActionState>).
 * Author: Mobile Team
 * Date: 2026
 *
 * Two separate streams because the actions happen after the detail is already on screen:
 * - state:       the detail load, like DashboardViewModel (no Idle, no reset).
 * - actionState: a one-shot action, like CreateBookingViewModel — call resetActionState() once
 *                a Success or Error has been handled, or a rotation replays it.
 *
 * BookingActionState is the sealed class declared in CreateBookingViewModel.kt (same package,
 * same Idle/Loading/Success(ReservationActionResponse)/Error shape); it is reused, not redefined.
 */

// ==================== STATE ====================

sealed class BookingDetailState {
    /** A request is in flight — show spinner, hide content/error. */
    object Loading : BookingDetailState()

    /** Reservation loaded. */
    data class Success(val reservation: ReservationResponse) : BookingDetailState()

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : BookingDetailState()
}

// ==================== VIEWMODEL ====================

class BookingDetailViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReservationRepository(application.applicationContext)

    private val _state = MutableLiveData<BookingDetailState>()
    val state: LiveData<BookingDetailState> = _state

    private val _actionState = MutableLiveData<BookingActionState>(BookingActionState.Idle)
    val actionState: LiveData<BookingActionState> = _actionState

    // ==================== LOAD ====================

    /** Fetches reservation [id]. Ignored while a request is in flight. Also used for Retry. */
    fun loadDetail(id: String) {
        if (_state.value is BookingDetailState.Loading) return

        _state.value = BookingDetailState.Loading
        viewModelScope.launch {
            when (val result = repo.getReservationDetail(id)) {
                is ApiResult.Success -> _state.value = BookingDetailState.Success(result.data)
                is ApiResult.Error -> _state.value =
                    BookingDetailState.Error(result.message, result.code)
            }
        }
    }

    // ==================== ACTIONS ====================

    /**
     * Moves reservation [id] to [newSlotId]. All rules (status, 12-hour notice, slot free)
     * are enforced by the server; its message comes back in BookingActionState.Error.
     */
    fun updateSlot(id: String, newSlotId: String) =
        runAction { repo.updateReservation(id, newSlotId) }

    /** Cancels reservation [id]; [reason] is optional. The server enforces the 12-hour notice. */
    fun cancelBooking(id: String, reason: String?) =
        runAction { repo.cancelReservation(id, reason) }

    fun resetActionState() {
        _actionState.value = BookingActionState.Idle
    }

    /**
     * Shared by both actions. Ignored while a request is in flight or after a success that
     * hasn't been reset yet, so a double-tap can never submit the same action twice.
     */
    private fun runAction(call: suspend () -> ApiResult<ReservationActionResponse>) {
        val current = _actionState.value
        if (current is BookingActionState.Loading || current is BookingActionState.Success) return

        _actionState.value = BookingActionState.Loading
        viewModelScope.launch {
            when (val result = call()) {
                is ApiResult.Success -> _actionState.value = BookingActionState.Success(result.data)
                is ApiResult.Error -> _actionState.value =
                    BookingActionState.Error(result.message, result.code)
            }
        }
    }
}
