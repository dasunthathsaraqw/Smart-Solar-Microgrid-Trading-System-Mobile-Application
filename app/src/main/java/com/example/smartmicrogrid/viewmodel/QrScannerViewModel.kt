package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import com.example.smartmicrogrid.utils.SessionManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * File: QrScannerViewModel.kt
 * Purpose: Drives the operator's QR check-in as one LiveData<ScanState>: a scanned token is first
 *          VERIFIED (dry run, nothing changes) so the operator can see what it is for, and only
 *          on their confirmation is it COMPLETED (verify + mark Completed, atomically). The
 *          ViewModel holds the token between the two steps; the station is the operator's own,
 *          read from SessionManager.
 * Author: Mobile Team
 * Date: 2026
 *
 * Flow:  Idle -> Verifying -> Verified -> Completing -> Completed        (then reset() -> Idle)
 *                    \-> Error (token rejected)   \-> Error (completion rejected)
 *
 * Only the ViewModel talks to the API; the Activity owns the camera and reports scans here.
 */

// ==================== STATE ====================

sealed class ScanState {
    /** Ready for a scan. Initial value, and the value after reset(). */
    object Idle : ScanState()

    /** A scanned token is being checked (dry run). */
    object Verifying : ScanState()

    /** The token is valid; [reservation] is what it is for, awaiting the operator's confirmation. */
    data class Verified(val reservation: ReservationResponse) : ScanState()

    /** Completing the reservation — the real charging-session completion is in flight. */
    object Completing : ScanState()

    /** The reservation is now Completed. */
    data class Completed(val reservation: ReservationResponse) : ScanState()

    /**
     * A step failed, with the server's own [message] (or a network message); [code] is the HTTP
     * status, null for a network failure.
     *
     * [reservation] tells which step: null = VERIFY failed (bad/unknown token — go back to
     * scanning); non-null = COMPLETING failed for that verified reservation (keep showing it; a
     * network failure can be retried with confirmComplete(), a server rejection such as "already
     * completed" cannot).
     */
    data class Error(
        val message: String,
        val code: Int? = null,
        val reservation: ReservationResponse? = null
    ) : ScanState()
}

// ==================== VIEWMODEL ====================

class QrScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReservationRepository(application.applicationContext)
    private val session = SessionManager(application.applicationContext)

    private val _state = MutableLiveData<ScanState>(ScanState.Idle)
    val state: LiveData<ScanState> = _state

    /** The scanned token, held from a successful scan until completion or reset(). */
    private var heldToken: String? = null

    /** The reservation the held token verified to, so a failed completion can keep showing it. */
    private var heldReservation: ReservationResponse? = null

    private var verifyJob: Job? = null

    // ==================== STEP 1: VERIFY ====================

    /**
     * Called by the Activity for each code the camera reads. Only acted on while Idle: a camera
     * reports the same code many times a second, and it must not restart a check that is already
     * running or overwrite a verified result. After an Error, call reset() to scan again.
     */
    fun onQrScanned(qrToken: String) {
        if (_state.value !is ScanState.Idle) return

        val stationId = stationIdOrError() ?: return

        heldToken = qrToken
        _state.value = ScanState.Verifying
        verifyJob = viewModelScope.launch {
            when (val result = repo.verifyQr(qrToken, stationId)) {
                is ApiResult.Success -> {
                    heldReservation = result.data
                    _state.value = ScanState.Verified(result.data)
                }
                is ApiResult.Error -> {
                    heldToken = null
                    _state.value = ScanState.Error(result.message, result.code)
                }
            }
        }
    }

    // ==================== STEP 2: COMPLETE ====================

    /**
     * Completes the reservation for the held token — the operator's confirmation. Allowed from
     * Verified, or from a completion Error (retry after a network failure). Ignored otherwise,
     * including while a completion is already in flight, so a double-tap can't send it twice.
     */
    fun confirmComplete() {
        val current = _state.value
        val retryable = current is ScanState.Error && current.reservation != null
        if (current !is ScanState.Verified && !retryable) return

        val token = heldToken ?: return
        val stationId = stationIdOrError() ?: return

        _state.value = ScanState.Completing
        viewModelScope.launch {
            when (val result = repo.scanComplete(token, stationId)) {
                is ApiResult.Success -> {
                    heldToken = null // done: the token can't be completed again from here
                    heldReservation = null
                    _state.value = ScanState.Completed(result.data)
                }
                is ApiResult.Error -> _state.value =
                    ScanState.Error(result.message, result.code, reservation = heldReservation)
            }
        }
    }

    // ==================== RESET ====================

    /**
     * Back to Idle, ready to scan again. Ignored while a completion is in flight: the request
     * can't be recalled and may already have succeeded on the server, so its outcome must be
     * shown. A verify in flight is only a dry run, so it is simply dropped.
     */
    fun reset() {
        if (_state.value is ScanState.Completing) return

        verifyJob?.cancel()
        heldToken = null
        heldReservation = null
        _state.value = ScanState.Idle
    }

    // ==================== INTERNAL ====================

    /** The operator's own station id, or null after emitting an Error if there isn't one. */
    private fun stationIdOrError(): String? {
        val stationId = session.getStationId()
        if (stationId.isNullOrBlank()) {
            _state.value = ScanState.Error(
                getApplication<Application>().getString(R.string.msg_operator_station_missing)
            )
            return null
        }
        return stationId
    }
}
