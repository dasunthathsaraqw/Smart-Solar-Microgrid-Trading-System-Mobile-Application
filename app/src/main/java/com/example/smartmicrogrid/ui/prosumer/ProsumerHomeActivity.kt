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
import com.example.smartmicrogrid.ui.common.attachProsumerBottomNav
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ActivityProsumerHomeBinding
import com.example.smartmicrogrid.ui.auth.LoginActivity
import com.example.smartmicrogrid.ui.booking.MyBookingsActivity
import com.example.smartmicrogrid.ui.booking.StationPickerActivity
import com.example.smartmicrogrid.ui.common.showIfCached
import com.example.smartmicrogrid.ui.maps.NearbyStationsActivity
import com.example.smartmicrogrid.ui.profile.ProfileActivity
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
        // Show the header from the saved session straight away, then load the dashboard counters
        // from GET /api/reports/my-dashboard.
        super.onCreate(savedInstanceState)
        session = SessionManager(applicationContext)

        // Safety net: process restore can bring this screen back after the session ended.
        if (!session.isLoggedIn()) {
            goToLogin()
            return
        }

        binding = ActivityProsumerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)
        attachProsumerBottomNav(R.id.nav_home)

        showHeader()
        setupListeners()
        observeDashboardState()

        // Fetch only when the ViewModel has nothing yet. A rotation keeps the ViewModel (and
        // replays its last state); process death gives a fresh, empty one.
        if (viewModel.dashboardState.value == null) {
            viewModel.loadDashboard()
        }
    }

    override fun onResume() {
        // Refresh only the header on return; the counters reload when the booking flow relaunches
        // this screen.
        super.onResume()
        // The Profile screen updates the cached name/email in the session; re-read them so the
        // header isn't stale on return. (binding is unset if onCreate redirected to Login.)
        if (::binding.isInitialized) showHeader()
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        // The four buttons open the prosumer's feature screens; Logout clears the session (and the
        // offline cache) before returning to Login.
        binding.btnRetry.setOnClickListener { viewModel.loadDashboard() }

        binding.btnCreateBooking.setOnClickListener {
            startActivity(Intent(this, StationPickerActivity::class.java))
        }

        binding.btnMyBookings.setOnClickListener {
            startActivity(Intent(this, MyBookingsActivity::class.java))
        }

        binding.btnNearbyStations.setOnClickListener {
            startActivity(Intent(this, NearbyStationsActivity::class.java))
        }

        binding.btnProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            session.clear()
            goToLogin()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeDashboardState() {
        // Render the dashboard load: counters, an error with Retry, or back to Login on a 401.
        // Offline, the cached counters are shown with the banner.
        viewModel.dashboardState.observe(this) { state ->
            // Shown only for cached data; every other state (fresh, loading, error) clears it.
            binding.cachedBanner.showIfCached((state as? DashboardState.Success)?.lastSyncedAt)

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
        // Name, email and NIC come from the saved session, not a request; a NIC missing because the
        // profile fetch failed at login shows as not available.
        val notAvailable = getString(R.string.value_not_available)
        val email = viewModel.userEmail.ifEmpty { notAvailable }
        val nic = viewModel.userNic ?: notAvailable

        binding.tvGreeting.text = "${getString(R.string.greeting_prefix)} ${viewModel.userName}".trim()
        binding.tvEmail.text = "${getString(R.string.label_email)}: $email"
        binding.tvNic.text = "${getString(R.string.label_nic)}: $nic"
    }

    private fun bindDashboard(data: ProsumerDashboardResponse) {
        // Counts are shown exactly as the server computed them; the next-reservation card is
        // replaced by a note when there is none.
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
        // The earliest upcoming Approved reservation, as chosen by the server, with its status chip
        // in that status's color.
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
        // Unknown statuses fall back to gray instead of failing.
        Constants.STATUS_PENDING -> R.color.status_pending
        Constants.STATUS_APPROVED -> R.color.status_approved
        Constants.STATUS_COMPLETED -> R.color.status_completed
        Constants.STATUS_CANCELLED -> R.color.status_cancelled
        else -> R.color.gray
    }

    // ==================== SCREEN STATES ====================

    private fun showLoading() {
        // Spinner only; content and error stay hidden until the request finishes.
        binding.progressBar.visibility = View.VISIBLE
        binding.contentScroll.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showContent() {
        // Dashboard loaded (fresh or cached): hide the spinner and any earlier error.
        binding.progressBar.visibility = View.GONE
        binding.contentScroll.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showError() {
        // Replace the whole content area (counters and navigation buttons) with the error message
        // and Retry.
        binding.progressBar.visibility = View.GONE
        binding.contentScroll.visibility = View.GONE
        binding.errorContainer.visibility = View.VISIBLE
    }

    // ==================== NAVIGATION ====================

    private fun goToLogin() {
        // Clear the task, so Back from Login can't return to a screen whose session has ended.
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
