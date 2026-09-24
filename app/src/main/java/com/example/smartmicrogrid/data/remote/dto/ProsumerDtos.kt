package com.example.smartmicrogrid.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * File: ProsumerDtos.kt
 * Purpose: DTOs for prosumer profile, self-registration, profile editing,
 *          password change, and the request-deactivation flow.
 *
 * Backend source of truth:
 * - ProsumerSelfServiceController.cs  (register, me, me/password, me/request-deactivation)
 * - ProsumersController.cs             (Backoffice-side CRUD — not used on mobile)
 * - ProsumerService.cs                 (RegisterAsync, UpdateOwnProfileAsync,
 *                                       ChangePasswordAsync, ToResponse)
 */

/**
 * Response body for:
 *   GET  /api/prosumers/me
 *   PUT  /api/prosumers/me
 *
 * Also returned (with same shape) from Backoffice endpoints, but mobile
 * only consumes the self-service ones.
 *
 * Example JSON:
 * {
 *   "id": "6512f3a4c8d9e1b2f3a4c8d9",
 *   "nic": "200012345678",
 *   "name": "Kamal Perera",
 *   "email": "kamal@example.com",
 *   "contactNumber": "0771234567",
 *   "address": "123, Galle Road, Colombo",
 *   "panelCapacityKw": 5.5,
 *   "isActive": true,
 *   "deactivationRequested": false,
 *   "createdAt": "2026-09-01T10:00:00Z",
 *   "createdBy": "self-registration",
 *   "updatedAt": "2026-09-01T10:00:00Z",
 *   "status": "active"
 * }
 *
 * IMPORTANT — status values (derived server-side, NOT stored):
 *   "active"      → isActive = true
 *   "pending"     → isActive = false, deactivationRequested = false
 *   "deactivated" → isActive = false, deactivationRequested = true
 */
data class ProsumerResponse(
    @SerializedName("id")                    val id: String,
    @SerializedName("nic")                   val nic: String,
    @SerializedName("name")                  val name: String,
    @SerializedName("email")                 val email: String,
    @SerializedName("contactNumber")         val contactNumber: String?,
    @SerializedName("address")               val address: String?,
    @SerializedName("panelCapacityKw")       val panelCapacityKw: Double?,
    @SerializedName("isActive")              val isActive: Boolean,
    @SerializedName("deactivationRequested") val deactivationRequested: Boolean,
    @SerializedName("createdAt")             val createdAt: String,
    @SerializedName("createdBy")             val createdBy: String?,
    @SerializedName("updatedAt")             val updatedAt: String?,
    @SerializedName("status")                val status: String
)

/**
 * Request body for POST /api/prosumers/register.
 *
 * Anonymous endpoint — no JWT required. After registration,
 * the account is created with isActive = false (status = "pending")
 * and must be activated by a Backoffice officer.
 *
 * Password rules are enforced server-side. Client should validate
 * basic rules (non-empty, minimum length) before submitting.
 */
data class RegisterProsumerRequest(
    @SerializedName("nic")             val nic: String,
    @SerializedName("name")            val name: String,
    @SerializedName("email")           val email: String,
    @SerializedName("contactNumber")   val contactNumber: String,
    @SerializedName("address")         val address: String,
    @SerializedName("panelCapacityKw") val panelCapacityKw: Double,
    @SerializedName("password")        val password: String
)

/**
 * Request body for PUT /api/prosumers/me.
 *
 * All fields are nullable — the backend applies only fields that are non-null.
 * To leave a field unchanged, set it to null.
 *
 * Do NOT include NIC — it's immutable and comes from the JWT server-side.
 */
data class UpdateOwnProfileRequest(
    @SerializedName("name")            val name: String? = null,
    @SerializedName("email")           val email: String? = null,
    @SerializedName("contactNumber")   val contactNumber: String? = null,
    @SerializedName("address")         val address: String? = null,
    @SerializedName("panelCapacityKw") val panelCapacityKw: Double? = null
)

/**
 * Request body for PUT /api/prosumers/me/password.
 *
 * Backend verifies currentPassword against the stored BCrypt hash,
 * then updates the hash in both the Prosumer and User collections.
 *
 * On success: 204 No Content.
 * On wrong current password: 400 Bad Request.
 */
data class ChangePasswordRequest(
    @SerializedName("currentPassword") val currentPassword: String,
    @SerializedName("newPassword")     val newPassword: String
)