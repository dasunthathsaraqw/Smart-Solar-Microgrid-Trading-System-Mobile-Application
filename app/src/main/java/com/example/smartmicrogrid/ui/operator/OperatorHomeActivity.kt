package com.example.smartmicrogrid.ui.operator

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.OperatorDashboardResponse
import com.example.smartmicrogrid.databinding.ActivityOperatorHomeBinding
import com.example.smartmicrogrid.databinding.ItemOperatorReservationBinding
import com.example.smartmicrogrid.ui.auth.LoginActivity
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.SessionManager
import com.example.smartmicrogrid.viewmodel.OperatorDashboardState
import com.example.smartmicrogrid.viewmodel.OperatorDashboardViewModel

/**
 * File: OperatorHomeActivity.kt
 * Purpose: Grid operator dashboard. Shows the header, today's activity counters and a preview of
 *          the upcoming approved reservations, loaded via OperatorDashboardViewModel, and
 *          navigates to the read-only Pending Approvals and Completed History screens, the QR
 *          scanner (check-in), and slot management.
 * Author: Mobile Team
 * Date: 2026
 */
class OperatorHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOperatorHomeBinding
    private val viewModel by viewModels<OperatorDashboardViewModel>()
    private lateinit var session: SessionManager

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        // Operator dashboard. Show the header from the saved session straight away, then load
        // today's counters from GET /api/reports/operator-dashboard, which the server scopes to the
        // operator's station.
        super.onCreate(savedInstanceState)
        session = SessionManager(applicationContext)

        // Safety net: process restore can bring this screen back after the session ended.
        if (!session.isLoggedIn()) {
            goToLogin()
            return
        }

        binding = ActivityOperatorHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showHeader()
        setupListeners()
        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadDashboard()
        }
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        // Four buttons open the operator's screens; Logout clears the session before returning to
        // Login.
        binding.btnRetry.setOnClickListener { viewModel.loadDashboard() }

        binding.btnPendingApprovals.setOnClickListener {
            startActivity(Intent(this, PendingApprovalsActivity::class.java))
        }
        binding.btnCompletedHistory.setOnClickListener {
            startActivity(Intent(this, CompletedHistoryActivity::class.java))
        }
        binding.btnScanQr.setOnClickListener {
            startActivity(Intent(this, QrScannerActivity::class.java))
        }
        binding.btnManageSlots.setOnClickListener {
            startActivity(Intent(this, SlotManagementActivity::class.java))
        }

        binding.btnLogout.setOnClickListener {
            session.clear()
            goToLogin()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        // Render the dashboard load: counters and preview, an error with Retry, or session expiry
        // on a 401. Operator data is never cached, so there is no offline banner here.
        viewModel.state.observe(this) { state ->
            when (state) {
                OperatorDashboardState.Loading -> showLoading()

                is OperatorDashboardState.Success -> {
                    bindDashboard(state.data)
                    showContent()
                }

                is OperatorDashboardState.Error -> {
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
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
        // Name, email and station id come from the login response saved in the session; an operator
        // with no station assigned sees it as not available.
        val notAvailable = getString(R.string.value_not_available)
        val email = viewModel.userEmail.ifEmpty { notAvailable }
        val stationId = viewModel.stationId ?: notAvailable

        binding.tvGreeting.text = "${getString(R.string.greeting_prefix)} ${viewModel.userName}".trim()
        binding.tvEmail.text = "${getString(R.string.label_email)}: $email"
        binding.tvStationId.text = "${getString(R.string.label_station_id)}: $stationId"
    }

    private fun bindDashboard(data: OperatorDashboardResponse) {
        // Today's counts exactly as the server computed them, then the upcoming-approved preview.
        binding.tvPendingTodayCount.text = data.pendingToday.toString()
        binding.tvApprovedTodayCount.text = data.approvedToday.toString()
        binding.tvCompletedTodayCount.text = data.completedToday.toString()
        binding.tvApprovedFutureCount.text = data.approvedFutureCount.toString()

        bindUpcoming(data)
    }

    /** The first few upcoming approved reservations as rows, or the empty text. */
    private fun bindUpcoming(data: OperatorDashboardResponse) {
        // Rows are added straight into a LinearLayout rather than a RecyclerView, because at most
        // five are shown.
        val container = binding.upcomingContainer
        container.removeAllViews() // rebuilt from scratch, so a replayed state can't duplicate rows

        val preview = data.upcomingApproved.take(UPCOMING_PREVIEW_COUNT)
        preview.forEach { reservation ->
            val row = ItemOperatorReservationBinding.inflate(layoutInflater, container, false)
            row.bindReservation(reservation, showCompletedAt = false) { showReservationInfoSheet(it) }
            container.addView(row.root)
        }

        binding.tvNoUpcoming.visibility = if (preview.isEmpty()) View.VISIBLE else View.GONE
    }

    // ==================== SCREEN STATES ====================

    private fun showLoading() {
        // Spinner only; content and error stay hidden until the request finishes.
        binding.progressBar.visibility = View.VISIBLE
        binding.contentScroll.visibility = View.GONE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showContent() {
        // Dashboard loaded: hide the spinner and any earlier error.
        binding.progressBar.visibility = View.GONE
        binding.contentScroll.visibility = View.VISIBLE
        binding.errorContainer.visibility = View.GONE
    }

    private fun showError() {
        // Replace the whole content area (counters, preview and navigation buttons) with the error
        // message and Retry.
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

    // ==================== CONSTANTS ====================

    private companion object {
        /** The server sends up to 10; the dashboard only previews the first few. */
        const val UPCOMING_PREVIEW_COUNT = 5
    }
}
