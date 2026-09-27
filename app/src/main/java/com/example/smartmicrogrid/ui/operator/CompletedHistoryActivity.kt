package com.example.smartmicrogrid.ui.operator

import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ActivityCompletedHistoryBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.viewmodel.CompletedHistoryViewModel
import com.example.smartmicrogrid.viewmodel.HistoryState

/**
 * File: CompletedHistoryActivity.kt
 * Purpose: Paginated history of completed reservations at the operator's own station, from
 *          GET /api/reservations/operator/history (the station comes from the session, never a
 *          picker). Pages are appended by an explicit "Load more" button; a failed later page
 *          keeps the list and turns the button into Retry. Tapping a row opens a read-only sheet.
 * Author: Mobile Team
 * Date: 2026
 */
class CompletedHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCompletedHistoryBinding
    private val viewModel by viewModels<CompletedHistoryViewModel>()
    private val adapter = OperatorReservationAdapter(showCompletedAt = true) { reservation ->
        showReservationInfoSheet(reservation)
    }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCompletedHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvHistory.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadHistory() }       // first page failed
        binding.btnLoadMore.setOnClickListener { viewModel.loadNextPage() }   // more, or retry of it

        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadHistory()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                HistoryState.Loading -> {
                    showOnly(binding.progressBar)
                    hideLoadMoreBar()
                }

                is HistoryState.Success -> {
                    showList(state.data.items)
                    showLoadMoreBar(visible = state.data.hasNextPage)
                }

                // Keep the rows on screen (also right after a rotation) and swap in the spinner.
                is HistoryState.LoadingMore -> {
                    showList(state.current.items)
                    showLoadMoreBar(visible = true, loading = true)
                }

                is HistoryState.Error -> handleError(state)
            }
        }
    }

    private fun handleError(state: HistoryState.Error) {
        // 401 = token rejected/expired; retrying can't fix that.
        if (state.code == 401) {
            handleSessionExpired()
            return
        }

        val current = state.current
        if (current == null) {
            // The first page failed: nothing to show, so the full error screen.
            binding.tvErrorMessage.text = state.message
            showOnly(binding.errorContainer)
            hideLoadMoreBar()
        } else {
            // A later page failed: keep the list, show the message, and let the button retry.
            showList(current.items)
            showLoadMoreBar(visible = true, errorMessage = state.message)
        }
    }

    // ==================== HELPERS ====================

    private fun showList(items: List<ReservationResponse>) {
        adapter.submitList(items)
        showOnly(if (items.isEmpty()) binding.tvEmpty else binding.rvHistory)
    }

    /** Shows exactly one of: list, spinner, empty text, error block. */
    private fun showOnly(visible: View) {
        listOf(binding.rvHistory, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    /**
     * The bar under the list. [loading] swaps the button for the spinner (same slot, so the bar
     * keeps its height); [errorMessage] shows the failure above a button that now says Retry.
     */
    private fun showLoadMoreBar(
        visible: Boolean,
        loading: Boolean = false,
        errorMessage: String? = null
    ) {
        binding.loadMoreBar.visibility = if (visible) View.VISIBLE else View.GONE

        binding.btnLoadMore.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        binding.progressLoadMore.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnLoadMore.setText(
            if (errorMessage != null) R.string.action_retry else R.string.action_load_more
        )

        binding.tvLoadMoreError.text = errorMessage.orEmpty()
        binding.tvLoadMoreError.visibility = if (errorMessage != null) View.VISIBLE else View.GONE
    }

    private fun hideLoadMoreBar() = showLoadMoreBar(visible = false)
}
