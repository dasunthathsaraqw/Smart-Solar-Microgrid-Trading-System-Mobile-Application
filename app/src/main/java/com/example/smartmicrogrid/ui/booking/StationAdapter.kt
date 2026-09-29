package com.example.smartmicrogrid.ui.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.databinding.ItemStationBinding

/**
 * File: StationAdapter.kt
 * Purpose: RecyclerView adapter for the station picker. Binds item_station rows and reports
 *          which station was chosen (whole card or its Select button).
 * Author: Mobile Team
 * Date: 2026
 */
class StationAdapter(
    private val onStationSelected: (StationResponse) -> Unit
) : ListAdapter<StationResponse, StationAdapter.StationViewHolder>(StationDiff) {

    class StationViewHolder(val binding: ItemStationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): StationViewHolder =
        // Inflate one item_station card through view binding; RecyclerView reuses it for other rows
        // as the list scrolls.
        StationViewHolder(
            ItemStationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: StationViewHolder, position: Int) {
        // Fill a recycled card with this row's station. Every field is set on every bind, so
        // nothing leaks in from the station the card showed before; the card and its Select button
        // report the same station.
        val station = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvStationName.text = station.stationName
            tvSchedule.text = "${context.getString(R.string.label_schedule)}: ${station.schedule}"
            tvCapacity.text = "${context.getString(R.string.label_capacity)}: " +
                context.getString(R.string.value_capacity_kw, station.capacityKw)
            tvAvailableSlots.text =
                "${context.getString(R.string.label_available_slots)}: ${station.availableSlots}"

            val select = View.OnClickListener { onStationSelected(station) }
            root.setOnClickListener(select)
            btnSelect.setOnClickListener(select)
        }
    }
}

// ==================== DIFF ====================

private object StationDiff : DiffUtil.ItemCallback<StationResponse>() {
    // Same station when the server id matches, even if its details have changed.
    override fun areItemsTheSame(old: StationResponse, new: StationResponse) = old.id == new.id
    // Data-class equality: the row is rebound only when a field it shows has changed.
    override fun areContentsTheSame(old: StationResponse, new: StationResponse) = old == new
}
