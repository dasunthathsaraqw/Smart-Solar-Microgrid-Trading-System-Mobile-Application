package com.example.smartmicrogrid.ui.operator

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.SlotResponse
import com.example.smartmicrogrid.databinding.ActivitySlotManagementBinding
import com.example.smartmicrogrid.databinding.DialogEditSlotBinding
import com.example.smartmicrogrid.ui.booking.SlotAdapter
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.viewmodel.SlotListState
import com.example.smartmicrogrid.viewmodel.SlotManagementViewModel
import com.example.smartmicrogrid.viewmodel.SlotUpdateState
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.timepicker.MaterialTimePicker
import com.google.android.material.timepicker.TimeFormat
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * File: SlotManagementActivity.kt
 * Purpose: Lets a grid operator adjust the slots of their own station. Lists the upcoming
 *          unbooked slots (the only slot list the API offers — the same GET the prosumer
 *          booking flow uses, pointed at the operator's station) and, on tap, opens an edit
 *          dialog for its start, end and capacity. Times are chosen with Material date and time
 *          pickers; only fields that actually changed are sent.
 * Author: Mobile Team
 * Date: 2026
 */
class SlotManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySlotManagementBinding
    private val viewModel by viewModels<SlotManagementViewModel>()
    private val adapter = SlotAdapter(actionLabelRes = R.string.action_edit_slot) { slot ->
        showEditDialog(slot)
    }

    // The open edit dialog, if any, so the update state can reach it.
    private var editDialog: AlertDialog? = null
    private var editBinding: DialogEditSlotBinding? = null

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySlotManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.rvSlots.adapter = adapter
        binding.btnRetry.setOnClickListener { viewModel.loadSlots() }

        observeListState()
        observeUpdateState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.listState.value == null) {
            viewModel.loadSlots()
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeListState() {
        viewModel.listState.observe(this) { state ->
            when (state) {
                SlotListState.Loading -> showOnly(binding.progressBar)

                // Also the state after a successful edit: the ViewModel swaps the saved slot in.
                is SlotListState.Success -> {
                    adapter.submitList(state.slots)
                    showOnly(if (state.slots.isEmpty()) binding.tvEmpty else binding.rvSlots)
                }

                is SlotListState.Error -> {
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

    private fun observeUpdateState() {
        viewModel.updateState.observe(this) { state ->
            when (state) {
                // Idle also follows a reset after Error — keep the inline error on screen.
                SlotUpdateState.Idle -> setSaving(false)

                SlotUpdateState.Loading -> setSaving(true)

                is SlotUpdateState.Success -> {
                    setSaving(false)
                    // Reset first so a rotation doesn't replay this Success (a second toast).
                    viewModel.resetUpdateState()
                    editDialog?.dismiss()
                    Toast.makeText(this, R.string.msg_slot_updated, Toast.LENGTH_SHORT).show()
                }

                is SlotUpdateState.Error -> {
                    setSaving(false)
                    viewModel.resetUpdateState()
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        showSaveError(state.message)
                    }
                }
            }
        }
    }

    /** The server's own message, inline in the open dialog (which stays open for correction). */
    private fun showSaveError(message: String) {
        val dialogBinding = editBinding
        if (dialogBinding == null) {
            // The dialog is gone (e.g. after a rotation) — fall back to a toast.
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } else {
            dialogBinding.tvError.text = message
            dialogBinding.tvError.visibility = View.VISIBLE
        }
    }

    // ==================== EDIT DIALOG ====================

    private fun showEditDialog(slot: SlotResponse) {
        val dialogBinding = DialogEditSlotBinding.inflate(layoutInflater)

        // The values being edited. Times are kept as the backend's ISO strings; an untouched
        // field still equals the slot's own value, which is how "unchanged" is detected.
        var startIso = slot.startTime
        var endIso = slot.endTime

        dialogBinding.etStartTime.setText(DateUtils.formatForDisplay(startIso))
        dialogBinding.etEndTime.setText(DateUtils.formatForDisplay(endIso))
        dialogBinding.etCapacity.setText(slot.capacityKw.toString())

        dialogBinding.etStartTime.setOnClickListener {
            pickDateTime(startIso) { picked ->
                startIso = picked
                dialogBinding.etStartTime.setText(DateUtils.formatForDisplay(picked))
            }
        }
        dialogBinding.etEndTime.setOnClickListener {
            pickDateTime(endIso) { picked ->
                endIso = picked
                dialogBinding.etEndTime.setText(DateUtils.formatForDisplay(picked))
            }
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSave.setOnClickListener {
            val capacity = validCapacity(dialogBinding) ?: return@setOnClickListener

            val startChanged = startIso != slot.startTime
            val endChanged = endIso != slot.endTime
            val capacityChanged = capacity != slot.capacityKw
            if (!startChanged && !endChanged && !capacityChanged) {
                Toast.makeText(this, R.string.msg_no_changes, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Only what changed is sent; null leaves a field as it is.
            viewModel.updateSlot(
                slotId = slot.id,
                startTime = if (startChanged) startIso else null,
                endTime = if (endChanged) endIso else null,
                capacityKw = if (capacityChanged) capacity else null
            )
        }

        dialog.setOnDismissListener {
            editDialog = null
            editBinding = null
        }
        editDialog = dialog
        editBinding = dialogBinding
        dialog.show()
    }

    /** The capacity as a number > 0, or null after showing the inline error. */
    private fun validCapacity(dialogBinding: DialogEditSlotBinding): Double? {
        dialogBinding.tilCapacity.error = null

        val text = dialogBinding.etCapacity.text?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            dialogBinding.tilCapacity.error = getString(R.string.error_field_required)
            return null
        }
        val value = text.toDoubleOrNull()
        if (value == null || value <= 0.0) {
            dialogBinding.tilCapacity.error = getString(R.string.error_panel_capacity_invalid)
            return null
        }
        return value
    }

    /** Disables the dialog's inputs and shows its spinner while a save is in flight. */
    private fun setSaving(saving: Boolean) {
        val dialogBinding = editBinding ?: return
        val enabled = !saving

        dialogBinding.tilStartTime.isEnabled = enabled
        dialogBinding.tilEndTime.isEnabled = enabled
        dialogBinding.tilCapacity.isEnabled = enabled
        dialogBinding.btnSave.isEnabled = enabled
        dialogBinding.btnCancel.isEnabled = enabled
        dialogBinding.progressSave.visibility = if (saving) View.VISIBLE else View.GONE
        if (saving) dialogBinding.tvError.visibility = View.GONE
        editDialog?.setCancelable(enabled)
    }

    // ==================== DATE / TIME PICKERS ====================

    /**
     * Picks a date, then a time, starting from [currentIso], and returns the result as the
     * backend's UTC ISO string. The operator chooses in local time; the conversion to UTC is
     * done here so no one ever types or reads a time zone.
     */
    private fun pickDateTime(currentIso: String, onPicked: (String) -> Unit) {
        val zone = ZoneId.systemDefault()
        val current = DateUtils.parseIso(currentIso)?.atZone(zone) ?: ZonedDateTime.now(zone)

        // MaterialDatePicker speaks in UTC-midnight milliseconds of the calendar day.
        val datePicker = MaterialDatePicker.Builder.datePicker()
            .setTitleText(R.string.title_select_date)
            .setSelection(
                current.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            )
            .build()

        datePicker.addOnPositiveButtonClickListener { utcMillis ->
            val date = Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate()

            val timePicker = MaterialTimePicker.Builder()
                .setTimeFormat(TimeFormat.CLOCK_24H)
                .setHour(current.hour)
                .setMinute(current.minute)
                .setTitleText(R.string.title_select_time)
                .build()

            timePicker.addOnPositiveButtonClickListener {
                val local = ZonedDateTime.of(date, LocalTime.of(timePicker.hour, timePicker.minute), zone)
                onPicked(DateUtils.toIsoUtc(local.toInstant()))
            }
            timePicker.show(supportFragmentManager, TAG_TIME_PICKER)
        }
        datePicker.show(supportFragmentManager, TAG_DATE_PICKER)
    }

    // ==================== HELPERS ====================

    /** Shows exactly one of: list, spinner, empty text, error block. */
    private fun showOnly(visible: View) {
        listOf(binding.rvSlots, binding.progressBar, binding.tvEmpty, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    private companion object {
        const val TAG_DATE_PICKER = "slot_date_picker"
        const val TAG_TIME_PICKER = "slot_time_picker"
    }
}
