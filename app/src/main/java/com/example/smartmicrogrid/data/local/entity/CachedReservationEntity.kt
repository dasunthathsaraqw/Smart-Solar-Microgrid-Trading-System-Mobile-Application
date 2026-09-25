package com.example.smartmicrogrid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * File: CachedReservationEntity.kt
 * Purpose: Room row for one of the prosumer's reservations, so the My Bookings list and the
 *          booking detail can still be shown when the server can't be reached.
 * Author: Mobile Team
 * Date: 2026
 *
 * Mirrors EVERY field of ReservationResponse, so a cached reservation can be turned back into a
 * complete response without dropping or defaulting anything (see CacheMappers). Only the last
 * column is extra.
 *
 * [lastSyncedAt] is epoch milliseconds (System.currentTimeMillis()) of the network fetch that
 * wrote the row — a plain number, so there is no date parsing or time-zone question, and the
 * "showing offline data from …" banner formats it for display.
 */
@Entity(tableName = "cached_reservations")
data class CachedReservationEntity(
    @PrimaryKey val id: String,
    val prosumerNic: String,
    val prosumerName: String,
    val stationId: String,
    val stationName: String,
    val slotId: String,
    val slotStartTime: String,
    val slotEndTime: String,
    val capacityKw: Double,
    val status: String,
    val qrGeneratedAt: String?,
    val createdAt: String,
    val createdBy: String?,
    val updatedAt: String?,
    val approvedAt: String?,
    val approvedBy: String?,
    val completedAt: String?,
    val completedBy: String?,
    val cancelledAt: String?,
    val cancelledBy: String?,
    val cancellationReason: String?,
    val lastSyncedAt: Long
)
