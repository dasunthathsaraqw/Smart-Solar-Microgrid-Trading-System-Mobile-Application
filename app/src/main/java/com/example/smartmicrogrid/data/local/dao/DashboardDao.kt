package com.example.smartmicrogrid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.smartmicrogrid.data.local.entity.CachedDashboardEntity

/**
 * File: DashboardDao.kt
 * Purpose: Reads and writes the cached prosumer dashboard snapshot.
 * Author: Mobile Team
 * Date: 2026
 *
 * A single-row table (see CachedDashboardEntity.SINGLE_ROW_ID): upsert always overwrites the one
 * snapshot, so there is nothing to clear before saving.
 */
@Dao
interface DashboardDao {

    // ==================== READ ====================

    @Query("SELECT * FROM cached_dashboard WHERE id = ${CachedDashboardEntity.SINGLE_ROW_ID} LIMIT 1")
    suspend fun get(): CachedDashboardEntity?

    // ==================== WRITE ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dashboard: CachedDashboardEntity)

    @Query("DELETE FROM cached_dashboard")
    suspend fun clear()
}
