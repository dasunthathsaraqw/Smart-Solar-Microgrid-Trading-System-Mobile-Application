package com.example.smartmicrogrid.ui.operator

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.DialogReservationInfoBinding
import com.example.smartmicrogrid.databinding.ItemOperatorReservationBinding
import com.example.smartmicrogrid.ui.common.applyReservationStatus
import com.example.smartmicrogrid.utils.DateUtils
import com.google.android.material.bottomsheet.BottomSheetDialog

/**
 * File: OperatorReservationUi.kt
 * Purpose: Shared UI bits for the operator's reservation screens: filling one
 *          item_operator_reservation row, and the read-only info sheet shown when a row is
 *          tapped. Used by the dashboard preview, the pending queue and the completed history,
 *          so the three stay identical.
 * Author: Mobile Team
 * Date: 2026
 */

// ==================== ROW ====================

/**
 * Fills this row from [reservation]: who booked (name + NIC), the slot, and its status chip.
 * With [showCompletedAt] the completion time is shown too (history), when there is one.
 * [onClick] receives the reservation when the row is tapped.
 */
fun ItemOperatorReservationBinding.bindReservation(
    reservation: ReservationResponse,
    showCompletedAt: Boolean,
    onClick: (ReservationResponse) -> Unit
) {
    val context = root.context

    tvProsumerName.text = reservation.prosumerName
    tvProsumerNic.text = "${context.getString(R.string.label_nic)}: ${reservation.prosumerNic}"
    tvSlotTime.text =
        DateUtils.formatSlotRange(context, reservation.slotStartTime, reservation.slotEndTime)
    chipStatus.applyReservationStatus(reservation.status)

    // Set both ways: rows are recycled, so a previous row's visibility must not leak through.
    val completedAt = reservation.completedAt
    if (showCompletedAt && !completedAt.isNullOrBlank()) {
        tvCompletedAt.text = "${context.getString(R.string.label_completed_at)}: " +
            DateUtils.formatForDisplay(completedAt)
        tvCompletedAt.visibility = View.VISIBLE
    } else {
        tvCompletedAt.visibility = View.GONE
    }

    root.setOnClickListener { onClick(reservation) }
}

// ==================== INFO SHEET ====================

/**
 * Shows a READ-ONLY bottom sheet for [reservation]. Everything comes from the row already in
 * hand — no request — and there are no buttons: operators can't act on reservations from mobile.
 */
fun Context.showReservationInfoSheet(reservation: ReservationResponse) {
    val sheet = DialogReservationInfoBinding.inflate(LayoutInflater.from(this))

    sheet.tvProsumerName.text = reservation.prosumerName
    sheet.tvNic.text = reservation.prosumerNic
    sheet.tvSlotTime.text =
        DateUtils.formatSlotRange(this, reservation.slotStartTime, reservation.slotEndTime)
    sheet.tvCapacity.text = getString(R.string.value_capacity_kw, reservation.capacityKw)
    sheet.chipStatus.applyReservationStatus(reservation.status)
    sheet.tvCreatedAt.text = DateUtils.formatForDisplay(reservation.createdAt)

    val completedAt = reservation.completedAt
    if (completedAt.isNullOrBlank()) {
        sheet.rowCompleted.visibility = View.GONE
    } else {
        sheet.tvCompletedAt.text = DateUtils.formatForDisplay(completedAt)
        sheet.rowCompleted.visibility = View.VISIBLE
    }

    BottomSheetDialog(this).apply {
        setContentView(sheet.root)
        show()
    }
}
