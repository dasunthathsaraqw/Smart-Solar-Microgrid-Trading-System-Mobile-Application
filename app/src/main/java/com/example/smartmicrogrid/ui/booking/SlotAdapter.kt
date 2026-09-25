package com.example.smartmicrogrid.ui.booking

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.databinding.ItemSlotBinding
import com.example.smartmicrogrid.utils.DateUtils

/**
 * File: SlotAdapter.kt
 * Purpose: RecyclerView adapter for the slot picker. Binds item_slot rows and reports which
 *          slot was chosen (whole card or its Select button).
 * Author: Mobile Team
 * Date: 2026
 */
class SlotAdapter(
    /** Text of each row's button — "Select" for booking, "Edit" for the operator's slot list. */
    @StringRes private val actionLabelRes: Int = R.string.action_select,
    private val onSlotSelected: (SlotResponse) -> Unit
) : ListAdapter<SlotResponse, SlotAdapter.SlotViewHolder>(SlotDiff) {

    class SlotViewHolder(val binding: ItemSlotBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SlotViewHolder =
        SlotViewHolder(
            ItemSlotBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        )

    override fun onBindViewHolder(holder: SlotViewHolder, position: Int) {
        val slot = getItem(position)
        val context = holder.itemView.context

        with(holder.binding) {
            tvSlotTime.text = DateUtils.formatSlotRange(context, slot.startTime, slot.endTime)
            tvCapacity.text = "${context.getString(R.string.label_capacity)}: " +
                context.getString(R.string.value_capacity_kw, slot.capacityKw)

            btnSelect.setText(actionLabelRes)

            val select = View.OnClickListener { onSlotSelected(slot) }
            root.setOnClickListener(select)
            btnSelect.setOnClickListener(select)
        }
    }
}

// ==================== DIFF ====================

private object SlotDiff : DiffUtil.ItemCallback<SlotResponse>() {
    override fun areItemsTheSame(old: SlotResponse, new: SlotResponse) = old.id == new.id
    override fun areContentsTheSame(old: SlotResponse, new: SlotResponse) = old == new
}
