package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.databinding.ActivitySlotPickerBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.viewmodel.SlotListState
import com.example.smartmicrogrid.viewmodel.SlotPickerViewModel

/**
 * File: SlotPickerActivity.kt
 * Purpose: Step 2 of Create Booking. Lists the unbooked, future slots of the chosen station
 *          from GET /api/slots/station/{id}/available; tapping one opens the confirm screen.
 * Author: Mobile Team
 * Date: 2026
 */
class SlotPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlotPickerBinding
    private val viewModel by viewModels<SlotPickerViewModel>()
    private val adapter = SlotAdapter { slot -> openConfirm(slot) }

    private lateinit var stationId: String
    private lateinit var stationName: String

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without a station there is nothing to list — bail out rather than call the API.
        val id = intent.getStringExtra(EXTRA_STATION_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        stationId = id
        stationName = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()

        binding = ActivitySlotPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.subtitle = stationName
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvSlots.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadSlots(stationId) }

        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadSlots(stationId)
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                SlotListState.Loading -> showOnly(binding.progressBar)

                is SlotListState.Success -> {
                    adapter.submitList(state.slots)
                    showOnly(if (state.slots.isEmpty()) binding.tvEmpty else binding.rvSlots)
                }

                is SlotListState.Error -> {
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
        listOf(binding.rvSlots, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    private fun openConfirm(slot: SlotResponse) {
        startActivity(ConfirmBookingActivity.newIntent(this, stationId, stationName, slot))
    }

    // ==================== INTENT ====================

    companion object {
        private const val EXTRA_STATION_ID = "extra_station_id"
        private const val EXTRA_STATION_NAME = "extra_station_name"

        fun newIntent(context: Context, stationId: String, stationName: String): Intent =
            Intent(context, SlotPickerActivity::class.java)
                .putExtra(EXTRA_STATION_ID, stationId)
                .putExtra(EXTRA_STATION_NAME, stationName)
    }
}
