package com.example.smartmicrogrid.ui.operator

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ItemOperatorReservationBinding

/**
 * File: OperatorReservationAdapter.kt
 * Purpose: RecyclerView adapter for the operator's reservation lists (pending queue and
 *          completed history). Binds item_operator_reservation rows and reports which
 *          reservation was tapped. Each row is filled by bindReservation().
 * Author: Mobile Team
 * Date: 2026
 */
class OperatorReservationAdapter(
    private val showCompletedAt: Boolean,
    private val onReservationSelected: (ReservationResponse) -> Unit
) : ListAdapter<ReservationResponse, OperatorReservationAdapter.ReservationViewHolder>(ReservationDiff) {

    class ReservationViewHolder(val binding: ItemOperatorReservationBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReservationViewHolder =
        // Inflate one item_operator_reservation row through view binding; RecyclerView reuses it as
        // the list scrolls.
        ReservationViewHolder(
            ItemOperatorReservationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        // Filled by the shared bindReservation(), so these rows match the dashboard preview
        // exactly; showCompletedAt adds the completion time on the history list.
        holder.binding.bindReservation(getItem(position), showCompletedAt, onReservationSelected)
    }
}

// ==================== DIFF ====================

private object ReservationDiff : DiffUtil.ItemCallback<ReservationResponse>() {
    override fun areItemsTheSame(old: ReservationResponse, new: ReservationResponse) =
        // Same reservation when the server id matches.
        old.id == new.id

    override fun areContentsTheSame(old: ReservationResponse, new: ReservationResponse) =
        // Data-class equality: rebind only when a field shown on the row changed.
        old == new
}
