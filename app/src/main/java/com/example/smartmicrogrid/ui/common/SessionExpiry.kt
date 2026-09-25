package com.example.smartmicrogrid.ui.common

import android.app.Activity
import android.content.Intent
import android.widget.Toast
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.ui.auth.LoginActivity
import com.example.smartmicrogrid.utils.SessionManager

/**
 * File: SessionExpiry.kt
 * Purpose: One place for "the server rejected our token (HTTP 401)": tell the user, clear the
 *          stored session, and send them to a fresh Login. Retrying can't fix an expired JWT,
 *          so authenticated screens call this instead of showing a Retry button.
 * Author: Mobile Team
 * Date: 2026
 */

// ==================== SESSION EXPIRY ====================

/** Shows the "session expired" toast, clears the session, and restarts at LoginActivity. */
fun Activity.handleSessionExpired() {
    Toast.makeText(this, R.string.msg_session_expired, Toast.LENGTH_LONG).show()
    SessionManager(applicationContext).clear()
    startActivity(
        Intent(this, LoginActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    )
    finish()
}
