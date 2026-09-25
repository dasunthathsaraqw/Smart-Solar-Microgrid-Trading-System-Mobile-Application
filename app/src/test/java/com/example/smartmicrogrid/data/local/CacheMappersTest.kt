package com.example.smartmicrogrid.data.local

import com.example.smartmicrogrid.data.local.entity.CachedDashboardEntity
import com.example.smartmicrogrid.data.local.entity.CachedProfileEntity
import com.example.smartmicrogrid.data.local.entity.CachedReservationEntity
import com.example.smartmicrogrid.data.local.entity.CachedStationEntity
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.testutil.sampleDashboard
import com.example.smartmicrogrid.testutil.sampleProsumer
import com.example.smartmicrogrid.testutil.sampleReservation
import com.example.smartmicrogrid.testutil.sampleStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * File: CacheMappersTest.kt
 * Purpose: Unit tests for CacheMappers — the functions that convert between what the server sends
 *          (a DTO) and what the offline cache stores (an entity / database row), in both
 *          directions.
 * Author: Mobile Team
 * Date: 2026
 *
 * TWO KINDS OF CHECK, because each catches something the other can miss:
 *
 *  1. ROUND TRIP  (DTO -> entity -> DTO, result must EQUAL the original).
 *     Catches "I forgot to map a field": the field silently comes back empty offline. That is the
 *     easiest bug to introduce (add a field to the DTO later, forget the mapper) and the hardest
 *     to notice, because nothing crashes.
 *
 *  2. COLUMN CHECK  (each DTO field must land in the entity property of the SAME NAME).
 *     Catches "I swapped two fields": a mapper that swaps prosumerNic and prosumerName going in
 *     AND coming out cancels itself, so a round trip alone still passes. Looking at the middle
 *     step directly exposes it. The sample data uses a different value for every field so a
 *     swap can't hide by coincidence.
 *
 * NOTHING IS LOSSY for reservations, stations and profiles: the entities carry every DTO field, so
 * the round trip must give back an IDENTICAL object. The two deliberate exceptions, each with its
 * own tests below, are the entity's extra lastSyncedAt column (no DTO counterpart, so it is not
 * part of a response) and the dashboard's nested next reservation (stored as JSON; unreadable JSON
 * must become "no next reservation", never a crash).
 */
class CacheMappersTest {

    private val syncedAt = 1_758_808_800_000L // an arbitrary, recognisable "time of sync"

    // ==================== RESERVATION ====================

    // Real-world bug caught: a reservation field missing from the mapping, so offline the detail
    // screen loses (say) the cancellation reason or the approval time, with no error anywhere.
    @Test
    fun `reservation round trip returns an identical reservation`() {
        val original = sampleReservation()

        val roundTripped = original.toEntity(syncedAt).toResponse()

        assertEquals(original, roundTripped)
    }

    // Real-world bug caught: the optional fields (approvedAt, completedAt, cancellationReason...)
    // mishandled when they are null, which is the normal state of a Pending reservation.
    @Test
    fun `a pending reservation with all optional fields empty round trips unchanged`() {
        val pending = sampleReservation(
            status = "Pending",
            qrGeneratedAt = null,
            createdBy = null,
            updatedAt = null,
            approvedAt = null,
            approvedBy = null,
            completedAt = null,
            completedBy = null,
            cancelledAt = null,
            cancelledBy = null,
            cancellationReason = null
        )

        assertEquals(pending, pending.toEntity(syncedAt).toResponse())
    }

    // Real-world bug caught: two similar fields swapped in BOTH directions (a round trip can't see
    // that). Every field is checked against the same-named column.
    @Test
    fun `every reservation field lands in the matching entity column`() {
        val dto = sampleReservation()

        val entity = dto.toEntity(syncedAt)

        assertReservationColumns(dto, entity)
    }

    // Real-world bug caught: the "showing offline data from ..." banner reporting the wrong time
    // because the sync time wasn't stored as given.
    @Test
    fun `reservation toEntity stamps the sync time it is given`() {
        assertEquals(syncedAt, sampleReservation().toEntity(syncedAt).lastSyncedAt)
        assertEquals(42L, sampleReservation().toEntity(42L).lastSyncedAt)
    }

    // Real-world bug caught: the sync time leaking into what the screens see. It isn't part of a
    // reservation, so two rows that differ ONLY by sync time must give the same response.
    @Test
    fun `the sync time is not part of the reservation response`() {
        val older = sampleReservation().toEntity(1L)
        val newer = sampleReservation().toEntity(2L)

        assertEquals(older.toResponse(), newer.toResponse())
    }

    // ==================== STATION ====================

    // Real-world bug caught: latitude and longitude (both plain numbers) being swapped, which
    // would put every station in the wrong place on a map.
    @Test
    fun `station round trip returns an identical station`() {
        val original = sampleStation()

        assertEquals(original, original.toEntity(syncedAt).toResponse())
    }

