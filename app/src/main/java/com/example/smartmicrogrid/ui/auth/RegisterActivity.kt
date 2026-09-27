package com.example.smartmicrogrid.ui.auth

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.RegisterProsumerRequest
import com.example.smartmicrogrid.databinding.ActivityRegisterBinding
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.viewmodel.AuthState
import com.example.smartmicrogrid.viewmodel.AuthViewModel
import com.google.android.material.textfield.TextInputLayout

/**
 * File: RegisterActivity.kt
 * Purpose: Prosumer self-registration form. Runs basic client-side checks with inline field
 *          errors, then delegates to AuthViewModel. On success the account is "pending" until
 *          Backoffice approves it, so the user is sent back to Login without a session.
 * Author: Mobile Team
 * Date: 2026
 */
class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private val viewModel by viewModels<AuthViewModel>()

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeRegisterState()
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.btnRegister.setOnClickListener { attemptRegister() }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptRegister()
                true
            } else {
                false
            }
        }

        binding.tvLoginLink.setOnClickListener { finish() }
    }

    private fun attemptRegister() {
        buildValidatedRequest()?.let { viewModel.registerProsumer(it) }
    }

    // ==================== VALIDATION ====================

    /**
     * Basic client-side checks: every field filled, panel capacity > 0, password length
     * >= Constants.MIN_PASSWORD_LENGTH. Shows inline errors, focuses the first bad field,
     * and returns null if anything is invalid. Real rules are enforced by the backend.
     */
    private fun buildValidatedRequest(): RegisterProsumerRequest? {
        clearErrors()

        val nic = binding.etNic.text?.toString().orEmpty().trim()
        val name = binding.etName.text?.toString().orEmpty().trim()
        val email = binding.etEmail.text?.toString().orEmpty().trim()
        val contact = binding.etContact.text?.toString().orEmpty().trim()
        val address = binding.etAddress.text?.toString().orEmpty().trim()
        val panelText = binding.etPanelCapacity.text?.toString().orEmpty().trim()
        val password = binding.etPassword.text?.toString().orEmpty() // never trimmed

        val required = getString(R.string.error_field_required)
        var firstInvalid: View? = null

        fun flag(layout: TextInputLayout, message: String, field: View) {
            layout.error = message
            if (firstInvalid == null) firstInvalid = field
        }

        if (nic.isEmpty()) flag(binding.tilNic, required, binding.etNic)
        if (name.isEmpty()) flag(binding.tilName, required, binding.etName)
        if (email.isEmpty()) flag(binding.tilEmail, required, binding.etEmail)
        if (contact.isEmpty()) flag(binding.tilContact, required, binding.etContact)
        if (address.isEmpty()) flag(binding.tilAddress, required, binding.etAddress)

        val panelCapacity = panelText.toDoubleOrNull()
        if (panelText.isEmpty()) {
            flag(binding.tilPanelCapacity, required, binding.etPanelCapacity)
        } else if (panelCapacity == null || panelCapacity <= 0.0) {
            flag(
                binding.tilPanelCapacity,
                getString(R.string.error_panel_capacity_invalid),
                binding.etPanelCapacity
            )
        }

        if (password.isEmpty()) {
            flag(binding.tilPassword, required, binding.etPassword)
        } else if (password.length < Constants.MIN_PASSWORD_LENGTH) {
            flag(
                binding.tilPassword,
                getString(R.string.error_password_too_short, Constants.MIN_PASSWORD_LENGTH),
                binding.etPassword
            )
        }

        if (firstInvalid != null || panelCapacity == null) {
            firstInvalid?.requestFocus()
            return null
        }

        return RegisterProsumerRequest(
            nic = nic,
            name = name,
            email = email,
            contactNumber = contact,
            address = address,
            panelCapacityKw = panelCapacity,
            password = password
        )
    }

    private fun clearErrors() {
        listOf(
            binding.tilNic, binding.tilName, binding.tilEmail, binding.tilContact,
            binding.tilAddress, binding.tilPanelCapacity, binding.tilPassword
        ).forEach { it.error = null }
    }

    // ==================== OBSERVERS ====================

    private fun observeRegisterState() {
        viewModel.registerState.observe(this) { state ->
            when (state) {
                AuthState.Idle -> setLoading(false)

                AuthState.Loading -> setLoading(true)

                is AuthState.Success -> {
                    setLoading(false)
                    // Reset first so a rotation doesn't replay this Success.
                    viewModel.resetRegisterState()
                    Toast.makeText(
                        this,
                        R.string.msg_registration_submitted,
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }

                is AuthState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetRegisterState()
                }
            }
        }
    }

    // ==================== HELPERS ====================

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        val enabled = !loading
        listOf<View>(
            binding.tilNic, binding.tilName, binding.tilEmail, binding.tilContact,
            binding.tilAddress, binding.tilPanelCapacity, binding.tilPassword,
            binding.btnRegister, binding.tvLoginLink
        ).forEach { it.isEnabled = enabled }
    }
}
