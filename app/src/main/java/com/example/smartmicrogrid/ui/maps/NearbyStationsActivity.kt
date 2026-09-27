package com.example.smartmicrogrid.ui.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.NearbyStationResponse
import com.example.smartmicrogrid.databinding.ActivityNearbyStationsBinding
import com.example.smartmicrogrid.databinding.DialogStationInfoBinding
import com.example.smartmicrogrid.ui.booking.SlotPickerActivity
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.viewmodel.NearbyState
import com.example.smartmicrogrid.viewmodel.NearbyStationsViewModel
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * File: NearbyStationsActivity.kt
 * Purpose: Google Maps screen of stations near the user. Asks for location permission, gets a
 *          location fix, loads GET /api/stations/nearby for it through NearbyStationsViewModel,
 *          and plots one marker per station. Tapping a marker opens a bottom sheet whose
 *          "View Slots" button goes straight to SlotPickerActivity (skipping the station list).
 * Author: Mobile Team
 * Date: 2026
 *
 * The Activity owns the permission prompt and the location client and reports their progress to
 * the ViewModel, which owns the screen state. The flow starts only while that state is Idle, so
 * a rotation keeps the state (and an in-flight location request) instead of starting over.
 */
class NearbyStationsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityNearbyStationsBinding
    private val viewModel by viewModels<NearbyStationsViewModel>()
    private lateinit var fusedClient: FusedLocationProviderClient

    private var googleMap: GoogleMap? = null

    /** Stations to plot. Held here because the map and the API result can arrive in either order. */
    private var stations: List<NearbyStationResponse>? = null

    private var locationCancellation: CancellationTokenSource? = null

    // Both permissions are requested together: for apps targeting Android 12+ the system ignores
    // a lone ACCESS_FINE_LOCATION request, and the user may grant only approximate location.
    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (hasLocationPermission()) {
                fetchLocation()
            } else {
                viewModel.onLocationDenied()
            }
        }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNearbyStationsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        binding.toolbar.setNavigationOnClickListener { finish() }
        setupButtons()

        // The map fragment is created by the layout; we only ask it for the GoogleMap.
        (supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment).getMapAsync(this)

        observeState()

        // Start only from a clean slate — after a rotation the state is already underway.
        if (viewModel.state.value is NearbyState.Idle) {
            startFlow()
        }
    }

    override fun onResume() {
        super.onResume()
        // Back from the system Settings screen after granting permission there: carry on.
        val state = viewModel.state.value
        if (state is NearbyState.LocationError && state.permissionDenied && hasLocationPermission()) {
            startFlow()
        }
    }

    override fun onDestroy() {
        // A rotation must not cancel the request (its result still reaches the ViewModel);
        // only leaving the screen for good does.
        if (isFinishing) locationCancellation?.cancel()
        super.onDestroy()
    }

    // ==================== BUTTONS ====================

    private fun setupButtons() {
        binding.btnGrantPermission.setOnClickListener { startFlow() }
        binding.btnRetry.setOnClickListener { startFlow() }
        binding.btnOpenSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
    }

    // ==================== PERMISSION + LOCATION ====================

    private fun hasLocationPermission(): Boolean =
        isGranted(Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(Manifest.permission.ACCESS_COARSE_LOCATION)

    private fun isGranted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /**
     * Whether the system will still show its permission prompt. False after the user has
     * refused "for good" — then only the Settings screen can grant it.
     */
    private fun canAskForPermission(): Boolean =
        shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) ||
            shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION)

    /** Permission check, then location, then (via the ViewModel) the stations request. */
    private fun startFlow() {
        viewModel.onLocationRequested()
        if (hasLocationPermission()) {
            fetchLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    /**
     * Gets a fresh location (falling back to the last known one) and hands it to the ViewModel.
     * Only called once permission is held. The callbacks talk to the ViewModel only, so a result
     * that arrives during a rotation is not lost.
     */
    @SuppressLint("MissingPermission") // hasLocationPermission() is checked by every caller
    private fun fetchLocation() {
        enableMyLocationLayer()

        val vm = viewModel
        val cancellation = CancellationTokenSource().also { locationCancellation = it }
        try {
            fusedClient
                .getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        vm.loadNearbyStations(location.latitude, location.longitude)
                    } else {
                        fetchLastKnownLocation(vm)
                    }
                }
                .addOnFailureListener { vm.onLocationUnavailable() }
        } catch (e: SecurityException) {
            vm.onLocationDenied()
        }
    }

    @SuppressLint("MissingPermission") // only reached from fetchLocation()
    private fun fetchLastKnownLocation(vm: NearbyStationsViewModel) {
        try {
            fusedClient.lastLocation
                .addOnSuccessListener { location ->
                    if (location != null) {
                        vm.loadNearbyStations(location.latitude, location.longitude)
                    } else {
                        vm.onLocationUnavailable()
                    }
                }
                .addOnFailureListener { vm.onLocationUnavailable() }
        } catch (e: SecurityException) {
            vm.onLocationDenied()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                NearbyState.Idle -> showLoading(R.string.msg_getting_location)

                NearbyState.LocationLoading -> showLoading(R.string.msg_getting_location)

                NearbyState.StationsLoading -> showLoading(R.string.msg_finding_stations)

                is NearbyState.Success -> {
                    stations = state.stations
                    plotStations()
                    // Nothing in range: keep the map (centred on the user) and say so.
                    showOverlay(if (state.stations.isEmpty()) binding.cardEmpty else null)
                }

                is NearbyState.LocationError ->
                    if (state.permissionDenied) showPermissionDenied(state.message)
                    else showError(state.message)

                is NearbyState.Error -> {
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) handleSessionExpired() else showError(state.message)
                }
            }
        }
    }

    // ==================== SCREEN STATES ====================

    private fun showLoading(@StringRes messageRes: Int) {
        binding.tvLoadingMessage.setText(messageRes)
        showOverlay(binding.cardLoading)
    }

    private fun showPermissionDenied(message: String) {
        binding.tvPermissionMessage.text = message
        // Grant Permission while the system will still prompt; Open Settings once it won't.
        val canAsk = canAskForPermission()
        binding.btnGrantPermission.visibility = if (canAsk) View.VISIBLE else View.GONE
        binding.btnOpenSettings.visibility = if (canAsk) View.GONE else View.VISIBLE
        showOverlay(binding.permissionContainer)
    }

    private fun showError(message: String) {
        binding.tvErrorMessage.text = message
        showOverlay(binding.errorContainer)
    }

    /** Shows exactly [visible] over the map; null shows only the map. */
    private fun showOverlay(visible: View?) {
        listOf(
            binding.cardLoading,
            binding.cardEmpty,
            binding.permissionContainer,
            binding.errorContainer
        ).forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    // ==================== MAP ====================

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        map.uiSettings.isZoomControlsEnabled = true
        map.setOnMarkerClickListener { marker ->
            (marker.tag as? NearbyStationResponse)?.let { showStationSheet(it) }
            true // handled: we show our own sheet instead of the default info window
        }
        enableMyLocationLayer()
        plotStations() // in case the stations arrived before the map was ready
    }

    /** Blue dot for the user, once both the map and the permission exist. */
    @SuppressLint("MissingPermission") // guarded by hasLocationPermission()
    private fun enableMyLocationLayer() {
        val map = googleMap ?: return
        if (hasLocationPermission()) {
            map.isMyLocationEnabled = true
        }
    }

    /** One marker per station (title = station name), then frame the camera. */
    private fun plotStations() {
        val map = googleMap ?: return
        val list = stations ?: return

        map.clear()
        list.forEach { station ->
            map.addMarker(
                MarkerOptions()
                    .position(LatLng(station.latitude, station.longitude))
                    .title(station.stationName)
            )?.tag = station
        }
        fitCamera(map, list)
    }

    /**
     * Frames every station plus the user's own position. With a single distinct point (the
     * user, when no stations came back, or one station on top of the user) it centres and
     * zooms on that point instead of building a zero-size bounds.
     */
    private fun fitCamera(map: GoogleMap, list: List<NearbyStationResponse>) {
        val points = buildList {
            list.forEach { add(LatLng(it.latitude, it.longitude)) }
            viewModel.searchLocation?.let { (lat, lng) -> add(LatLng(lat, lng)) }
        }.distinct()

        when {
            points.isEmpty() -> return
            points.size == 1 ->
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.first(), SINGLE_POINT_ZOOM))
            else -> {
                val mapView = binding.map
                // newLatLngBounds needs a laid-out map; try again after layout if it isn't yet.
                if (mapView.width == 0 || mapView.height == 0) {
                    mapView.post { fitCamera(map, list) }
                    return
                }
                val bounds = LatLngBounds.Builder().apply { points.forEach { include(it) } }.build()
                map.moveCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds, mapView.width, mapView.height, BOUNDS_PADDING_PX
                    )
                )
            }
        }
    }

    // ==================== STATION SHEET ====================

    private fun showStationSheet(station: NearbyStationResponse) {
        val sheet = DialogStationInfoBinding.inflate(layoutInflater)
        sheet.tvStationName.text = station.stationName
        sheet.tvDistance.text = getString(R.string.value_distance_km, station.distanceKm)
        sheet.tvSchedule.text = station.schedule
        sheet.tvCapacity.text = getString(R.string.value_capacity_kw, station.capacityKw)
        // availableSlotCount is the live, 7-day figure; availableSlots is the station record's.
        sheet.tvAvailableSlots.text = station.availableSlotCount.toString()

        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheet.root)

        sheet.btnViewSlots.setOnClickListener {
            dialog.dismiss()
            // Straight to the slot picker for this station, skipping the station list.
            startActivity(SlotPickerActivity.newIntent(this, station.id, station.stationName))
        }
        dialog.show()
    }

    // ==================== CONSTANTS ====================

    private companion object {
        const val SINGLE_POINT_ZOOM = 14f
        const val BOUNDS_PADDING_PX = 120
    }
}
