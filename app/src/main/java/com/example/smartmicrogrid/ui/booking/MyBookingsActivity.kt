package com.example.smartmicrogrid.ui.booking

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.databinding.ActivityMyBookingsBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.viewmodel.BookingListState
import com.example.smartmicrogrid.viewmodel.MyBookingsViewModel
import com.google.android.material.tabs.TabLayout

/**
 * File: MyBookingsActivity.kt
 * Purpose: Lists the signed-in prosumer's reservations, one tab per status plus All, from
 *          GET /api/reservations/my?status=. Tapping a row opens its detail screen.
 * Author: Mobile Team
 * Date: 2026
 */
class MyBookingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMyBookingsBinding
    private val viewModel by viewModels<MyBookingsViewModel>()
    private val adapter = BookingAdapter { booking ->
        startActivity(BookingDetailActivity.newIntent(this, booking.id))
    }

    /**
     * Status filter per tab, in the same order as the TabItems in activity_my_bookings.xml.
     * null = All (no filter).
     */
    private val tabStatuses: List<String?> = listOf(
        Constants.STATUS_PENDING,
        Constants.STATUS_APPROVED,
        Constants.STATUS_COMPLETED,
        Constants.STATUS_CANCELLED,
        null
    )

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyBookingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvBookings.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.reload() }

        setupTabs()
        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadBookings(viewModel.selectedStatus)
        }
    }

    // ==================== TABS ====================

    private fun setupTabs() {
        // Re-select the remembered tab BEFORE attaching the listener, so restoring it after a
        // rotation doesn't trigger a second load.
        val selectedIndex = tabStatuses.indexOf(viewModel.selectedStatus).coerceAtLeast(0)
        binding.tabLayout.getTabAt(selectedIndex)?.select()

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                viewModel.loadBookings(tabStatuses[tab.position])
            }

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            // Tapping the current tab again refreshes it.
            override fun onTabReselected(tab: TabLayout.Tab) {
                viewModel.reload()
            }
        })
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                BookingListState.Loading -> showOnly(binding.progressBar)

                is BookingListState.Success -> {
                    adapter.submitList(state.bookings)
                    showOnly(if (state.bookings.isEmpty()) binding.tvEmpty else binding.rvBookings)
                }

                is BookingListState.Error -> {
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
        listOf(binding.rvBookings, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }
}
