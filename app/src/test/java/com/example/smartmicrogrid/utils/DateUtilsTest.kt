package com.example.smartmicrogrid.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import java.util.TimeZone

/**
 * File: DateUtilsTest.kt
 * Purpose: Unit tests for DateUtils — turning the backend's timestamp text into a real moment in
 *          time, and turning a moment back into text a person reads (or the backend accepts).
 * Author: Mobile Team
 * Date: 2026
 *
 * HOW TO READ A TEST HERE. Every test has three beats:
 *   1. ARRANGE — set up the input (often just a string).
 *   2. ACT     — call the function being tested.
 *   3. ASSERT  — check the answer is what a person would expect. If it isn't, the test fails and
 *                prints "expected X but was Y".
 * The test names are plain sentences (Kotlin allows spaces inside backticks), so a failing name
 * reads like a description of what broke.
 *
 * WHY THE TIME ZONE AND LOCALE ARE PINNED. formatForDisplay() converts to the DEVICE's time zone
 * and language. Left alone, a test would pass on one machine and fail on another. @Before pins
 * both to fixed values and @After restores them, so results are identical everywhere.
 *
 * NOT TESTED HERE: formatSlotRange(), because it needs an Android Context to look up a string
 * resource, which a plain JVM test doesn't have.
 */
class DateUtilsTest {

    private lateinit var originalTimeZone: TimeZone
    private lateinit var originalLocale: Locale

    @Before
    fun pinTimeZoneAndLocale() {
        originalTimeZone = TimeZone.getDefault()
        originalLocale = Locale.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
        Locale.setDefault(Locale.US) // month names come out as "Sep", not a localised spelling
    }

    @After
    fun restoreTimeZoneAndLocale() {
        TimeZone.setDefault(originalTimeZone)
        Locale.setDefault(originalLocale)
    }

    // ==================== parseIso ====================

    // Real-world bug caught: the most common backend format stops parsing at all, so every
    // reservation time in the app is blank or wrong.
    @Test
    fun `parseIso reads a normal timestamp ending in Z`() {
        val result = DateUtils.parseIso("2026-09-25T14:00:00Z")

        assertEquals(Instant.parse("2026-09-25T14:00:00Z"), result)
    }

    // Real-world bug caught: the backend sometimes includes milliseconds. A parser written for
    // one exact pattern ("...:00Z" only) rejects these and the time silently vanishes.
    @Test
    fun `parseIso reads a timestamp with milliseconds`() {
        val result = DateUtils.parseIso("2026-09-25T14:00:00.123Z")

        assertEquals(Instant.parse("2026-09-25T14:00:00.123Z"), result)
    }

    // Real-world bug caught: C# (.NET) can send SEVEN fractional digits ("ticks"). Old-style date
    // parsers handle at most three, so times from .NET would fail intermittently.
    @Test
    fun `parseIso reads dotnet style seven digit fractions`() {
        val result = DateUtils.parseIso("2026-09-25T14:00:00.1234567Z")

        assertEquals(Instant.parse("2026-09-25T14:00:00.1234567Z"), result)
    }

    // Real-world bug caught: a timestamp carrying an offset (+05:30) must be converted to the
    // same moment in UTC, not just have the offset text ignored (which would be 5.5 hours off).
    @Test
    fun `parseIso converts an explicit offset to the same moment`() {
        val result = DateUtils.parseIso("2026-09-25T19:30:00+05:30")

        assertEquals(Instant.parse("2026-09-25T14:00:00Z"), result)
    }

    // Real-world bug caught: the backend sometimes sends NO zone at all. The rule is "assume
    // UTC". A version that assumed the DEVICE's zone instead would shift every such time by the
    // user's UTC offset — and only for users outside UTC, so it would pass on a developer's
    // machine. The test therefore runs with the device set to Sri Lanka time (UTC+5:30).
    @Test
    fun `parseIso assumes UTC when there is no zone even if the device is not in UTC`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Colombo"))

        val result = DateUtils.parseIso("2026-09-25T14:00:00")

