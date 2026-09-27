package com.example.smartmicrogrid.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.NearbyStationResponse
import com.example.smartmicrogrid.data.repository.ApiResult
import com.example.smartmicrogrid.data.repository.StationRepository
import kotlinx.coroutines.launch

/**
 * File: NearbyStationsViewModel.kt
 * Purpose: Tracks the whole "stations near me" flow as one LiveData<NearbyState>: getting the
 *          user's location, then loading GET /api/stations/nearby for it. The Activity owns the
 *          permission prompt and the location client and reports their progress here; this
 *          ViewModel owns the state and the API call.
 * Author: Mobile Team
 * Date: 2026
 *
 * The states are persistent screen states, not one-shot events, so there is no reset: a
 * rotation simply replays the current one. The Activity starts the flow only while the state
 * is still Idle. Retry is the Activity re-running the flow from the top (fresh location).
 */

// ==================== STATE ====================

sealed class NearbyState {
    /** Nothing started yet. Initial value. */
    object Idle : NearbyState()

    /** Waiting for the device location — "Getting your location...". */
    object LocationLoading : NearbyState()

    /** Location known, waiting for the API — "Finding stations...". */
    object StationsLoading : NearbyState()

    /** Stations loaded, closest first (may be empty: nothing within the radius). */
    data class Success(val stations: List<NearbyStationResponse>) : NearbyState()

    /**
     * No location to search from. [permissionDenied] tells the UI which recovery to offer:
     * true  -> the user refused location access (Grant Permission / Open Settings);
     * false -> access is fine but no fix could be had, e.g. location is switched off (Retry).
     */
    data class LocationError(
        val message: String,
        val permissionDenied: Boolean = false
    ) : NearbyState()

    /** The stations request failed; [code] is the HTTP status, null for a network failure. */
    data class Error(val message: String, val code: Int? = null) : NearbyState()
}

// ==================== VIEWMODEL ====================

class NearbyStationsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = StationRepository(application.applicationContext)

    private val _state = MutableLiveData<NearbyState>(NearbyState.Idle)
    val state: LiveData<NearbyState> = _state

    /**
     * The (latitude, longitude) the current or last search was made from — the user's position.
     * The Activity uses it to frame the camera (include the user, or centre on them when no
     * stations came back), and it survives a rotation, unlike the Activity's own fields.
     */
    var searchLocation: Pair<Double, Double>? = null
        private set

    // ==================== LOCATION (reported by the Activity) ====================

    /** The Activity has permission and is asking the device for a location. */
    fun onLocationRequested() {
        _state.value = NearbyState.LocationLoading
    }

    /** The user refused location access. */
    fun onLocationDenied() {
        _state.value = NearbyState.LocationError(
            message = getApplication<Application>().getString(R.string.msg_location_permission_denied),
            permissionDenied = true
        )
    }

    /** Permission is fine but the device gave no location (off, no fix, or an error). */
    fun onLocationUnavailable() {
        _state.value = NearbyState.LocationError(
            message = getApplication<Application>().getString(R.string.msg_location_unavailable),
            permissionDenied = false
        )
    }

    // ==================== LOAD ====================

    /**
     * Loads stations near ([latitude], [longitude]) with the endpoint's defaults (10 km, up to
     * 20). Call once the location is known. Ignored while a request is already in flight.
     */
    fun loadNearbyStations(latitude: Double, longitude: Double) {
        if (_state.value is NearbyState.StationsLoading) return

        searchLocation = latitude to longitude
        _state.value = NearbyState.StationsLoading
        viewModelScope.launch {
            when (val result = repo.getNearbyStations(latitude, longitude)) {
                is ApiResult.Success -> _state.value = NearbyState.Success(result.data)
                is ApiResult.Error -> _state.value = NearbyState.Error(result.message, result.code)
            }
        }
    }
}
