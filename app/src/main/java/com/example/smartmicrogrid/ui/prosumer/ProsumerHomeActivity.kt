package com.example.smartmicrogrid.ui.prosumer

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ActivityProsumerHomeBinding
import com.example.smartmicrogrid.ui.auth.LoginActivity
import com.example.smartmicrogrid.ui.booking.MyBookingsActivity
import com.example.smartmicrogrid.ui.booking.StationPickerActivity
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.utils.SessionManager
import com.example.smartmicrogrid.viewmodel.DashboardState
import com.example.smartmicrogrid.viewmodel.DashboardViewModel

/**
 * File: ProsumerHomeActivity.kt
 * Purpose: Prosumer dashboard. Shows the signed-in user's header, the four reservation-status
 *          counters and the next approved reservation, loaded via DashboardViewModel. The
 *          navigation buttons are placeholders ("Coming soon") until those screens exist.
 * Author: Mobile Team
 * Date: 2026
 */
class ProsumerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProsumerHomeBinding
    private val viewModel by viewModels<DashboardViewModel>()
    private lateinit var session: SessionManager

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(applicationContext)

        // Safety net: process restore can bring this screen back after the session ended.
        if (!session.isLoggedIn()) {
            goToLogin()
            return
        }

        binding = ActivityProsumerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showHeader()
        setupListeners()
        observeDashboardState()

        // Fetch only when the ViewModel has nothing yet. A rotation keeps the ViewModel (and
        // replays its last state); process death gives a fresh, empty one.
        if (viewModel.dashboardState.value == null) {
            viewModel.loadDashboard()
        }
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.btnRetry.setOnClickListener { viewModel.loadDashboard() }

        binding.btnCreateBooking.setOnClickListener {
            startActivity(Intent(this, StationPickerActivity::class.java))
        }

        binding.btnMyBookings.setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }

        // These screens don't exist yet — no broken Intents, just a toast.
        listOf(
            binding.btnNearbyStations,
            binding.btnProfile
        ).forEach { button ->
            button.setOnClickListener {
                Toast.makeText(this, R.string.msg_coming_soon, Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnLogout.setOnClickListener {
            session.clear()
            goToLogin()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeDashboardState() {
        viewModel.dashboardState.observe(this) { state ->
            when (state) {
                DashboardState.Loading -> showLoading()

                is DashboardState.Success -> {
                    bindDashboard(state.data)
                    showContent()
                }

                is DashboardState.Error -> {
                    // 401 = token rejected/expired. Retrying can't fix that, so end the session.
                    if (state.code == 401) {
                        Toast.makeText(this, R.string.msg_session_expired, Toast.LENGTH_LONG).show()
                        session.clear()
                        goToLogin()
                    } else {
                        binding.tvErrorMessage.text = state.message
                        showError()
                    }
                }
            }
        }
    }

    // ==================== BINDING ====================

    private fun showHeader() {
        val notAvailable = getString(R.string.value_not_available)
        val email = viewModel.userEmail.ifEmpty { notAvailable }
        val nic = viewModel.userNic ?: notAvailable

        binding.tvGreeting.text = "${getString(R.string.greeting_prefix)} ${viewModel.userName}".trim()
        binding.tvEmail.text = "${getString(R.string.label_email)}: $email"
        binding.tvNic.text = "${getString(R.string.label_nic)}: $nic"
    }

    private fun bindDashboard(data: ProsumerDashboardResponse) {
        binding.tvPendingCount.text = data.pendingCount.toString()
        binding.tvApprovedCount.text = data.approvedFutureCount.toString()
        binding.tvCompletedCount.text = data.completedCount.toString()
        binding.tvCancelledCount.text = data.cancelledCount.toString()

        val next = data.nextReservation
        if (next == null) {
            binding.nextReservationDetails.visibility = View.GONE
            binding.tvNoUpcoming.visibility = View.VISIBLE
        } else {
            bindNextReservation(next)
            binding.nextReservationDetails.visibility = View.VISIBLE
            binding.tvNoUpcoming.visibility = View.GONE
        }
    }

    private fun bindNextReservation(reservation: ReservationResponse) {
        binding.tvStationName.text = reservation.stationName
        binding.tvSlotTime.text = getString(
            R.string.label_slot_range,
            DateUtils.formatForDisplay(reservation.slotStartTime),
            DateUtils.formatTimeForDisplay(reservation.slotEndTime)
        )

        // Chip shows the backend's status text, outlined and tinted in that status's color.
        val color = ContextCompat.getColor(this, statusColorRes(reservation.status))
        binding.chipStatus.text = reservation.status
        binding.chipStatus.setTextColor(color)
        binding.chipStatus.chipStrokeColor = ColorStateList.valueOf(color)
    }

    private fun statusColorRes(status: String): Int = when (status) {
        Constants.STATUS_PENDING -> R.color.status_pending
        Constants.STATUS_APPROVED -> R.color.status_approved
        Constants.STATUS_COMPLETED -> R.color.status_completed
        Constants.STATUS_CANCELLED -> R.color.status_cancelled
        else -> R.color.gray
    }

    // ==================== SCREEN STATES ====================

    private fun showLoading() {
        binding.progressBar.visibility = View.VISIBLE
        binding.contentScroll.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showContent() {
        binding.progressBar.visibility = View.GONE
        binding.contentScroll.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showError() {
        binding.progressBar.visibility = View.GONE
        binding.contentScroll.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
    }

    // ==================== NAVIGATION ====================

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
