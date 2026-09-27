package com.example.smartmicrogrid.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * File: ValidatorsTest.kt
 * Purpose: Unit tests for Validators.isValidEmail(), which replaced Android's Patterns.EMAIL_ADDRESS
 *          so the check can run in plain JVM tests.
 * Author: Mobile Team
 * Date: 2026
 *
 * The important promise is "behaves like the Android version did". These tests pin the cases that
 * matter, so a future tidy-up of the pattern can't quietly start rejecting real addresses (users
 * locked out of login) or accepting junk (a pointless round trip to the server).
 */
class ValidatorsTest {

    // Real-world bug caught: the check rejecting ordinary addresses, so a real user can't log in.
    @Test
    fun `accepts ordinary email addresses`() {
        assertTrue(Validators.isValidEmail("kamal@example.com"))
        assertTrue(Validators.isValidEmail("first.last@example.co.lk"))
        assertTrue(Validators.isValidEmail("user+tag@sub.example.org"))
        assertTrue(Validators.isValidEmail("user_name-1@example-site.com"))
    }

    // Real-world bug caught: a check so loose that obvious typos (no @, no domain dot) reach the
    // server, which then has to reject them.
    @Test
    fun `rejects text that is not an email address`() {
        assertFalse(Validators.isValidEmail("not-an-email"))
        assertFalse(Validators.isValidEmail("missing-at.example.com"))
        assertFalse(Validators.isValidEmail("no-domain-dot@example"))
        assertFalse(Validators.isValidEmail("@example.com"))
        assertFalse(Validators.isValidEmail("user@"))
    }

    // Real-world bug caught: the pattern matching only PART of the text (so "a@b.com and junk"
    // would pass). The whole string has to be an email.
    @Test
    fun `rejects an address with spaces or trailing junk`() {
        assertFalse(Validators.isValidEmail("user name@example.com"))
        assertFalse(Validators.isValidEmail("user@example.com extra"))
        assertFalse(Validators.isValidEmail(" user@example.com"))
    }

    @Test
    fun `rejects an empty string`() {
        assertFalse(Validators.isValidEmail(""))
    }
}
