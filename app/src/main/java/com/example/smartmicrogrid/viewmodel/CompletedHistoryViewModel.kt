package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.PagedResult
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import com.example.smartmicrogrid.utils.SessionManager
import kotlinx.coroutines.launch

/**
 * File: CompletedHistoryViewModel.kt
 * Purpose: Loads the operator's completed-reservation history, one page at a time
 *          (GET /api/reservations/operator/history), and exposes it as LiveData<HistoryState>.
 *          The station is the operator's own, read from SessionManager — never chosen in the UI.
 * Author: Mobile Team
 * Date: 2026
 *
 * HOW PAGES ARE MERGED: the Success state always holds ONE PagedResult whose `items` is the
 * ACCUMULATED list of every page loaded so far, and whose paging fields (page, totalPages,
 * hasNextPage, ...) are those of the LATEST page. Each load builds a new list (never mutates
 * the old one), so the Activity can hand `items` straight to a ListAdapter and DiffUtil animates
 * only the appended rows. Pages are de-duplicated by reservation id, because a completion
 * landing between two page requests would otherwise shift a row onto the next page twice.
 *
 * LoadingMore and Error carry the list loaded so far, so a rotation or a failed "load more"
 * never wipes what is on screen.
 */

// ==================== STATE ====================

sealed class HistoryState {
    /** First page in flight (nothing to show yet) — show spinner, hide list/empty/error. */
    object Loading : HistoryState()

    /** Loaded through [PagedResult.page]; `items` is everything loaded so far. May be empty. */
    data class Success(val data: PagedResult<ReservationResponse>) : HistoryState()

    /** The next page is in flight; [current] is the list to keep showing, with a footer spinner. */
    data class LoadingMore(val current: PagedResult<ReservationResponse>) : HistoryState()

    /**
     * A request failed with a user-readable [message]; [code] is the HTTP status, null for a
     * network failure. [current] is null when the FIRST page failed (nothing to show — show the
     * error/retry screen) and non-null when a LATER page failed (keep the list, offer retry).
     */
    data class Error(
        val message: String,
        val code: Int? = null,
        val current: PagedResult<ReservationResponse>? = null
    ) : HistoryState()
}

// ==================== VIEWMODEL ====================

class CompletedHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReservationRepository(application.applicationContext)
    private val session = SessionManager(application.applicationContext)

    private val _state = MutableLiveData<HistoryState>()
    val state: LiveData<HistoryState> = _state

    // ==================== LOAD ====================

    /**
     * Loads [page] of the history. Page 1 (the default) starts fresh and replaces whatever was
     * loaded; a later page is appended to the current list. Also used for Retry after a failed
     * first page. Ignored while a request is already in flight.
     */
    fun loadHistory(page: Int = 1) {
        fetch(page, previous = if (page == 1) null else currentData())
    }

    /**
     * Loads the page after the last one shown and appends it. Does nothing if there is nothing
     * loaded yet, no further page, or a request is in flight. Also used for Retry after a failed
     * "load more".
     */
    fun loadNextPage() {
        val current = currentData() ?: return
        if (!current.hasNextPage) return
        fetch(current.page + 1, previous = current)
    }

    // ==================== INTERNAL ====================

    /** The list loaded so far, whatever state it is wrapped in; null before the first success. */
    private fun currentData(): PagedResult<ReservationResponse>? = when (val s = _state.value) {
        is HistoryState.Success -> s.data
        is HistoryState.LoadingMore -> s.current
        is HistoryState.Error -> s.current
        else -> null
    }

    private fun fetch(page: Int, previous: PagedResult<ReservationResponse>?) {
        val inFlight = _state.value
        if (inFlight is HistoryState.Loading || inFlight is HistoryState.LoadingMore) return

        val stationId = session.getStationId()
        if (stationId.isNullOrBlank()) {
            _state.value = HistoryState.Error(
                message = getApplication<Application>().getString(R.string.msg_operator_station_missing),
                current = previous
            )
            return
        }

        _state.value = if (previous == null) HistoryState.Loading else HistoryState.LoadingMore(previous)
        viewModelScope.launch {
            when (val result = repo.getCompletedHistory(stationId, page = page, pageSize = PAGE_SIZE)) {
                is ApiResult.Success -> {
                    val latest = result.data
                    _state.value = HistoryState.Success(
                        if (previous == null) latest
                        else latest.copy(items = (previous.items + latest.items).distinctBy { it.id })
                    )
                }
                is ApiResult.Error -> _state.value =
                    HistoryState.Error(result.message, result.code, previous)
            }
        }
    }

    private companion object {
        const val PAGE_SIZE = 20
    }
}
