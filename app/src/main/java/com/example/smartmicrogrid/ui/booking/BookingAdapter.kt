package com.example.smartmicrogrid.ui.booking

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ItemBookingBinding
import com.example.smartmicrogrid.ui.common.applyReservationStatus
import com.example.smartmicrogrid.utils.DateUtils

/**
 * File: BookingAdapter.kt
 * Purpose: RecyclerView adapter for the My Bookings list. Binds item_booking rows (station,
 *          slot time, status chip) and reports which reservation was tapped.
 * Author: Mobile Team
 * Date: 2026
 */
class BookingAdapter(
    private val onBookingSelected: (ReservationResponse) -> Unit
) : ListAdapter<ReservationResponse, BookingAdapter.BookingViewHolder>(BookingDiff) {

    class BookingViewHolder(val binding: ItemBookingBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BookingViewHolder =
        // Inflate one item_booking card through view binding; RecyclerView reuses it as the list
        // scrolls.
        BookingViewHolder(
            ItemBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
        // Show the station, the slot window in the device's time zone and the status chip. Tapping
        // the card hands the reservation to the Activity, which opens its detail screen.
        val booking = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvStationName.text = booking.stationName
            tvSlotTime.text =
                DateUtils.formatSlotRange(context, booking.slotStartTime, booking.slotEndTime)
            chipStatus.applyReservationStatus(booking.status)
            root.setOnClickListener { onBookingSelected(booking) }
        }
    }
}

// ==================== DIFF ====================

private object BookingDiff : DiffUtil.ItemCallback<ReservationResponse>() {
    override fun areItemsTheSame(old: ReservationResponse, new: ReservationResponse) =
        // Same booking when the server id matches, even if its status has changed since the last
        // load.
        old.id == new.id

    override fun areContentsTheSame(old: ReservationResponse, new: ReservationResponse) =
        // Data-class equality: a reservation whose status moved (e.g. Pending to Approved) is
        // rebound, so its chip updates.
        old == new
}
