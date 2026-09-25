package com.example.smartmicrogrid.utils

/**
 * File: Validators.kt
 * Purpose: Small, dependency-free input checks shared by the ViewModels.
 * Author: Mobile Team
 * Date: 2026
 *
 * Pure Kotlin on purpose (no Android classes), so it also runs in plain JVM unit tests.
 * These are basic shape checks only — every real rule is enforced by the backend.
 */
object Validators {

    /**
     * The same pattern as Android's own android.util.Patterns.EMAIL_ADDRESS, copied here so the
     * check behaves identically but doesn't need the Android framework (whose Patterns class is an
     * empty stub in JVM unit tests).
     */
    private val EMAIL_REGEX = Regex(
        "[a-zA-Z0-9\\+\\.\\_\\%\\-\\+]{1,256}" +
            "\\@" +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,64}" +
            "(" +
            "\\." +
            "[a-zA-Z0-9][a-zA-Z0-9\\-]{0,25}" +
            ")+"
    )

    /** True if [email] looks like an email address (the WHOLE string must match). */
    fun isValidEmail(email: String): Boolean = EMAIL_REGEX.matches(email)
}
