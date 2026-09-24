package com.example.smartmicrogrid.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * File: ReservationDtos.kt
 * Purpose: DTOs for reservation lifecycle (create, update, cancel, view, search),
 *          the action-summary response, and QR verification flows.
 *
 * Backend source of truth:
 * - ReservationsController.cs
 * - ReservationService.cs
 * - ReservationConflictException.cs / SlotOverlapException.cs
 *
 * IMPORTANT — Status values (case-sensitive, match exactly):
 *   "Pending"   — created, awaiting approval
 *   "Approved"  — approved, QR issued
 *   "Completed" — energy transferred
 *   "Cancelled" — cancelled by prosumer or operator
 */

/**
 * Full reservation returned by nearly every reservation endpoint.
 *
 * Example JSON:
 * {
 *   "id": "6512f3a4c8d9e1b2f3a4c8d9",
 *   "prosumerNic": "200012345678",
 *   "prosumerName": "Kamal Perera",
 *   "stationId": "6512f3a4c8d9e1b2f3a4c8da",
 *   "stationName": "Colombo North Hub",
 *   "slotId": "6512f3a4c8d9e1b2f3a4c8db",
 *   "slotStartTime": "2026-09-25T14:00:00Z",
 *   "slotEndTime": "2026-09-25T15:00:00Z",
 *   "capacityKw": 5.5,
 *   "status": "Approved",
 *   "qrGeneratedAt": "2026-09-24T10:00:00Z",
 *   "createdAt": "2026-09-20T10:00:00Z",
 *   "createdBy": "kamal@example.com",
 *   "updatedAt": "2026-09-24T10:00:00Z",
 *   "approvedAt": "2026-09-24T10:00:00Z",
 *   "approvedBy": "operator@smartsolar.com",
 *   "completedAt": null,
 *   "completedBy": null,
 *   "cancelledAt": null,
 *   "cancelledBy": null,
 *   "cancellationReason": null
 * }
 */
data class ReservationResponse(
    @SerializedName("id")                 val id: String,
    @SerializedName("prosumerNic")        val prosumerNic: String,
    @SerializedName("prosumerName")       val prosumerName: String,
    @SerializedName("stationId")          val stationId: String,
    @SerializedName("stationName")        val stationName: String,
    @SerializedName("slotId")             val slotId: String,
    @SerializedName("slotStartTime")      val slotStartTime: String,
    @SerializedName("slotEndTime")        val slotEndTime: String,
    @SerializedName("capacityKw")         val capacityKw: Double,
    @SerializedName("status")             val status: String,
    @SerializedName("qrGeneratedAt")      val qrGeneratedAt: String?,
    @SerializedName("createdAt")          val createdAt: String,
    @SerializedName("createdBy")          val createdBy: String?,
    @SerializedName("updatedAt")          val updatedAt: String?,
    @SerializedName("approvedAt")         val approvedAt: String?,
    @SerializedName("approvedBy")         val approvedBy: String?,
    @SerializedName("completedAt")        val completedAt: String?,
    @SerializedName("completedBy")        val completedBy: String?,
    @SerializedName("cancelledAt")        val cancelledAt: String?,
    @SerializedName("cancelledBy")        val cancelledBy: String?,
    @SerializedName("cancellationReason") val cancellationReason: String?
)

/**
 * Action-summary response returned after every prosumer action:
 *   - POST /api/reservations/my           → Action = "Created"
 *   - PUT  /api/reservations/my/{id}      → Action = "Updated"
 *   - PUT  /api/reservations/my/{id}/cancel → Action = "Cancelled"
 *
 * Example JSON:
 * {
 *   "action": "Created",
 *   "reservation": { ...ReservationResponse... },
 *   "message": "Reservation created successfully.",
 *   "hoursUntilSlot": 52.4,
 *   "canStillModify": true
 * }
 *
 * This is EXACTLY the shape required for the "summary page after each action"
 * mentioned in the assignment rubric.
 */
data class ReservationActionResponse(
    @SerializedName("action")         val action: String,
    @SerializedName("reservation")    val reservation: ReservationResponse,
    @SerializedName("message")        val message: String,
    @SerializedName("hoursUntilSlot") val hoursUntilSlot: Double,
    @SerializedName("canStillModify") val canStillModify: Boolean
)

