package com.example.smartmicrogrid.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.databinding.ActivityLoginBinding
import com.example.smartmicrogrid.ui.operator.OperatorHomeActivity
import com.example.smartmicrogrid.ui.prosumer.ProsumerHomeActivity
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.SessionManager
import com.example.smartmicrogrid.viewmodel.AuthState
import com.example.smartmicrogrid.viewmodel.AuthViewModel

/**
 * File: LoginActivity.kt
 * Purpose: Launcher screen. Collects email + password, delegates to AuthViewModel, and routes
 *          to the role-specific home on success. Skips the form if a valid session exists.
 * Author: Mobile Team
 * Date: 2026
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val viewModel by viewModels<AuthViewModel>()
    private lateinit var session: SessionManager

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(applicationContext)

        // Already signed in (and not expired)? Go straight to the right home.
        // If the stored role can't be routed (Backoffice/unknown) the session is cleared
        // and we fall through to the login form — this is the launcher, so we can't finish().
        if (session.isLoggedIn() && routeByRole(session.getUserType())) {
            finish()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        observeLoginState()
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.btnLogin.setOnClickListener { attemptLogin() }

        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                attemptLogin()
                true
            } else {
                false
            }
        }

        binding.tvRegisterLink.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun attemptLogin() {
        viewModel.login(
            email = binding.etEmail.text?.toString().orEmpty(),
            password = binding.etPassword.text?.toString().orEmpty()
        )
    }

    // ==================== OBSERVERS ====================

    private fun observeLoginState() {
        viewModel.loginState.observe(this) { state ->
            when (state) {
                AuthState.Idle -> setLoading(false)

                AuthState.Loading -> setLoading(true)

                is AuthState.Success -> {
                    setLoading(false)
                    // Reset first so a rotation doesn't replay this Success.
                    viewModel.resetLoginState()
                    if (routeByRole(state.role)) {
                        Toast.makeText(
                            this,
                            getString(R.string.msg_login_welcome, state.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        finish()
                    }
                }

                is AuthState.Error -> {
                    setLoading(false)
                    Toast.makeText(this, state.message, Toast.LENGTH_LONG).show()
                    viewModel.resetLoginState()
                }
            }
        }
    }

    // ==================== HELPERS ====================

    private fun setLoading(loading: Boolean) {
        binding.progressBar.visibility = if (loading) View.VISIBLE else View.GONE
        val enabled = !loading
        binding.tilEmail.isEnabled = enabled
        binding.tilPassword.isEnabled = enabled
        binding.btnLogin.isEnabled = enabled
        binding.tvRegisterLink.isEnabled = enabled
    }

    /**
     * Opens the home screen for [role].
     *
     * @return true if a home screen was started (caller should finish()); false if the role
     *         has no mobile home — in that case a toast is shown and the session is cleared.
     */
    private fun routeByRole(role: String?): Boolean {
        return when (role) {
            Constants.ROLE_PROSUMER -> {
                startActivity(Intent(this, ProsumerHomeActivity::class.java))
                true
            }
            Constants.ROLE_OPERATOR -> {
                startActivity(Intent(this, OperatorHomeActivity::class.java))
                true
            }
            Constants.ROLE_BACKOFFICE -> {
                Toast.makeText(this, R.string.msg_backoffice_web_only, Toast.LENGTH_LONG).show()
                session.clear()
                false
            }
            else -> {
                Toast.makeText(this, R.string.msg_unknown_role, Toast.LENGTH_LONG).show()
                session.clear()
                false
            }
        }
    }
}
