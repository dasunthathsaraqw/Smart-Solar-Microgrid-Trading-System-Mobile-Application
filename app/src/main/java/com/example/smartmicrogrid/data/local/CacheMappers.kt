package com.example.smartmicrogrid.data.local

import com.example.smartmicrogrid.data.local.entity.CachedDashboardEntity
import com.example.smartmicrogrid.data.local.entity.CachedProfileEntity
import com.example.smartmicrogrid.data.local.entity.CachedReservationEntity
import com.example.smartmicrogrid.data.local.entity.CachedStationEntity
import com.example.smartmicrogrid.data.remote.dto.ProsumerDashboardResponse
import com.example.smartmicrogrid.data.remote.dto.ProsumerResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.remote.dto.StationResponse
import com.google.gson.Gson

/**
 * File: CacheMappers.kt
 * Purpose: Converts between the network DTOs and the Room cache entities, in both directions, so
 *          the repositories can save a fresh response and rebuild the same response from the
 *          cache when the network is unavailable.
 * Author: Mobile Team
 * Date: 2026
 *
 * LOSSLESS: the reservation, station and profile entities carry every DTO field, so reading a
 * row back gives exactly the response that was saved — nothing is dropped or defaulted, and the
 * UI needs no "cached" variants of its models. The only exceptions are the dashboard's nested
 * next reservation (stored as JSON, and null if that JSON can't be read back) and the extra
 * lastSyncedAt column, which has no DTO counterpart.
 *
 * toEntity() takes the sync time as a parameter so a whole list can be stamped with one moment
 * (call System.currentTimeMillis() once, pass it to every row).
 */

// ==================== JSON (dashboard's nested reservation) ====================

private val gson = Gson()

// ==================== RESERVATION ====================

fun ReservationResponse.toEntity(syncedAt: Long): CachedReservationEntity =
    CachedReservationEntity(
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
        cancellationReason = cancellationReason,
        lastSyncedAt = syncedAt
    )

fun CachedReservationEntity.toResponse(): ReservationResponse =
    ReservationResponse(
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

// ==================== STATION ====================

fun StationResponse.toEntity(syncedAt: Long): CachedStationEntity =
    CachedStationEntity(
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
        updatedAt = updatedAt,
        lastSyncedAt = syncedAt
    )

fun CachedStationEntity.toResponse(): StationResponse =
    StationResponse(
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

/** The four counts as columns; the next reservation (if any) as one JSON string. */
fun ProsumerDashboardResponse.toEntity(syncedAt: Long): CachedDashboardEntity =
    CachedDashboardEntity(
        pendingCount = pendingCount,
        approvedFutureCount = approvedFutureCount,
        completedCount = completedCount,
        cancelledCount = cancelledCount,
        nextReservationJson = nextReservation?.let { gson.toJson(it) },
        lastSyncedAt = syncedAt
    )

/**
 * Rebuilds the dashboard. If the stored next-reservation JSON can't be read (it never should
 * fail — we wrote it — but a cache must not crash the screen), the counts are still returned and
 * the next reservation is simply absent.
 */
fun CachedDashboardEntity.toResponse(): ProsumerDashboardResponse =
    ProsumerDashboardResponse(
        pendingCount = pendingCount,
        approvedFutureCount = approvedFutureCount,
        completedCount = completedCount,
        cancelledCount = cancelledCount,
        nextReservation = nextReservationJson?.let {
            runCatching { gson.fromJson(it, ReservationResponse::class.java) }.getOrNull()
        }
    )

// ==================== PROFILE ====================

fun ProsumerResponse.toEntity(syncedAt: Long): CachedProfileEntity =
    CachedProfileEntity(
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
        status = status,
        lastSyncedAt = syncedAt
    )

fun CachedProfileEntity.toResponse(): ProsumerResponse =
    ProsumerResponse(
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
