package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ActivityBookingDetailBinding
import com.example.smartmicrogrid.databinding.DialogCancelReasonBinding
import com.example.smartmicrogrid.ui.common.applyReservationStatus
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.viewmodel.BookingActionState
import com.example.smartmicrogrid.viewmodel.BookingDetailState
import com.example.smartmicrogrid.viewmodel.BookingDetailViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * File: BookingDetailActivity.kt
 * Purpose: One reservation in full, with the actions that apply to its status:
 *          - Update Slot   — Pending only (opens the slot picker in update mode)
 *          - Cancel Booking — Pending or Approved (asks for an optional reason first)
 *          - Show QR       — Approved only
 *          Update and cancel results go to the shared BookingSummaryActivity.
 * Author: Mobile Team
 * Date: 2026
 */
class BookingDetailActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingDetailBinding
    private val viewModel by viewModels<BookingDetailViewModel>()

    private lateinit var reservationId: String

    /** The loaded reservation, kept for the buttons' Intents. Null until the load succeeds. */
    private var reservation: ReservationResponse? = null

    // The one spinner serves both the detail load and update/cancel requests, so its visibility
    // is derived from both flags. Starts "loading" to match the layout default.
    private var detailLoading = true
    private var actionLoading = false

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without an id there is nothing to show — bail out rather than call the API.
        val id = intent.getStringExtra(EXTRA_RESERVATION_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        reservationId = id

        binding = ActivityBookingDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeDetailState()
        observeActionState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadDetail(reservationId)
        }
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.loadDetail(reservationId) }

        binding.btnUpdateSlot.setOnClickListener {
            reservation?.let {
                // Same station as the booking; the slot picker runs in update mode.
                startActivity(
                    SlotPickerActivity.newIntent(this, it.stationId, it.stationName, reservationId)
                )
            }
        }

        binding.btnCancelBooking.setOnClickListener { showCancelDialog() }

        binding.btnShowQr.setOnClickListener {
            reservation?.let {
                startActivity(
                    QrDisplayActivity.newIntent(
                        this, reservationId, it.stationName, it.slotStartTime, it.slotEndTime
                    )
                )
            }
        }
    }

    private fun showCancelDialog() {
        val dialogBinding = DialogCancelReasonBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnDismiss.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnConfirmCancel.setOnClickListener {
            val reason = dialogBinding.etReason.text?.toString()
            dialog.dismiss()
            viewModel.cancelBooking(reservationId, reason)
        }
        dialog.show()
    }

    // ==================== OBSERVERS ====================

    private fun observeDetailState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                BookingDetailState.Loading -> {
                    detailLoading = true
                    hideActions()
                    binding.contentScroll.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                }

                is BookingDetailState.Success -> {
                    detailLoading = false
                    reservation = state.reservation
                    bindReservation(state.reservation)
                    binding.contentScroll.visibility = View.VISIBLE
                    binding.errorContainer.visibility = View.GONE
                }

                is BookingDetailState.Error -> {
                    detailLoading = false
                    hideActions()
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        binding.tvErrorMessage.text = state.message
                        binding.contentScroll.visibility = View.GONE
                        binding.errorContainer.visibility = View.VISIBLE
                    }
                }
            }
            refreshProgress()
        }
    }

    private fun observeActionState() {
        viewModel.actionState.observe(this) { state ->
            when (state) {
                // Idle also follows a reset after Error — nothing to show or hide beyond this.
                BookingActionState.Idle -> setActionLoading(false)

                BookingActionState.Loading -> setActionLoading(true)

                is BookingActionState.Success -> {
                    setActionLoading(false)
                    // Reset first so a rotation doesn't replay this Success (a second summary).
                    viewModel.resetActionState()
                    startActivity(BookingSummaryActivity.newIntent(this, state.response))
                    finish()
                }

                is BookingActionState.Error -> {
                    setActionLoading(false)
                    viewModel.resetActionState()
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        // Server's own message, as-is (e.g. the 12-hour notice rule).
                        Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    // ==================== BINDING ====================

    private fun bindReservation(r: ReservationResponse) {
        binding.tvStationName.text = r.stationName
        binding.tvSlotTime.text = DateUtils.formatSlotRange(this, r.slotStartTime, r.slotEndTime)
        binding.tvCapacity.text = getString(R.string.value_capacity_kw, r.capacityKw)
        binding.chipStatus.applyReservationStatus(r.status)

        // Created is always shown; the rest only when the server has a value.
        bindTimestamp(binding.rowCreated, binding.tvCreatedAt, r.createdAt)
        bindTimestamp(binding.rowApproved, binding.tvApprovedAt, r.approvedAt)
        bindTimestamp(binding.rowCompleted, binding.tvCompletedAt, r.completedAt)
        bindTimestamp(binding.rowCancelled, binding.tvCancelledAt, r.cancelledAt)

        val reason = r.cancellationReason
        binding.rowCancellationReason.visibility =
            if (reason.isNullOrBlank()) View.GONE else View.VISIBLE
        binding.tvCancellationReason.text = reason.orEmpty()

        updateActions(r.status)
    }

    private fun bindTimestamp(row: View, value: TextView, iso: String?) {
        row.visibility = if (iso.isNullOrBlank()) View.GONE else View.VISIBLE
        value.text = DateUtils.formatForDisplay(iso)
    }

    // ==================== ACTIONS ====================

    /**
     * Which buttons apply to [status] (mirrors the server's rules, which stay authoritative —
     * a late attempt is still rejected with its own message):
     * - Update Slot:    Pending only
     * - Cancel Booking: Pending or Approved
     * - Show QR:        Approved only
     * Any other or unrecognised status (Completed, Cancelled, something new) shows no buttons.
     * Status text is case-sensitive, so a differently-cased value also shows none.
     */
    private fun updateActions(status: String) {
        val pending = status == Constants.STATUS_PENDING
        val approved = status == Constants.STATUS_APPROVED

        binding.btnUpdateSlot.visibility = if (pending) View.VISIBLE else View.GONE
        binding.btnCancelBooking.visibility = if (pending || approved) View.VISIBLE else View.GONE
        binding.btnShowQr.visibility = if (approved) View.VISIBLE else View.GONE
    }

    private fun hideActions() {
        binding.btnUpdateSlot.visibility = View.GONE
        binding.btnCancelBooking.visibility = View.GONE
        binding.btnShowQr.visibility = View.GONE
    }

    private fun setActionLoading(loading: Boolean) {
        actionLoading = loading
        val enabled = !loading
        binding.btnUpdateSlot.isEnabled = enabled
        binding.btnCancelBooking.isEnabled = enabled
        binding.btnShowQr.isEnabled = enabled
        refreshProgress()
    }

    private fun refreshProgress() {
        binding.progressBar.visibility =
            if (detailLoading || actionLoading) View.VISIBLE else View.GONE
    }

    // ==================== INTENT ====================

    companion object {
        private const val EXTRA_RESERVATION_ID = "extra_reservation_id"

        fun newIntent(context: Context, reservationId: String): Intent =
            Intent(context, BookingDetailActivity::class.java)
                .putExtra(EXTRA_RESERVATION_ID, reservationId)
    }
}
