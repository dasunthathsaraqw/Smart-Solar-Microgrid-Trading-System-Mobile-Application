package com.example.smartmicrogrid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * File: CachedStationEntity.kt
 * Purpose: Room row for one station, so the station list of the booking flow can still be shown
 *          when the server can't be reached.
 * Author: Mobile Team
 * Date: 2026
 *
 * Mirrors EVERY field of StationResponse (lossless round trip). Not used for the nearby-stations
 * map: those results depend on where the user is, so stale ones would be actively misleading.
 * [lastSyncedAt] is epoch milliseconds of the network fetch that wrote the row.
 */
@Entity(tableName = "cached_stations")
data class CachedStationEntity(
    @PrimaryKey val id: String,
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val capacityKw: Double,
    val availableSlots: Int,
    val schedule: String,
    val isActive: Boolean,
    val createdAt: String,
    val createdBy: String?,
    val updatedAt: String?,
    val lastSyncedAt: Long
)
