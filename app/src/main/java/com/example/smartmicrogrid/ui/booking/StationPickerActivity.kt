package com.example.smartmicrogrid.ui.booking

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.databinding.ActivityStationPickerBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
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
        viewModel.state.observe(this) { state ->
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
        listOf(binding.rvStations, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    private fun openSlotPicker(station: StationResponse) {
        startActivity(SlotPickerActivity.newIntent(this, station.id, station.stationName))
    }
}
