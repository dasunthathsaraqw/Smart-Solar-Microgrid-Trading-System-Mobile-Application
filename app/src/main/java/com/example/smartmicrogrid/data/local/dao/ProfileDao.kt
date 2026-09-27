package com.example.smartmicrogrid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.smartmicrogrid.data.local.entity.CachedProfileEntity

/**
 * File: ProfileDao.kt
 * Purpose: Reads and writes the cached profile of the signed-in prosumer.
 * Author: Mobile Team
 * Date: 2026
 *
 * There is at most one profile on a device. The plain upsert only refreshes a row with the same
 * id; [replace] (clear, then insert, in one transaction) is what the repository uses, so the
 * table can never hold two people's profiles.
 */
@Dao
abstract class ProfileDao {

    // ==================== READ ====================

    @Query("SELECT * FROM cached_profile LIMIT 1")
    abstract suspend fun get(): CachedProfileEntity?

    // ==================== WRITE ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(profile: CachedProfileEntity)

    @Query("DELETE FROM cached_profile")
    abstract suspend fun clear()

    // ==================== REPLACE (TRANSACTION) ====================

    @Transaction
    open suspend fun replace(profile: CachedProfileEntity) {
        clear()
        upsert(profile)
    }
}
