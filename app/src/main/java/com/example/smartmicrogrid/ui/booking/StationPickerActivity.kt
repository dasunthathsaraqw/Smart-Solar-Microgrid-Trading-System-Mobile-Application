package com.example.smartmicrogrid.ui.booking

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.databinding.ActivityStationPickerBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.ui.common.showIfCached
import com.example.smartmicrogrid.viewmodel.StationListState
import com.example.smartmicrogrid.viewmodel.StationPickerViewModel

/**
 * File: StationPickerActivity.kt
 * Purpose: Step 1 of Create Booking. Lists stations from GET /api/stations; tapping one opens
 *          the slot picker for it.
 * Author: Mobile Team
 * Date: 2026
 */
class StationPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStationPickerBinding
    private val viewModel by viewModels<StationPickerViewModel>()
    private val adapter = StationAdapter { station -> openSlotPicker(station) }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        // Booking step 1: set up the toolbar, the station list and Retry, then load the stations
        // from GET /api/stations.
        super.onCreate(savedInstanceState)
        binding = ActivityStationPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvStations.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadStations() }

        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadStations()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        // Show one view per StationListState; the offline banner appears only when the list came
        // from the Room cache instead of the server.
        viewModel.state.observe(this) { state ->
            // Shown only for cached data; every other state (fresh, loading, error) clears it.
            binding.cachedBanner.showIfCached((state as? StationListState.Success)?.lastSyncedAt)

            when (state) {
                StationListState.Loading -> showOnly(binding.progressBar)

                is StationListState.Success -> {
                    adapter.submitList(state.stations)
                    showOnly(if (state.stations.isEmpty()) binding.tvEmpty else binding.rvStations)
                }

                is StationListState.Error -> {
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        binding.tvErrorMessage.text = state.message
                        showOnly(binding.errorContainer)
                    }
                }
            }
        }
    }

    // ==================== HELPERS ====================

    /** Shows exactly one of: list, spinner, empty text, error block. */
    private fun showOnly(visible: View) {
        // Switching all four views in one place keeps them mutually exclusive, whatever state came
        // before.
        listOf(binding.rvStations, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    private fun openSlotPicker(station: StationResponse) {
        // Booking step 2. The station name travels with the id so the slot picker can show it as
        // its subtitle without another request.
        startActivity(SlotPickerActivity.newIntent(this, station.id, station.stationName))
    }
}
