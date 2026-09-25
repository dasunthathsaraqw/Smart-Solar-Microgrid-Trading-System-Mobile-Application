package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.repository.CachedResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import com.example.smartmicrogrid.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * File: MyBookingsViewModel.kt
 * Purpose: Loads the signed-in prosumer's reservations for the My Bookings tabs and exposes the
 *          outcome as LiveData<BookingListState>. Remembers which tab (status filter) is
 *          selected so a rotation doesn't lose it.
 * Author: Mobile Team
 * Date: 2026
 *
 * Like DashboardViewModel there is no Idle state or reset. The state has no value until the
 * first loadBookings() call. An empty Success list is the "no bookings" case.
 */

// ==================== STATE ====================

sealed class BookingListState {
    /** A request is in flight — show spinner, hide list/empty/error. */
    object Loading : BookingListState()

    /**
     * Reservations loaded for the selected tab (may be empty). [lastSyncedAt] is null for fresh
     * data; when the server couldn't be reached it is the epoch-millisecond time of the fetch the
     * cached copy came from.
     */
    data class Success(
        val bookings: List<ReservationResponse>,
        val lastSyncedAt: Long? = null
    ) : BookingListState() {
        /** True when this is the offline cache's copy, not a fresh fetch. */
        val isCached: Boolean get() = lastSyncedAt != null
    }

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : BookingListState()
}

// ==================== VIEWMODEL ====================

class MyBookingsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReservationRepository(application.applicationContext)

    private val _state = MutableLiveData<BookingListState>()
    val state: LiveData<BookingListState> = _state

    /**
     * The status filter of the selected tab: one of Constants.STATUS_*, or null for "All".
     * Starts on Pending (the first tab). The Activity reads this to re-select the right tab
     * after a rotation, without triggering another load.
     */
    var selectedStatus: String? = Constants.STATUS_PENDING
        private set

    private var loadJob: Job? = null

    // ==================== LOAD ====================

    /**
     * Loads reservations for [status] (null = all) and makes it the selected tab.
     *
     * Not guarded like the other loaders: a tab switch while a request is in flight must win,
     * so the previous request is cancelled and its late response can never overwrite the
     * newer tab's list.
     */
    fun loadBookings(status: String? = null) {
        selectedStatus = status
        loadJob?.cancel()

        _state.value = BookingListState.Loading
        loadJob = viewModelScope.launch {
            when (val result = repo.getMyReservations(status)) {
                is CachedResult.Fresh -> _state.value = BookingListState.Success(result.data)
                is CachedResult.Cached -> _state.value =
                    BookingListState.Success(result.data, result.lastSyncedAt)
                is CachedResult.Failed -> _state.value =
                    BookingListState.Error(result.error.message, result.error.code)
            }
        }
    }

    /** Reloads the currently selected tab — Retry, or refreshing after returning to the list. */
    fun reload() = loadBookings(selectedStatus)
}
