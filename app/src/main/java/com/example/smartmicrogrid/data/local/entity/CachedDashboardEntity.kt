package com.example.smartmicrogrid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * File: CachedDashboardEntity.kt
 * Purpose: Room snapshot of the prosumer dashboard (status counts and the next reservation), so
 *          the dashboard can still be shown when the server can't be reached.
 * Author: Mobile Team
 * Date: 2026
 *
 * A SINGLE-ROW table: [id] is always [SINGLE_ROW_ID], so saving a new snapshot replaces the old
 * one. The nested next reservation is stored as one JSON string ([nextReservationJson], null when
 * there is none) rather than as twenty extra columns: it is only ever read back whole, as part of
 * this snapshot. CacheMappers does the (de)serialisation with Gson.
 * [lastSyncedAt] is epoch milliseconds of the network fetch that wrote the row.
 */
@Entity(tableName = "cached_dashboard")
data class CachedDashboardEntity(
    @PrimaryKey val id: Int = SINGLE_ROW_ID,
    val pendingCount: Int,
    val approvedFutureCount: Int,
    val completedCount: Int,
    val cancelledCount: Int,
    val nextReservationJson: String?,
    val lastSyncedAt: Long
) {
    companion object {
        const val SINGLE_ROW_ID = 1
    }
}
