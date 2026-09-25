package com.example.smartmicrogrid.ui.profile

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.UpdateOwnProfileRequest
import com.example.smartmicrogrid.databinding.ActivityProfileBinding
import com.example.smartmicrogrid.databinding.DialogChangePasswordBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.viewmodel.ProfileActionState
import com.example.smartmicrogrid.viewmodel.ProfileState
import com.example.smartmicrogrid.viewmodel.ProfileViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * File: ProfileActivity.kt
 * Purpose: The prosumer's own profile and account actions, driven by ProfileViewModel:
 *          - view the profile (the NIC is read-only text);
 *          - edit name, email, contact number, address and panel capacity — only the fields that
 *            actually changed are sent;
 *          - change password (dialog, three fields);
 *          - request deactivation (after an "are you sure?" dialog). The account stays active and
 *            the user stays logged in; the button just becomes "Deactivation requested".
 * Author: Mobile Team
 * Date: 2026
 *
 * Prosumer-only: GridOperators have no profile endpoints, and no route leads here for them.
 */
class ProfileActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProfileBinding
    private val viewModel by viewModels<ProfileViewModel>()

    /** The profile as the server last returned it: what "changed" is measured against. */
    private var baseline: ProsumerResponse? = null

    // The one spinner serves both the profile load and account actions, so its visibility is
    // derived from both flags. Starts "loading" to match the layout default.
    private var profileLoading = true
    private var actionBusy = false

    /**
     * True right after a rotation, so the first profile replay doesn't overwrite what the user
     * had typed (the system has already restored those fields). See [bindProfile].
     */
    private var keepRestoredFields = false

    // The open change-password dialog, if any, so the action state can reach it.
    private var passwordDialog: AlertDialog? = null
    private var passwordBinding: DialogChangePasswordBinding? = null

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        keepRestoredFields = savedInstanceState != null

        setupListeners()
        observeProfileState()
        observeActionState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.profileState.value == null) {
            viewModel.loadProfile()
        }
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.loadProfile() }

        binding.btnSaveProfile.setOnClickListener { attemptSave() }
        binding.etPanelCapacity.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptSave()
                true
            } else {
                false
            }
        }

        binding.btnChangePassword.setOnClickListener { showPasswordDialog() }
        binding.btnRequestDeactivation.setOnClickListener { confirmDeactivation() }
    }

    // ==================== OBSERVERS ====================

    private fun observeProfileState() {
        viewModel.profileState.observe(this) { state ->
            when (state) {
                ProfileState.Loading -> {
                    profileLoading = true
                    binding.contentScroll.visibility = View.GONE
                    binding.errorContainer.visibility = View.GONE
                }

                is ProfileState.Success -> {
                    profileLoading = false
                    bindProfile(state.profile)
                    binding.contentScroll.visibility = View.VISIBLE
                    binding.errorContainer.visibility = View.GONE
                }

                is ProfileState.Error -> {
                    profileLoading = false
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        binding.tvErrorMessage.text = state.message
                        binding.contentScroll.visibility = View.GONE
                        binding.errorContainer.visibility = View.VISIBLE
                    }
                }
            }
            refreshProgress()
        }
    }

    private fun observeActionState() {
        viewModel.actionState.observe(this) { state ->
            when (state) {
                // Idle also follows a reset after Error — keep an inline error on screen.
                ProfileActionState.Idle -> setBusy(false)

                ProfileActionState.Loading -> {
                    passwordBinding?.tvError?.visibility = View.GONE
                    setBusy(true)
                }

                is ProfileActionState.Success -> {
                    setBusy(false)
                    // Reset first so a rotation doesn't replay this Success (a second toast).
                    viewModel.resetActionState()
                    // A saved profile or a deactivation request has already updated the form via
                    // profileState; a password change just closes its dialog.
                    passwordDialog?.dismiss()
                    Toast.makeText(this, state.message, Toast.LENGTH_SHORT).show()
                }

                is ProfileActionState.Error -> {
                    setBusy(false)
                    viewModel.resetActionState()
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        showActionError(state.message)
                    }
                }
            }
        }
    }

    /** Inline in the password dialog when it is open (a wrong current password, a mismatch); a toast otherwise. */
    private fun showActionError(message: String) {
        val dialogBinding = passwordBinding
        if (dialogBinding == null) {
            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        } else {
            dialogBinding.tvError.text = message
            dialogBinding.tvError.visibility = View.VISIBLE
        }
    }

    // ==================== BINDING ====================

    private fun bindProfile(profile: ProsumerResponse) {
        baseline = profile
        binding.tvNic.text = profile.nic

        // After a rotation the system has already put back what the user typed; overwriting it
        // with the server's copy would lose unsaved edits. Only skip when there IS restored text
        // (a rotation during the first load leaves the fields empty and they must be filled).
        if (keepRestoredFields && !binding.etName.text.isNullOrEmpty()) {
            keepRestoredFields = false
        } else {
            keepRestoredFields = false
            binding.etName.setText(profile.name)
            binding.etEmail.setText(profile.email)
            binding.etContact.setText(profile.contactNumber.orEmpty())
            binding.etAddress.setText(profile.address.orEmpty())
            binding.etPanelCapacity.setText(profile.panelCapacityKw?.toString().orEmpty())
        }

        // A pending request shows as a disabled "Deactivation requested"; the account stays active.
        binding.btnRequestDeactivation.setText(
            if (profile.deactivationRequested) R.string.label_deactivation_requested
            else R.string.action_request_deactivation
        )
        updateButtons()
    }

    // ==================== SAVE ====================

    /**
     * Builds an UpdateOwnProfileRequest holding only the fields that differ from the loaded
     * profile. Name and email are required. Contact, address and capacity are required only if
     * the profile already had a value (they can't be cleared); if it had none, leaving them
     * blank means "unchanged". Everything else is the server's to judge.
     */
    private fun attemptSave() {
        val current = baseline ?: return
        clearErrors()

        val name = binding.etName.text?.toString().orEmpty().trim()
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        val contact = binding.etContact.text?.toString().orEmpty().trim()
        val address = binding.etAddress.text?.toString().orEmpty().trim()
        val capacityText = binding.etPanelCapacity.text?.toString().orEmpty().trim()

        val required = getString(R.string.error_field_required)
        var firstInvalid: View? = null
        fun flag(message: String, field: View, layoutError: (String) -> Unit) {
            layoutError(message)
            if (firstInvalid == null) firstInvalid = field
        }

        if (name.isEmpty()) flag(required, binding.etName) { binding.tilName.error = it }
        if (email.isEmpty()) flag(required, binding.etEmail) { binding.tilEmail.error = it }
        if (contact.isEmpty() && !current.contactNumber.isNullOrEmpty()) {
            flag(required, binding.etContact) { binding.tilContact.error = it }
        }
        if (address.isEmpty() && !current.address.isNullOrEmpty()) {
            flag(required, binding.etAddress) { binding.tilAddress.error = it }
        }

        var capacity: Double? = null
        if (capacityText.isEmpty()) {
            if (current.panelCapacityKw != null) {
                flag(required, binding.etPanelCapacity) { binding.tilPanelCapacity.error = it }
            }
        } else {
            capacity = capacityText.toDoubleOrNull()
            if (capacity == null || capacity <= 0.0) {
                flag(getString(R.string.error_panel_capacity_invalid), binding.etPanelCapacity) {
                    binding.tilPanelCapacity.error = it
                }
            }
        }

        if (firstInvalid != null) {
            firstInvalid?.requestFocus()
            return
        }

        val request = UpdateOwnProfileRequest(
            name = name.takeIf { it != current.name },
            email = email.takeIf { it != current.email },
            contactNumber = contact.takeIf { it.isNotEmpty() && it != current.contactNumber.orEmpty() },
            address = address.takeIf { it.isNotEmpty() && it != current.address.orEmpty() },
            panelCapacityKw = capacity?.takeIf { it != current.panelCapacityKw }
        )

        val nothingChanged = request.name == null && request.email == null &&
            request.contactNumber == null && request.address == null &&
            request.panelCapacityKw == null
        if (nothingChanged) {
            Toast.makeText(this, R.string.msg_no_changes, Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.updateProfile(request)
    }

    private fun clearErrors() {
        listOf(
            binding.tilName, binding.tilEmail, binding.tilContact,
            binding.tilAddress, binding.tilPanelCapacity
        ).forEach { it.error = null }
    }

    // ==================== CHANGE PASSWORD ====================

    private fun showPasswordDialog() {
        val dialogBinding = DialogChangePasswordBinding.inflate(layoutInflater)
        val dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogBinding.root)
            .create()

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnSubmit.setOnClickListener {
            // The ViewModel checks length and confirmation; a wrong current password is the server's.
            viewModel.changePassword(
                oldPassword = dialogBinding.etOldPassword.text?.toString().orEmpty(),
                newPassword = dialogBinding.etNewPassword.text?.toString().orEmpty(),
                confirmPassword = dialogBinding.etConfirmPassword.text?.toString().orEmpty()
            )
        }

        dialog.setOnDismissListener {
            passwordDialog = null
            passwordBinding = null
        }
        passwordDialog = dialog
        passwordBinding = dialogBinding
        dialog.show()
    }

    // ==================== DEACTIVATION ====================

    /** Nothing is deactivated and no one is logged out: it only asks Backoffice to review. */
    private fun confirmDeactivation() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.action_request_deactivation)
            .setMessage(R.string.msg_deactivation_confirm)
            .setPositiveButton(R.string.action_request_deactivation) { _, _ ->
                viewModel.requestDeactivation()
            }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    // ==================== BUSY STATE ====================

    private fun setBusy(busy: Boolean) {
        actionBusy = busy
        updateButtons()

        passwordBinding?.let { dialogBinding ->
            val enabled = !busy
            dialogBinding.tilOldPassword.isEnabled = enabled
            dialogBinding.tilNewPassword.isEnabled = enabled
            dialogBinding.tilConfirmPassword.isEnabled = enabled
            dialogBinding.btnSubmit.isEnabled = enabled
            dialogBinding.btnCancel.isEnabled = enabled
            dialogBinding.progressSubmit.visibility = if (busy) View.VISIBLE else View.GONE
        }
        passwordDialog?.setCancelable(!busy)
        refreshProgress()
    }

    /** Buttons are off while an action runs; deactivation also stays off once requested. */
    private fun updateButtons() {
        val enabled = !actionBusy
        binding.btnSaveProfile.isEnabled = enabled
        binding.btnChangePassword.isEnabled = enabled
        binding.btnRequestDeactivation.isEnabled =
            enabled && baseline?.deactivationRequested != true
    }

    private fun refreshProgress() {
        binding.progressBar.visibility =
            if (profileLoading || actionBusy) View.VISIBLE else View.GONE
    }
}
