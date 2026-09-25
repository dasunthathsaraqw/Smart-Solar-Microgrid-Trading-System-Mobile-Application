package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.databinding.ActivitySlotPickerBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.viewmodel.SlotListState
import com.example.smartmicrogrid.viewmodel.SlotPickerViewModel

/**
 * File: SlotPickerActivity.kt
 * Purpose: Step 2 of Create Booking. Lists the unbooked, future slots of the chosen station
 *          from GET /api/slots/station/{id}/available; tapping one opens the confirm screen.
 *          Also used in UPDATE MODE: when launched with a reservationId (from the booking
 *          detail screen) it lists slots for that booking's station, shows "Select a new
 *          slot", and passes the reservationId on so the confirm step moves the booking
 *          instead of creating one.
 * Author: Mobile Team
 * Date: 2026
 */
class SlotPickerActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlotPickerBinding
    private val viewModel by viewModels<SlotPickerViewModel>()
    private val adapter = SlotAdapter { slot -> openConfirm(slot) }

    private lateinit var stationId: String
    private lateinit var stationName: String

    /** Non-null = update mode: the reservation being moved. Null = normal create flow. */
    private var reservationId: String? = null

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
        reservationId = intent.getStringExtra(EXTRA_RESERVATION_ID)

        binding = ActivitySlotPickerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.subtitle = stationName
        if (reservationId != null) {
            binding.toolbar.setTitle(R.string.title_select_new_slot)
        }
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
        startActivity(
            ConfirmBookingActivity.newIntent(this, stationId, stationName, slot, reservationId)
        )
    }

    // ==================== INTENT ====================

    companion object {
        private const val EXTRA_STATION_ID = "extra_station_id"
        private const val EXTRA_STATION_NAME = "extra_station_name"
        private const val EXTRA_RESERVATION_ID = "extra_reservation_id"

        /** Pass [reservationId] to open in update mode (moving that reservation to a new slot). */
        fun newIntent(
            context: Context,
            stationId: String,
            stationName: String,
            reservationId: String? = null
        ): Intent = Intent(context, SlotPickerActivity::class.java)
            .putExtra(EXTRA_STATION_ID, stationId)
            .putExtra(EXTRA_STATION_NAME, stationName)
            .putExtra(EXTRA_RESERVATION_ID, reservationId)
    }
}
