package com.example.smartmicrogrid.ui.operator

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.databinding.ActivityPendingApprovalsBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.viewmodel.PendingApprovalsViewModel
import com.example.smartmicrogrid.viewmodel.PendingListState

/**
 * File: PendingApprovalsActivity.kt
 * Purpose: READ-ONLY queue of reservations awaiting approval at the operator's station, from
 *          GET /api/reports/pending-approvals. There is no approve action on mobile (that is
 *          done in the web app); tapping a row only opens an info sheet with what the list
 *          already holds.
 * Author: Mobile Team
 * Date: 2026
 */
class PendingApprovalsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPendingApprovalsBinding
    private val viewModel by viewModels<PendingApprovalsViewModel>()
    private val adapter = OperatorReservationAdapter(showCompletedAt = false) { reservation ->
        showReservationInfoSheet(reservation)
    }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPendingApprovalsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvPending.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadPending() }

        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadPending()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                PendingListState.Loading -> showOnly(binding.progressBar)

                is PendingListState.Success -> {
                    adapter.submitList(state.reservations)
                    showOnly(if (state.reservations.isEmpty()) binding.tvEmpty else binding.rvPending)
                }

                is PendingListState.Error -> {
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
        listOf(binding.rvPending, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }
}
