package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.databinding.ActivityBookingSummaryBinding
import com.example.smartmicrogrid.ui.common.applyReservationStatus
import com.example.smartmicrogrid.ui.prosumer.ProsumerHomeActivity
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.DateUtils
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException

/**
 * File: BookingSummaryActivity.kt
 * Purpose: REUSABLE result screen for any ReservationActionResponse. Create Booking opens it
 *          now; the update and cancel flows in the next round route through the same screen
 *          via newIntent(). Done returns to the dashboard.
 * Author: Mobile Team
 * Date: 2026
 */
class BookingSummaryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBookingSummaryBinding

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nothing to summarise (missing or unreadable extra) — bail out.
        val response = readResponse()
        if (response == null) {
            finish()
            return
        }

        binding = ActivityBookingSummaryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        bindResponse(response)

        binding.btnDone.setOnClickListener { goToDashboard() }
        binding.btnViewMyBookings.setOnClickListener {
            // My Bookings doesn't exist yet.
            Toast.makeText(this, R.string.msg_coming_soon, Toast.LENGTH_SHORT).show()
        }
        // The flow is over: Back behaves like Done instead of returning into a finished step.
        onBackPressedDispatcher.addCallback(this) { goToDashboard() }
    }

    // ==================== BINDING ====================

    private fun bindResponse(response: ReservationActionResponse) {
        val reservation = response.reservation

        binding.tvActionTitle.text = actionTitle(response.action)
        binding.tvMessage.text = response.message

        binding.tvStationName.text = reservation.stationName
        binding.tvSlotTime.text = DateUtils.formatSlotRange(
            this, reservation.slotStartTime, reservation.slotEndTime
        )
        binding.tvCapacity.text = getString(R.string.value_capacity_kw, reservation.capacityKw)
        binding.chipStatus.applyReservationStatus(reservation.status)

        binding.tvHoursUntilSlot.text = getString(R.string.value_hours, response.hoursUntilSlot)
        binding.tvCanModify.text = getString(
            if (response.canStillModify) R.string.label_can_still_modify_yes
            else R.string.label_can_still_modify_no
        )
        binding.tvCanModify.setTextColor(
            ContextCompat.getColor(
                this,
                if (response.canStillModify) R.color.success else R.color.error
            )
        )
    }

    /** Headline for the action taken; an action we don't recognise is shown as the raw text. */
    private fun actionTitle(action: String): String = when {
        action.equals(Constants.ACTION_CREATED, ignoreCase = true) ->
            getString(R.string.label_action_created)
        action.equals(Constants.ACTION_UPDATED, ignoreCase = true) ->
            getString(R.string.label_action_updated)
        action.equals(Constants.ACTION_CANCELLED, ignoreCase = true) ->
            getString(R.string.label_action_cancelled)
        else -> action
    }

    // ==================== NAVIGATION ====================

    /**
     * Back to the existing dashboard (not a fresh login). CLEAR_TOP without SINGLE_TOP finishes
     * the old ProsumerHomeActivity and starts a new one, so the dashboard reloads and its
     * counters reflect the action that was just taken.
     */
    private fun goToDashboard() {
        startActivity(
            Intent(this, ProsumerHomeActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        )
        finish()
    }

    // ==================== INTENT ====================

    /** Reads the response passed by [newIntent]; null if absent or not valid JSON. */
    private fun readResponse(): ReservationActionResponse? {
        val json = intent.getStringExtra(EXTRA_RESPONSE_JSON) ?: return null
        return try {
            Gson().fromJson(json, ReservationActionResponse::class.java)
        } catch (e: JsonSyntaxException) {
            null
        }
    }

    companion object {
        private const val EXTRA_RESPONSE_JSON = "extra_reservation_action_response_json"

        /**
         * Builds the Intent for any ReservationActionResponse (create now; update/cancel later).
         * The DTO isn't Parcelable, so it travels as a Gson JSON string — no Gradle plugin or
         * DTO changes needed, and the nested ReservationResponse comes along for free.
         */
        fun newIntent(context: Context, response: ReservationActionResponse): Intent =
            Intent(context, BookingSummaryActivity::class.java)
                .putExtra(EXTRA_RESPONSE_JSON, Gson().toJson(response))
    }
}
