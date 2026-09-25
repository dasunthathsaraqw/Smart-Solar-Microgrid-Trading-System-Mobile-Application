package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import kotlinx.coroutines.launch

/**
 * File: CreateBookingViewModel.kt
 * Purpose: Submits a new reservation (final step of the Create Booking flow) and exposes the
 *          outcome as LiveData<BookingActionState>.
 * Author: Mobile Team
 * Date: 2026
 *
 * This is a one-shot action, so it follows the AuthViewModel pattern: call resetState() once a
 * Success or Error has been handled, otherwise a rotation replays it (duplicate toast, or a
 * second navigation to the summary).
 */

// ==================== STATE ====================

sealed class BookingActionState {
    /** Nothing in flight, nothing to show. Initial value, and the value after a reset. */
    object Idle : BookingActionState()

    /** The request is in flight — show spinner, disable Confirm. */
    object Loading : BookingActionState()

    /** Reservation created; [response] is the action summary for the summary screen. */
    data class Success(val response: ReservationActionResponse) : BookingActionState()

    /**
     * Rejected or failed. [message] is the server's own text where there is one (409 slot
     * already booked, 400 rule violation, …); [code] is the HTTP status, null for network.
     */
    data class Error(val message: String, val code: Int? = null) : BookingActionState()
}

// ==================== VIEWMODEL ====================

/**
 * The repository is a constructor parameter that defaults to the real one, so the app behaves
 * exactly as before while a unit test can pass a fake. @JvmOverloads keeps the plain
 * (Application) constructor that Android's default ViewModel factory looks for.
 */
class CreateBookingViewModel @JvmOverloads constructor(
    application: Application,
    private val repo: ReservationRepository = ReservationRepository(application.applicationContext)
) : AndroidViewModel(application) {

    private val _state = MutableLiveData<BookingActionState>(BookingActionState.Idle)
    val state: LiveData<BookingActionState> = _state

    // ==================== CONFIRM ====================

    /**
     * POST /api/reservations/my. Ignored while a request is in flight or after a success that
     * hasn't been reset yet, so a double-tap can never submit two reservations. All booking
     * rules are enforced server-side; nothing is validated here.
     */
    fun confirmBooking(stationId: String, slotId: String) {
        val current = _state.value
        if (current is BookingActionState.Loading || current is BookingActionState.Success) return

        _state.value = BookingActionState.Loading
        viewModelScope.launch {
            when (val result = repo.createReservation(stationId, slotId)) {
                is ApiResult.Success -> _state.value = BookingActionState.Success(result.data)
                is ApiResult.Error -> _state.value =
                    BookingActionState.Error(result.message, result.code)
            }
        }
    }

    fun resetState() {
        _state.value = BookingActionState.Idle
    }
}
