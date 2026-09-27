package com.example.smartmicrogrid.ui.common

import android.content.res.ColorStateList
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.utils.Constants
import com.google.android.material.chip.Chip

/**
 * File: StatusChip.kt
 * Purpose: Styles a Chip for a reservation status: the backend's status text, outlined and
 *          tinted in that status's color (with dark-mode variants from values-night).
 * Author: Mobile Team
 * Date: 2026
 */

// ==================== STATUS CHIP ====================

/** Shows [status] on this chip, colored per status. Unknown statuses fall back to gray. */
fun Chip.applyReservationStatus(status: String) {
    val color = ContextCompat.getColor(context, reservationStatusColor(status))
    text = status
    setTextColor(color)
    chipStrokeColor = ColorStateList.valueOf(color)
}

@ColorRes
private fun reservationStatusColor(status: String): Int = when (status) {
    Constants.STATUS_PENDING -> R.color.status_pending
    Constants.STATUS_APPROVED -> R.color.status_approved
    Constants.STATUS_COMPLETED -> R.color.status_completed
    Constants.STATUS_CANCELLED -> R.color.status_cancelled
    else -> R.color.gray
}
