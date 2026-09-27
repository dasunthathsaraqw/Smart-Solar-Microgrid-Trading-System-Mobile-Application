package com.example.smartmicrogrid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.example.smartmicrogrid.data.local.entity.CachedReservationEntity

/**
 * File: ReservationDao.kt
 * Purpose: Reads and writes the cached prosumer reservations.
 * Author: Mobile Team
 * Date: 2026
 *
 * Lists come back newest slot first, like the server's default ("slotTime", "desc"). Writes use
 * REPLACE, so re-saving a reservation just refreshes its row.
 *
 * The replace* functions make a successful network fetch AUTHORITATIVE for what it covered, in
 * one transaction, so the cache never keeps a row the server no longer returns for that filter
 * (a reservation that moved from Pending to Approved must not linger under Pending):
 * - replaceAll:       a fetch of ALL statuses replaces the whole table;
 * - replaceForStatus: a fetch of ONE status replaces only that status's rows.
 */
@Dao
abstract class ReservationDao {

    // ==================== READ ====================

    @Query("SELECT * FROM cached_reservations ORDER BY slotStartTime DESC")
    abstract suspend fun getAll(): List<CachedReservationEntity>

    @Query("SELECT * FROM cached_reservations WHERE status = :status ORDER BY slotStartTime DESC")
    abstract suspend fun getByStatus(status: String): List<CachedReservationEntity>

    @Query("SELECT * FROM cached_reservations WHERE id = :id LIMIT 1")
    abstract suspend fun getById(id: String): CachedReservationEntity?

    // ==================== WRITE ====================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsert(reservation: CachedReservationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun upsertAll(reservations: List<CachedReservationEntity>)

    @Query("DELETE FROM cached_reservations WHERE status = :status")
    abstract suspend fun deleteByStatus(status: String)

    @Query("DELETE FROM cached_reservations")
    abstract suspend fun clear()

    // ==================== REPLACE (TRANSACTIONS) ====================

    @Transaction
    open suspend fun replaceAll(reservations: List<CachedReservationEntity>) {
        clear()
        upsertAll(reservations)
    }

    @Transaction
    open suspend fun replaceForStatus(status: String, reservations: List<CachedReservationEntity>) {
        deleteByStatus(status)
        upsertAll(reservations)
    }
}
