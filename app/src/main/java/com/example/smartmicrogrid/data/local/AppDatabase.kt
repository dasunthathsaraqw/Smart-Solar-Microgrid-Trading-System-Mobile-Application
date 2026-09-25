package com.example.smartmicrogrid.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.smartmicrogrid.data.local.dao.DashboardDao
import com.example.smartmicrogrid.data.local.dao.ProfileDao
import com.example.smartmicrogrid.data.local.dao.ReservationDao
import com.example.smartmicrogrid.data.local.dao.StationDao
import com.example.smartmicrogrid.data.local.entity.CachedDashboardEntity
import com.example.smartmicrogrid.data.local.entity.CachedProfileEntity
import com.example.smartmicrogrid.data.local.entity.CachedReservationEntity
import com.example.smartmicrogrid.data.local.entity.CachedStationEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * File: AppDatabase.kt
 * Purpose: The app's Room (SQLite) database: an offline CACHE of read-only API data — the
 *          prosumer's reservations, the station list, the dashboard snapshot and the profile.
 * Author: Mobile Team
 * Date: 2026
 *
 * It is a cache, never the source of truth, and it holds only what the network-first repositories
 * wrote after a successful fetch. Deliberately NOT stored here: the session (JWT etc. stay in
 * SessionManager), nearby stations (location-dependent), QR tokens (time- and security-sensitive),
 * and everything operator-side (an operator must see live data). Nothing that mutates is cached.
 *
 * Because it is only a cache, a schema change may simply wipe it (fallbackToDestructiveMigration):
 * the next successful fetch refills it, so no hand-written migrations are needed.
 */
@Database(
    entities = [
        CachedReservationEntity::class,
        CachedStationEntity::class,
        CachedDashboardEntity::class,
        CachedProfileEntity::class
    ],
    version = 1,
    exportSchema = false // a disposable cache; avoids needing a schema-export path in the build
)
abstract class AppDatabase : RoomDatabase() {

    // ==================== DAOs ====================

    abstract fun reservationDao(): ReservationDao
    abstract fun stationDao(): StationDao
    abstract fun dashboardDao(): DashboardDao
    abstract fun profileDao(): ProfileDao

    // ==================== MAINTENANCE ====================

    /**
     * Empties every table. Called when the signed-in user changes or leaves (logout, expired
     * session, a new login) so one person's cached data can never be shown to another. Runs on
     * the IO dispatcher, as Room forbids this on the main thread.
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) { clearAllTables() }

    // ==================== SINGLETON ====================

    companion object {
        private const val DATABASE_NAME = "solar_microgrid_cache.db"

        @Volatile
        private var instance: AppDatabase? = null

        /** The one shared database. Uses the application context, so it never leaks an Activity. */
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: buildDatabase(context.applicationContext).also { instance = it }
            }

        private fun buildDatabase(appContext: Context): AppDatabase =
            Room.databaseBuilder(appContext, AppDatabase::class.java, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build()
    }
}