        assertEquals(Instant.parse("2026-09-25T14:00:00Z"), result)
    }

    // Real-world bug caught: bad text from the server crashing the app with an exception
    // instead of quietly giving "no value". The contract is: never throw, return null.
    @Test
    fun `parseIso returns null for garbage instead of crashing`() {
        assertNull(DateUtils.parseIso("not a date"))
        assertNull(DateUtils.parseIso("2026-13-45T99:99:99Z")) // looks right, isn't a real date
    }

    // Real-world bug caught: a date with no time of day ("2026-09-25") being guessed at as
    // midnight. The current rule is that it isn't a timestamp, so it's null — this test records
    // that decision so changing it later is a conscious choice, not an accident.
    @Test
    fun `parseIso returns null for a date without a time`() {
        assertNull(DateUtils.parseIso("2026-09-25"))
    }

    // Real-world bug caught: optional fields (like approvedAt on a still-pending reservation)
    // arrive as null or empty. Passing those in must be safe, not a NullPointerException.
    @Test
    fun `parseIso returns null for null blank and empty input`() {
        assertNull(DateUtils.parseIso(null))
        assertNull(DateUtils.parseIso(""))
        assertNull(DateUtils.parseIso("   "))
    }

    // ==================== formatForDisplay ====================

    // Real-world bug caught: the date format string being wrong (day/month swapped, 12-hour vs
    // 24-hour, missing comma). This pins the exact text a user sees.
    @Test
    fun `formatForDisplay produces the expected text`() {
        val result = DateUtils.formatForDisplay("2026-09-25T14:00:00Z")

        assertEquals("25 Sep 2026, 14:00", result)
    }

    // Real-world bug caught: showing the SERVER's UTC time instead of the user's local time. A
    // charging slot at 14:00 UTC is 19:30 in Sri Lanka; showing "14:00" would send the user to
    // the station five and a half hours early.
    @Test
    fun `formatForDisplay converts to the device time zone`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Colombo")) // UTC+5:30

        val result = DateUtils.formatForDisplay("2026-09-25T14:00:00Z")

        assertEquals("25 Sep 2026, 19:30", result)
    }

    // Real-world bug caught: converting only the clock time and forgetting the DATE changes too.
    // 20:00 UTC is 01:30 the NEXT day in Sri Lanka; a bug here shows the right time on the
    // wrong day.
    @Test
    fun `formatForDisplay rolls the date forward when the zone crosses midnight`() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Colombo"))

        val result = DateUtils.formatForDisplay("2026-09-25T20:00:00Z")

        assertEquals("26 Sep 2026, 01:30", result)
    }

    // Real-world bug caught: a value we can't parse blanking the whole screen. The rule is to
    // show the original text rather than nothing, so the user still sees something.
    @Test
    fun `formatForDisplay falls back to the original text when it cannot parse`() {
        assertEquals("garbage", DateUtils.formatForDisplay("garbage"))
    }

    @Test
    fun `formatForDisplay returns empty text for null`() {
        assertEquals("", DateUtils.formatForDisplay(null))
    }

    // ==================== formatTimeForDisplay ====================

    // Real-world bug caught: the end of a slot ("14:00 – 15:00") showing the wrong time or
    // ignoring the time zone, while the start (which uses formatForDisplay) is correct.
    @Test
    fun `formatTimeForDisplay shows only the time of day in the device time zone`() {
        assertEquals("15:00", DateUtils.formatTimeForDisplay("2026-09-25T15:00:00Z"))

        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Colombo"))
        assertEquals("20:30", DateUtils.formatTimeForDisplay("2026-09-25T15:00:00Z"))
    }

    // ==================== formatMillisForDisplay ====================

    // Real-world bug caught: the "Showing offline data from ..." banner using a different time
    // format or zone than the rest of the app, or reading milliseconds as seconds (which would
    // give a date in 1970).
    @Test
    fun `formatMillisForDisplay matches formatForDisplay for the same moment`() {
        val instant = Instant.parse("2026-09-25T14:00:00Z")

        val fromMillis = DateUtils.formatMillisForDisplay(instant.toEpochMilli())

        assertEquals("25 Sep 2026, 14:00", fromMillis)
        assertEquals(DateUtils.formatForDisplay("2026-09-25T14:00:00Z"), fromMillis)
    }

    // ==================== toIsoUtc ====================

    // Real-world bug caught: sending the backend a slot time it can't read, or one in the wrong
    // zone. This is the exact text that goes on the wire when an operator edits a slot.
    @Test
    fun `toIsoUtc writes the backend format in UTC`() {
        val result = DateUtils.toIsoUtc(Instant.parse("2026-09-25T08:30:00Z"))

        assertEquals("2026-09-25T08:30:00Z", result)
    }

    // Real-world bug caught: fractional seconds leaking into what we send. The picker only has
    // minutes, and the backend format we settled on is whole seconds.
    @Test
    fun `toIsoUtc drops fractions of a second`() {
        val result = DateUtils.toIsoUtc(Instant.parse("2026-09-25T08:30:00.987Z"))

        assertEquals("2026-09-25T08:30:00Z", result)
    }

    // Real-world bug caught: the operator picks 14:00 local time in the slot editor and the
    // wrong moment reaches the server (converted twice, or not at all). Here we do exactly what
    // the editor does: build 14:00 Sri Lanka time, send it as UTC, read it back.
    // 14:00 at UTC+5:30 must be 08:30 UTC, and reading that back must give the same moment.
    @Test
    fun `a picked local time survives the trip to the backend format and back`() {
        val zone = ZoneId.of("Asia/Colombo")
        val picked = ZonedDateTime.of(2026, 9, 25, 14, 0, 0, 0, zone).toInstant()

        val sent = DateUtils.toIsoUtc(picked)
        val readBack = DateUtils.parseIso(sent)

        assertEquals("2026-09-25T08:30:00Z", sent)
        assertNotNull(readBack)
        assertEquals(picked, readBack)
    }
}