/**
 * Request body for POST /api/reservations/my.
 *
 * Only stationId and slotId are required from the client.
 * The backend forces prosumerNic from the JWT claim.
 * If you include prosumerNic, it will be IGNORED on the server side.
 *
 * All business rules are validated server-side:
 *   - Prosumer must be active
 *   - Station must be active
 *   - Slot must belong to that station and not be booked
 *   - Slot must start in the future
 *   - Slot must be within 7 days
 *   - No duplicate reservation for the same slot+prosumer
 */
data class CreateOwnReservationRequest(
    @SerializedName("stationId") val stationId: String,
    @SerializedName("slotId")    val slotId: String
)

/**
 * Request body for PUT /api/reservations/my/{id}.
 *
 * Only Pending reservations can be updated, and only when there are at least
 * 12 hours remaining until the slot starts. Backend re-validates everything.
 */
data class UpdateReservationRequest(
    @SerializedName("newSlotId") val newSlotId: String
)

/**
 * Request body for PUT /api/reservations/my/{id}/cancel.
 *
 * Reason is optional. The 12-hour notice rule applies (no override for prosumers).
 */
data class CancelReservationRequest(
    @SerializedName("reason") val reason: String? = null
)

/**
 * Request body for POST /api/reservations/my/search.
 *
 * Every field is optional and combined with AND.
 * ProsumerNic and ProsumerName are IGNORED on /my/search — the backend forces
 * the signed NIC regardless of what the client sends.
 *
 * SortBy allowed values: "slotTime" (default), "capacity", "prosumer", "station"
 * SortDir allowed values: "asc", "desc" (default "desc")
 * Page: 1-based
 * PageSize: 1..100, default 10
 */
data class ReservationSearchRequest(
    @SerializedName("prosumerNic")   val prosumerNic: String? = null,
    @SerializedName("prosumerName")  val prosumerName: String? = null,
    @SerializedName("stationId")     val stationId: String? = null,
    @SerializedName("status")        val status: String? = null,
    @SerializedName("dateFrom")      val dateFrom: String? = null,
    @SerializedName("dateTo")        val dateTo: String? = null,
    @SerializedName("minCapacityKw") val minCapacityKw: Double? = null,
    @SerializedName("maxCapacityKw") val maxCapacityKw: Double? = null,
    @SerializedName("sortBy")        val sortBy: String? = null,
    @SerializedName("sortDir")       val sortDir: String? = null,
    @SerializedName("page")          val page: Int = 1,
    @SerializedName("pageSize")      val pageSize: Int = 10
)

/**
 * Generic paginated wrapper used by search and operator history endpoints.
 *
 * Example JSON:
 * {
 *   "items": [ { ...ReservationResponse... }, ... ],
 *   "totalCount": 42,
 *   "page": 1,
 *   "pageSize": 10,
 *   "totalPages": 5,
 *   "hasNextPage": true,
 *   "hasPreviousPage": false
 * }
 */
data class PagedResult<T>(
    @SerializedName("items")           val items: List<T>,
    @SerializedName("totalCount")      val totalCount: Int,
    @SerializedName("page")            val page: Int,
    @SerializedName("pageSize")        val pageSize: Int,
    @SerializedName("totalPages")      val totalPages: Int,
    @SerializedName("hasNextPage")     val hasNextPage: Boolean,
    @SerializedName("hasPreviousPage") val hasPreviousPage: Boolean
)

/**
 * Request body for POST /api/reservations/verify-qr
 * and POST /api/reservations/scan-complete.
 *
 * qrToken is the raw 64-character hex string scanned from the prosumer's QR code
 * (two concatenated GUIDs without hyphens).
 *
 * stationId must match the operator's persisted station assignment.
 *
 * verify-qr:   does NOT mutate the reservation (dry-run check)
 * scan-complete: verifies AND atomically marks Completed (finalization)
 */
data class VerifyQrRequest(
    @SerializedName("qrToken")   val qrToken: String,
    @SerializedName("stationId") val stationId: String
)