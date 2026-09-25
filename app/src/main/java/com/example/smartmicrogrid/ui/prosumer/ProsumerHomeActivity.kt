package com.example.smartmicrogrid.ui.prosumer

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.databinding.ActivityProsumerHomeBinding
import com.example.smartmicrogrid.ui.auth.LoginActivity
import com.example.smartmicrogrid.utils.SessionManager

/**
 * File: ProsumerHomeActivity.kt
 * Purpose: PLACEHOLDER home for the Prosumer role. Shows the stored name, email and NIC and a
 *          Logout button. The real dashboard replaces this later.
 * Author: Mobile Team
 * Date: 2026
 */
class ProsumerHomeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityProsumerHomeBinding
    private lateinit var session: SessionManager

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        session = SessionManager(applicationContext)

        // Safety net: process restore can bring this screen back after the session ended.
        if (!session.isLoggedIn()) {
            goToLogin()
            return
        }

        binding = ActivityProsumerHomeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        showSession()
        binding.btnLogout.setOnClickListener {
            session.clear()
            goToLogin()
        }
    }

    // ==================== HELPERS ====================

    private fun showSession() {
        val notAvailable = getString(R.string.value_not_available)
        val name = session.getName().orEmpty()
        val email = session.getEmail() ?: notAvailable
        val nic = session.getNic() ?: notAvailable

        binding.tvGreeting.text = "${getString(R.string.greeting_prefix)} $name".trim()
        binding.tvEmail.text = "${getString(R.string.label_email)}: $email"
        binding.tvNic.text = "${getString(R.string.label_nic)}: $nic"
    }

    private fun goToLogin() {
        startActivity(
            Intent(this, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }
}
