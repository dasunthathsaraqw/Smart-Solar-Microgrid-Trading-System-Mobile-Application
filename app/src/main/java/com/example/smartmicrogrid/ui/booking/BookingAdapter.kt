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
        BookingViewHolder(
            ItemBookingBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: BookingViewHolder, position: Int) {
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
        old.id == new.id

    override fun areContentsTheSame(old: ReservationResponse, new: ReservationResponse) =
        old == new
}
