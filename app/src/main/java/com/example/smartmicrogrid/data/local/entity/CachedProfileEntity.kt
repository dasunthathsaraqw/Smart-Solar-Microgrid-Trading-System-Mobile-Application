package com.example.smartmicrogrid.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * File: CachedProfileEntity.kt
 * Purpose: Room row for the signed-in prosumer's own profile, so the Profile screen can still be
 *          shown when the server can't be reached.
 * Author: Mobile Team
 * Date: 2026
 *
 * Mirrors EVERY field of ProsumerResponse (lossless round trip). There is only ever one profile
 * on a device — the signed-in user's — and the cache is cleared on logout so it can never be
 * shown to someone else. Holds personal data (NIC, email, address), like the session prefs do.
 * [lastSyncedAt] is epoch milliseconds of the network fetch that wrote the row.
 */
@Entity(tableName = "cached_profile")
data class CachedProfileEntity(
    @PrimaryKey val id: String,
    val nic: String,
    val name: String,
    val email: String,
    val contactNumber: String?,
    val address: String?,
    val panelCapacityKw: Double?,
    val isActive: Boolean,
    val deactivationRequested: Boolean,
    val createdAt: String,
    val createdBy: String?,
    val updatedAt: String?,
    val status: String,
    val lastSyncedAt: Long
)
