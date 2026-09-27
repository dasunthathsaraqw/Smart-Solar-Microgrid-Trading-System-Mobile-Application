package com.example.smartmicrogrid.testutil

import com.example.smartmicrogrid.data.remote.dto.LoginResponse
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.RegisterProsumerRequest
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.example.smartmicrogrid.utils.Constants

/**
 * File: TestData.kt
 * Purpose: Ready-made sample objects (a reservation, a profile, a login response...) for tests, so
 *          each test only spells out the one thing it actually cares about.
 * Author: Mobile Team
 * Date: 2026
 *
 * Every parameter has a default, so `sampleReservation(status = "Pending")` gives a complete
 * reservation that differs only in its status. The default values are deliberately DISTINCT from
 * one another (every text field says what it is), so if a field ever gets copied into the wrong
 * place by mistake, an equality check fails instead of accidentally passing.
 */

// ==================== RESERVATIONS ====================

/**
 * A reservation with every field filled in. That combination (approved AND completed AND
 * cancelled at once) can't happen for real; it exists so mapping tests can prove each field is
 * carried across. Override any field with a named argument, e.g. `completedAt = null`.
 */
fun sampleReservation(
    id: String = "res-001",
    prosumerNic: String = "200012345678",
    prosumerName: String = "Kamal Perera",
    stationId: String = "station-001",
    stationName: String = "Colombo North Hub",
    slotId: String = "slot-001",
    slotStartTime: String = "2026-09-25T14:00:00Z",
    slotEndTime: String = "2026-09-25T15:00:00Z",
    capacityKw: Double = 5.5,
    status: String = "Approved",
    qrGeneratedAt: String? = "2026-09-24T10:01:00Z",
    createdAt: String = "2026-09-20T10:00:00Z",
    createdBy: String? = "created-by@example.com",
    updatedAt: String? = "2026-09-24T10:05:00Z",
    approvedAt: String? = "2026-09-24T10:00:00Z",
    approvedBy: String? = "approver@smartsolar.com",
    completedAt: String? = "2026-09-25T15:05:00Z",
    completedBy: String? = "completer@smartsolar.com",
    cancelledAt: String? = "2026-09-22T09:30:00Z",
    cancelledBy: String? = "canceller@example.com",
    cancellationReason: String? = "Change of plans"
) = ReservationResponse(
    id = id,
    prosumerNic = prosumerNic,
    prosumerName = prosumerName,
    stationId = stationId,
    stationName = stationName,
    slotId = slotId,
    slotStartTime = slotStartTime,
    slotEndTime = slotEndTime,
    capacityKw = capacityKw,
    status = status,
    qrGeneratedAt = qrGeneratedAt,
    createdAt = createdAt,
    createdBy = createdBy,
    updatedAt = updatedAt,
    approvedAt = approvedAt,
    approvedBy = approvedBy,
    completedAt = completedAt,
    completedBy = completedBy,
    cancelledAt = cancelledAt,
    cancelledBy = cancelledBy,
    cancellationReason = cancellationReason
)

fun sampleReservationAction(
    action: String = "Created",
    reservation: ReservationResponse = sampleReservation(),
    message: String = "Reservation created successfully.",
    hoursUntilSlot: Double = 52.4,
    canStillModify: Boolean = true
) = ReservationActionResponse(
    action = action,
    reservation = reservation,
    message = message,
    hoursUntilSlot = hoursUntilSlot,
    canStillModify = canStillModify
)

// ==================== STATIONS ====================

fun sampleStation(
    id: String = "station-001",
    stationName: String = "Colombo North Hub",
    latitude: Double = 6.9271,
    longitude: Double = 79.8612,
    capacityKw: Double = 500.0,
    availableSlots: Int = 20,
    schedule: String = "06:00-20:00 Mon-Sun",
    isActive: Boolean = true,
    createdAt: String = "2026-09-01T10:00:00Z",
    createdBy: String? = "admin@smartsolar.com",
    updatedAt: String? = "2026-09-02T10:00:00Z"
) = StationResponse(
    id = id,
    stationName = stationName,
    latitude = latitude,
    longitude = longitude,
    capacityKw = capacityKw,
    availableSlots = availableSlots,
    schedule = schedule,
    isActive = isActive,
    createdAt = createdAt,
    createdBy = createdBy,
    updatedAt = updatedAt
)

// ==================== DASHBOARD ====================

/** The four counts are all different numbers, so a mix-up between them shows up in a comparison. */
fun sampleDashboard(
    pendingCount: Int = 2,
    approvedFutureCount: Int = 3,
    completedCount: Int = 15,
    cancelledCount: Int = 1,
    nextReservation: ReservationResponse? = sampleReservation()
) = ProsumerDashboardResponse(
    pendingCount = pendingCount,
    approvedFutureCount = approvedFutureCount,
    completedCount = completedCount,
    cancelledCount = cancelledCount,
    nextReservation = nextReservation
)

// ==================== PROFILE ====================

fun sampleProsumer(
    id: String = "pros-001",
    nic: String = "200012345678",
    name: String = "Kamal Perera",
    email: String = "kamal@example.com",
    contactNumber: String? = "0771234567",
    address: String? = "123, Galle Road, Colombo",
    panelCapacityKw: Double? = 5.5,
    isActive: Boolean = true,
    deactivationRequested: Boolean = false,
    createdAt: String = "2026-09-01T10:00:00Z",
    createdBy: String? = "self-registration",
    updatedAt: String? = "2026-09-02T10:00:00Z",
    status: String = "active"
) = ProsumerResponse(
    id = id,
    nic = nic,
    name = name,
    email = email,
    contactNumber = contactNumber,
    address = address,
    panelCapacityKw = panelCapacityKw,
    isActive = isActive,
    deactivationRequested = deactivationRequested,
    createdAt = createdAt,
    createdBy = createdBy,
    updatedAt = updatedAt,
    status = status
)

// ==================== AUTH ====================

fun sampleLoginResponse(
    token: String = "jwt-token-123",
    name: String = "Kamal Perera",
    email: String = "kamal@example.com",
    role: String = Constants.ROLE_PROSUMER,
    stationId: String? = null,
    expiresAt: String = "2026-09-25T14:00:00Z"
) = LoginResponse(
    token = token,
    name = name,
    email = email,
    role = role,
    stationId = stationId,
    expiresAt = expiresAt
)

/** A registration form that passes every client-side check. Override one field to break one rule. */
fun validRegisterRequest(
    nic: String = "200012345678",
    name: String = "Kamal Perera",
    email: String = "kamal@example.com",
    contactNumber: String = "0771234567",
    address: String = "123, Galle Road, Colombo",
    panelCapacityKw: Double = 5.5,
    password: String = "secret1"
) = RegisterProsumerRequest(
    nic = nic,
    name = name,
    email = email,
    contactNumber = contactNumber,
    address = address,
    panelCapacityKw = panelCapacityKw,
    password = password
)
