package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.databinding.ActivityConfirmBookingBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.viewmodel.BookingActionState
import com.example.smartmicrogrid.viewmodel.CreateBookingViewModel

/**
 * File: ConfirmBookingActivity.kt
 * Purpose: Step 3 of Create Booking. Recaps the chosen station and slot and, on Confirm,
 *          submits POST /api/reservations/my. Success opens the summary screen; a server
 *          rejection (409 slot taken, 400 rule violation) is shown inline, as-is.
 * Author: Mobile Team
 * Date: 2026
 */
class ConfirmBookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmBookingBinding
    private val viewModel by viewModels<CreateBookingViewModel>()

    private lateinit var stationId: String
    private lateinit var slotId: String

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without both ids there is nothing to book — bail out rather than call the API.
        val stationIdExtra = intent.getStringExtra(EXTRA_STATION_ID)
        val slotIdExtra = intent.getStringExtra(EXTRA_SLOT_ID)
        if (stationIdExtra.isNullOrBlank() || slotIdExtra.isNullOrBlank()) {
            finish()
            return
        }
        stationId = stationIdExtra
        slotId = slotIdExtra

        binding = ActivityConfirmBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showSelection()
        setupListeners()
        observeState()
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { viewModel.confirmBooking(stationId, slotId) }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                // Idle also follows a reset after Error — keep the inline error on screen.
                BookingActionState.Idle -> setLoading(false)

                BookingActionState.Loading -> {
                    binding.tvError.visibility = View.GONE
                    setLoading(true)
                }

                is BookingActionState.Success -> {
                    setLoading(false)
                    // Reset first so a rotation doesn't replay this Success (a second summary).
                    viewModel.resetState()
                    startActivity(BookingSummaryActivity.newIntent(this, state.response))
                    finish()
                }

                is BookingActionState.Error -> {
                    setLoading(false)
                    viewModel.resetState()
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        // Server's own message, as-is (e.g. "Slot is already booked").
                        binding.tvError.text = state.message
                        binding.tvError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    // ==================== HELPERS ====================

    private fun showSelection() {
        binding.tvStationName.text = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
        binding.tvSlotTime.text = DateUtils.formatSlotRange(
            this,
            intent.getStringExtra(EXTRA_SLOT_START),
            intent.getStringExtra(EXTRA_SLOT_END)
        )
        binding.tvCapacity.text =
            getString(R.string.value_capacity_kw, intent.getDoubleExtra(EXTRA_CAPACITY_KW, 0.0))
    }

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnConfirm.isEnabled = !loading
        binding.btnCancel.isEnabled = !loading
    }

    // ==================== INTENT ====================

    companion object {
        private const val EXTRA_STATION_ID = "extra_station_id"
        private const val EXTRA_STATION_NAME = "extra_station_name"
        private const val EXTRA_SLOT_ID = "extra_slot_id"
        private const val EXTRA_SLOT_START = "extra_slot_start"
        private const val EXTRA_SLOT_END = "extra_slot_end"
        private const val EXTRA_CAPACITY_KW = "extra_capacity_kw"

        /** Slot times are passed as the backend's raw ISO strings and formatted on this screen. */
        fun newIntent(
            context: Context,
            stationId: String,
            stationName: String,
            slot: SlotResponse
        ): Intent = Intent(context, ConfirmBookingActivity::class.java)
            .putExtra(EXTRA_STATION_ID, stationId)
            .putExtra(EXTRA_STATION_NAME, stationName)
            .putExtra(EXTRA_SLOT_ID, slot.id)
            .putExtra(EXTRA_SLOT_START, slot.startTime)
            .putExtra(EXTRA_SLOT_END, slot.endTime)
            .putExtra(EXTRA_CAPACITY_KW, slot.capacityKw)
    }
}