    @Test
    fun `a station with empty optional fields round trips unchanged`() {
        val station = sampleStation(createdBy = null, updatedAt = null)

        assertEquals(station, station.toEntity(syncedAt).toResponse())
    }

    @Test
    fun `every station field lands in the matching entity column`() {
        val dto = sampleStation()

        val entity = dto.toEntity(syncedAt)

        assertStationColumns(dto, entity)
        assertEquals(syncedAt, entity.lastSyncedAt)
    }

    // ==================== PROFILE ====================

    // Real-world bug caught: the offline profile showing a blank contact number or wrong status
    // because one field was never mapped.
    @Test
    fun `profile round trip returns an identical profile`() {
        val original = sampleProsumer()

        assertEquals(original, original.toEntity(syncedAt).toResponse())
    }

    // Real-world bug caught: profiles legitimately have no contact number / address / capacity
    // (older accounts). Missing values must stay missing, not turn into "" or 0.0.
    @Test
    fun `a profile with empty optional fields round trips unchanged`() {
        val profile = sampleProsumer(
            contactNumber = null,
            address = null,
            panelCapacityKw = null,
            createdBy = null,
            updatedAt = null
        )

        val roundTripped = profile.toEntity(syncedAt).toResponse()

        assertEquals(profile, roundTripped)
        assertNull(roundTripped.contactNumber)
        assertNull(roundTripped.panelCapacityKw)
    }

    // Real-world bug caught: isActive and deactivationRequested (both true/false) swapped. The
    // Profile screen would then show "Deactivation requested" for a normal active account.
    @Test
    fun `every profile field lands in the matching entity column`() {
        val dto = sampleProsumer(isActive = true, deactivationRequested = false)

        val entity = dto.toEntity(syncedAt)

        assertProfileColumns(dto, entity)
        assertEquals(syncedAt, entity.lastSyncedAt)
    }

    @Test
    fun `a profile with a pending deactivation request keeps that flag`() {
        val requested = sampleProsumer(deactivationRequested = true)

        assertEquals(true, requested.toEntity(syncedAt).toResponse().deactivationRequested)
    }

    // ==================== DASHBOARD ====================

    // Real-world bug caught: dashboard counts landing in the wrong columns (pending shown as
    // completed...). The four counts are 2, 3, 15 and 1 so any mix-up is visible.
    @Test
    fun `dashboard counts land in the matching entity columns`() {
        val dto = sampleDashboard()

        val entity = dto.toEntity(syncedAt)

        assertEquals("pendingCount", dto.pendingCount, entity.pendingCount)
        assertEquals("approvedFutureCount", dto.approvedFutureCount, entity.approvedFutureCount)
        assertEquals("completedCount", dto.completedCount, entity.completedCount)
        assertEquals("cancelledCount", dto.cancelledCount, entity.cancelledCount)
        assertEquals(syncedAt, entity.lastSyncedAt)
    }

    // Real-world bug caught: the single-row table not using its fixed id, so every save would ADD
    // a row instead of replacing the snapshot and the "latest" one would be ambiguous.
    @Test
    fun `the dashboard snapshot always uses the single fixed row id`() {
        val entity = sampleDashboard().toEntity(syncedAt)

        assertEquals(CachedDashboardEntity.SINGLE_ROW_ID, entity.id)
    }

    // Real-world bug caught: the nested next reservation losing fields when squeezed into JSON
    // and back. The whole reservation, including its optional fields, must survive.
    @Test
    fun `dashboard round trip returns an identical dashboard including the next reservation`() {
        val original = sampleDashboard()

        val roundTripped = original.toEntity(syncedAt).toResponse()

        assertEquals(original, roundTripped)
        assertNotNull(roundTripped.nextReservation)
    }

    // Real-world bug caught: "no upcoming reservation" (null) being saved as the text "null" or an
    // empty string, and coming back as a bogus reservation or a crash.
    @Test
    fun `a dashboard with no next reservation stores none and reads back none`() {
        val original = sampleDashboard(nextReservation = null)

        val entity = original.toEntity(syncedAt)

        assertNull("nothing to store when there is no next reservation", entity.nextReservationJson)
        assertEquals(original, entity.toResponse())
        assertNull(entity.toResponse().nextReservation)
    }

    // Real-world bug caught: a next reservation whose own optional fields are empty (the usual
    // shape of an approved-but-not-yet-completed booking) failing the JSON round trip.
    @Test
    fun `a next reservation with empty optional fields survives the JSON round trip`() {
        val next = sampleReservation(
            status = "Approved",
            completedAt = null,
            completedBy = null,
            cancelledAt = null,
            cancelledBy = null,
            cancellationReason = null
        )
        val original = sampleDashboard(nextReservation = next)

        assertEquals(original, original.toEntity(syncedAt).toResponse())
    }

