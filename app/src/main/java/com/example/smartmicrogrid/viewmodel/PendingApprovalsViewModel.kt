package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import kotlinx.coroutines.launch

/**
 * File: PendingApprovalsViewModel.kt
 * Purpose: Loads the queue of reservations awaiting approval (GET /api/reports/pending-approvals)
 *          for the operator's read-only Pending Approvals screen and exposes the outcome as
 *          LiveData<PendingListState>.
 * Author: Mobile Team
 * Date: 2026
 *
 * Read-only: there is no approve call on mobile (approval happens in the web app), so this
 * ViewModel only loads. Same shape as MyBookingsViewModel's list state: no Idle, no reset, no
 * value until the first load, and an empty Success list is the "nothing pending" case.
 */

// ==================== STATE ====================

sealed class PendingListState {
    /** A request is in flight — show spinner, hide list/empty/error. */
    object Loading : PendingListState()

    /** Queue loaded (may be empty). */
    data class Success(val reservations: List<ReservationResponse>) : PendingListState()

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : PendingListState()
}

// ==================== VIEWMODEL ====================

class PendingApprovalsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReservationRepository(application.applicationContext)

    private val _state = MutableLiveData<PendingListState>()
    val state: LiveData<PendingListState> = _state

    // ==================== LOAD ====================

    /** Fetches up to [count] pending reservations. Ignored while in flight. Also used for Retry. */
    fun loadPending(count: Int = DEFAULT_COUNT) {
        if (_state.value is PendingListState.Loading) return

        _state.value = PendingListState.Loading
        viewModelScope.launch {
            when (val result = repo.getPendingApprovals(count)) {
                is ApiResult.Success -> _state.value = PendingListState.Success(result.data)
                is ApiResult.Error -> _state.value =
                    PendingListState.Error(result.message, result.code)
            }
        }
    }

    private companion object {
        const val DEFAULT_COUNT = 20
    }
}
