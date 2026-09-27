package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.LiveData
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.databinding.ActivityConfirmBookingBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.viewmodel.BookingActionState
import com.example.smartmicrogrid.viewmodel.BookingDetailViewModel
import com.example.smartmicrogrid.viewmodel.CreateBookingViewModel

/**
 * File: ConfirmBookingActivity.kt
 * Purpose: Final step of Create Booking. Recaps the chosen station and slot and, on Confirm,
 *          submits POST /api/reservations/my. Success opens the summary screen; a server
 *          rejection (409 slot taken, 400 rule violation) is shown inline, as-is.
 *          Also used in UPDATE MODE: when launched with a reservationId it says "Confirm new
 *          slot" and moves that reservation (PUT /api/reservations/my/{id}) through
 *          BookingDetailViewModel instead of creating one. Both paths end at the same summary.
 * Author: Mobile Team
 * Date: 2026
 */
class ConfirmBookingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityConfirmBookingBinding

    // Only the ViewModel for the current mode is ever touched (both are lazy).
    private val createViewModel by viewModels<CreateBookingViewModel>()
    private val updateViewModel by viewModels<BookingDetailViewModel>()

    private lateinit var stationId: String
    private lateinit var slotId: String

    /** Non-null = update mode: the reservation being moved. Null = normal create flow. */
    private var reservationId: String? = null

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
        reservationId = intent.getStringExtra(EXTRA_RESERVATION_ID)

        binding = ActivityConfirmBookingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (reservationId != null) {
            binding.toolbar.setTitle(R.string.title_confirm_new_slot)
            binding.btnConfirm.setText(R.string.action_confirm_update)
        }

        showSelection()
        setupListeners()
        observeState()
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnCancel.setOnClickListener { finish() }
        binding.btnConfirm.setOnClickListener { submit() }
    }

    // ==================== MODE ====================

    /** Create or move, depending on the mode. Both ViewModels ignore a double-tap. */
    private fun submit() {
        val id = reservationId
        if (id != null) {
            updateViewModel.updateSlot(id, slotId)
        } else {
            createViewModel.confirmBooking(stationId, slotId)
        }
    }

    private fun resetAction() {
        if (reservationId != null) updateViewModel.resetActionState() else createViewModel.resetState()
    }

    private fun actionState(): LiveData<BookingActionState> =
        if (reservationId != null) updateViewModel.actionState else createViewModel.state

    // ==================== OBSERVERS ====================

    private fun observeState() {
        actionState().observe(this) { state ->
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
                    resetAction()
                    startActivity(BookingSummaryActivity.newIntent(this, state.response))
                    finish()
                }

                is BookingActionState.Error -> {
                    setLoading(false)
                    resetAction()
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
        private const val EXTRA_RESERVATION_ID = "extra_reservation_id"

        /**
         * Slot times are passed as the backend's raw ISO strings and formatted on this screen.
         * Pass [reservationId] to open in update mode (moving that reservation to [slot]).
         */
        fun newIntent(
            context: Context,
            stationId: String,
            stationName: String,
            slot: SlotResponse,
            reservationId: String? = null
        ): Intent = Intent(context, ConfirmBookingActivity::class.java)
            .putExtra(EXTRA_STATION_ID, stationId)
            .putExtra(EXTRA_STATION_NAME, stationName)
            .putExtra(EXTRA_SLOT_ID, slot.id)
            .putExtra(EXTRA_SLOT_START, slot.startTime)
            .putExtra(EXTRA_SLOT_END, slot.endTime)
            .putExtra(EXTRA_CAPACITY_KW, slot.capacityKw)
            .putExtra(EXTRA_RESERVATION_ID, reservationId)
    }
}
