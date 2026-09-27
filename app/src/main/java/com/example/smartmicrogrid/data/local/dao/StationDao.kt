package com.example.smartmicrogrid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.smartmicrogrid.data.local.entity.CachedStationEntity

/**
 * File: StationDao.kt
 * Purpose: Reads and writes the cached stations.
 * Author: Mobile Team
 * Date: 2026
 *
 * The station list is always fetched whole, so a successful fetch REPLACES the table
 * (replaceAll, one transaction): a station the server no longer returns disappears from the
 * cache too. Writes use REPLACE, so re-saving a station just refreshes its row.
 */
@Dao
abstract class StationDao {

    // ==================== READ ====================

    @Query("SELECT * FROM cached_stations ORDER BY stationName")
    abstract suspend fun getAll(): List<CachedStationEntity>

    @Query("SELECT * FROM cached_stations WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): CachedStationEntity?

    // ==================== WRITE ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(stations: List<CachedStationEntity>)

    @Query("DELETE FROM cached_stations")
    abstract suspend fun clear()

    // ==================== REPLACE (TRANSACTION) ====================

    @Transaction
    open suspend fun replaceAll(stations: List<CachedStationEntity>) {
        clear()
        upsertAll(stations)
    }
}
