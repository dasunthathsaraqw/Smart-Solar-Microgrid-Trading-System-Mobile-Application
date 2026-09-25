package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.ReservationRepository
import kotlinx.coroutines.launch

/**
 * File: QrDisplayViewModel.kt
 * Purpose: Fetches the QR token for an Approved reservation and exposes the outcome as
 *          LiveData<QrState>. Turning the token into an image is the Activity's job.
 * Author: Mobile Team
 * Date: 2026
 *
 * Like DashboardViewModel there is no Idle state or reset. The state has no value until
 * loadQr() is first called. The server only issues a token for Approved reservations; anything
 * else comes back as Error with the server's own message.
 */

// ==================== STATE ====================

sealed class QrState {
    /** A request is in flight — show spinner, hide QR/error. */
    object Loading : QrState()

    /** Token loaded; [qrToken] is the raw string to encode as a QR image. */
    data class Success(val qrToken: String) : QrState()

    /** Load failed with a user-readable [message]; [code] is the HTTP status, null for network. */
    data class Error(val message: String, val code: Int? = null) : QrState()
}

// ==================== VIEWMODEL ====================

class QrDisplayViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ReservationRepository(application.applicationContext)

    private val _state = MutableLiveData<QrState>()
    val state: LiveData<QrState> = _state

    // ==================== LOAD ====================

    /** Fetches the QR token for [reservationId]. Ignored while in flight. Also used for Retry. */
    fun loadQr(reservationId: String) {
        if (_state.value is QrState.Loading) return

        _state.value = QrState.Loading
        viewModelScope.launch {
            when (val result = repo.getReservationQr(reservationId)) {
                is ApiResult.Success -> _state.value = QrState.Success(result.data.qrToken)
                is ApiResult.Error -> _state.value = QrState.Error(result.message, result.code)
            }
        }
    }
}
