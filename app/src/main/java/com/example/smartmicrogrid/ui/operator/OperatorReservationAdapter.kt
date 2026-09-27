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
        ReservationViewHolder(
            ItemOperatorReservationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: ReservationViewHolder, position: Int) {
        holder.binding.bindReservation(getItem(position), showCompletedAt, onReservationSelected)
    }
}

// ==================== DIFF ====================

private object ReservationDiff : DiffUtil.ItemCallback<ReservationResponse>() {
    override fun areItemsTheSame(old: ReservationResponse, new: ReservationResponse) =
        old.id == new.id

    override fun areContentsTheSame(old: ReservationResponse, new: ReservationResponse) =
        old == new
}