    // Real-world bug caught: THE deliberate "lossy" case. If the stored JSON is ever unreadable (a
    // partly written file, a schema change), the dashboard must still open with its counts and
    // simply have no next reservation — a cache must never crash the screen it is helping.
    @Test
    fun `unreadable stored JSON becomes no next reservation and keeps the counts`() {
        val counts = sampleDashboard()
        val corrupted = counts.toEntity(syncedAt).copy(nextReservationJson = "{ this is not valid json")

        val result = corrupted.toResponse()

        assertNull(result.nextReservation)
        assertEquals(counts.pendingCount, result.pendingCount)
        assertEquals(counts.approvedFutureCount, result.approvedFutureCount)
        assertEquals(counts.completedCount, result.completedCount)
        assertEquals(counts.cancelledCount, result.cancelledCount)
    }

    @Test
    fun `an empty stored JSON string also becomes no next reservation`() {
        val emptied = sampleDashboard().toEntity(syncedAt).copy(nextReservationJson = "")

        assertNull(emptied.toResponse().nextReservation)
    }

    // ==================== helpers: the column-by-column checks ====================
    // Each check names the field, so a failure reads e.g. "prosumerNic expected:<...> but was:<...>".
    // Doubles are compared with an explicit tolerance of 0.0 (JUnit refuses a bare double comparison).

    private fun assertReservationColumns(dto: ReservationResponse, e: CachedReservationEntity) {
        assertEquals("id", dto.id, e.id)
        assertEquals("prosumerNic", dto.prosumerNic, e.prosumerNic)
        assertEquals("prosumerName", dto.prosumerName, e.prosumerName)
        assertEquals("stationId", dto.stationId, e.stationId)
        assertEquals("stationName", dto.stationName, e.stationName)
        assertEquals("slotId", dto.slotId, e.slotId)
        assertEquals("slotStartTime", dto.slotStartTime, e.slotStartTime)
        assertEquals("slotEndTime", dto.slotEndTime, e.slotEndTime)
        assertEquals("capacityKw", dto.capacityKw, e.capacityKw, 0.0)
        assertEquals("status", dto.status, e.status)
        assertEquals("qrGeneratedAt", dto.qrGeneratedAt, e.qrGeneratedAt)
        assertEquals("createdAt", dto.createdAt, e.createdAt)
        assertEquals("createdBy", dto.createdBy, e.createdBy)
        assertEquals("updatedAt", dto.updatedAt, e.updatedAt)
        assertEquals("approvedAt", dto.approvedAt, e.approvedAt)
        assertEquals("approvedBy", dto.approvedBy, e.approvedBy)
        assertEquals("completedAt", dto.completedAt, e.completedAt)
        assertEquals("completedBy", dto.completedBy, e.completedBy)
        assertEquals("cancelledAt", dto.cancelledAt, e.cancelledAt)
        assertEquals("cancelledBy", dto.cancelledBy, e.cancelledBy)
        assertEquals("cancellationReason", dto.cancellationReason, e.cancellationReason)
        assertEquals("lastSyncedAt", syncedAt, e.lastSyncedAt)
    }

    private fun assertStationColumns(dto: StationResponse, e: CachedStationEntity) {
        assertEquals("id", dto.id, e.id)
        assertEquals("stationName", dto.stationName, e.stationName)
        assertEquals("latitude", dto.latitude, e.latitude, 0.0)
        assertEquals("longitude", dto.longitude, e.longitude, 0.0)
        assertEquals("capacityKw", dto.capacityKw, e.capacityKw, 0.0)
        assertEquals("availableSlots", dto.availableSlots, e.availableSlots)
        assertEquals("schedule", dto.schedule, e.schedule)
        assertEquals("isActive", dto.isActive, e.isActive)
        assertEquals("createdAt", dto.createdAt, e.createdAt)
        assertEquals("createdBy", dto.createdBy, e.createdBy)
        assertEquals("updatedAt", dto.updatedAt, e.updatedAt)
    }

    private fun assertProfileColumns(dto: ProsumerResponse, e: CachedProfileEntity) {
        assertEquals("id", dto.id, e.id)
        assertEquals("nic", dto.nic, e.nic)
        assertEquals("name", dto.name, e.name)
        assertEquals("email", dto.email, e.email)
        assertEquals("contactNumber", dto.contactNumber, e.contactNumber)
        assertEquals("address", dto.address, e.address)
        assertEquals("panelCapacityKw", dto.panelCapacityKw, e.panelCapacityKw)
        assertEquals("isActive", dto.isActive, e.isActive)
        assertEquals("deactivationRequested", dto.deactivationRequested, e.deactivationRequested)
        assertEquals("createdAt", dto.createdAt, e.createdAt)
        assertEquals("createdBy", dto.createdBy, e.createdBy)
        assertEquals("updatedAt", dto.updatedAt, e.updatedAt)
        assertEquals("status", dto.status, e.status)
    }
}
