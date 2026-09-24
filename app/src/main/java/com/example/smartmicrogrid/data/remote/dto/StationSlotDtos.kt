package com.example.smartmicrogrid.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * File: StationSlotDtos.kt
 * Purpose: DTOs for stations, slots, nearby-station lookup, and both dashboards
 *          (prosumer and operator).
 *
 * Backend source of truth:
 * - StationsController.cs
 * - SlotsController.cs
 * - ReportsController.cs
 * - ProsumerReportsController.cs
 * - StationService.cs, SlotService.cs, ReportService.cs
 */

/**
 * Full station record.
 *
 * Example JSON:
 * {
 *   "id": "6512f3a4c8d9e1b2f3a4c8d9",
 *   "stationName": "Colombo North Hub",
 *   "latitude": 6.9271,
 *   "longitude": 79.8612,
 *   "capacityKw": 500.0,
 *   "availableSlots": 20,
 *   "schedule": "06:00-20:00 Mon-Sun",
 *   "isActive": true,
 *   "createdAt": "2026-09-01T10:00:00Z",
 *   "createdBy": "admin@smartsolar.com",
 *   "updatedAt": "2026-09-01T10:00:00Z"
 * }
 *
 * Notes:
 * - schedule is a STRING formatted like "HH:mm-HH:mm DAY-DAY".
 *   Example: "06:00-20:00 Mon-Sun", "09:00-17:00 Mon-Fri".
 *   Format it directly in the UI — do not parse into separate fields unless needed.
 */
data class StationResponse(
    @SerializedName("id")             val id: String,
    @SerializedName("stationName")    val stationName: String,
    @SerializedName("latitude")       val latitude: Double,
    @SerializedName("longitude")      val longitude: Double,
    @SerializedName("capacityKw")     val capacityKw: Double,
    @SerializedName("availableSlots") val availableSlots: Int,
    @SerializedName("schedule")       val schedule: String,
    @SerializedName("isActive")       val isActive: Boolean,
    @SerializedName("createdAt")      val createdAt: String,
    @SerializedName("createdBy")      val createdBy: String?,
    @SerializedName("updatedAt")      val updatedAt: String?
)

/**
 * Nearby-station record returned by GET /api/stations/nearby.
 *
 * Extends the base StationResponse with two computed values:
 *   - distanceKm         — haversine distance from the query point
 *   - availableSlotCount — count of unbooked slots in the next 7 days
 *
 * Example JSON:
 * {
 *   "id": "...",
 *   "stationName": "Colombo North Hub",
 *   "latitude": 6.9271,
 *   "longitude": 79.8612,
 *   "capacityKw": 500.0,
 *   "availableSlots": 20,
 *   "schedule": "06:00-20:00 Mon-Sun",
 *   "isActive": true,
 *   "createdAt": "...",
 *   "createdBy": "...",
 *   "updatedAt": "...",
 *   "distanceKm": 2.34,
 *   "availableSlotCount": 12
 * }
 */
data class NearbyStationResponse(
    @SerializedName("id")                 val id: String,
    @SerializedName("stationName")        val stationName: String,
    @SerializedName("latitude")           val latitude: Double,
    @SerializedName("longitude")          val longitude: Double,
    @SerializedName("capacityKw")         val capacityKw: Double,
    @SerializedName("availableSlots")     val availableSlots: Int,
    @SerializedName("schedule")           val schedule: String,
    @SerializedName("isActive")           val isActive: Boolean,
    @SerializedName("createdAt")          val createdAt: String,
    @SerializedName("createdBy")          val createdBy: String?,
    @SerializedName("updatedAt")          val updatedAt: String?,
    @SerializedName("distanceKm")         val distanceKm: Double,
    @SerializedName("availableSlotCount") val availableSlotCount: Int
)

/**
 * Full energy slot record.
 *
 * Example JSON:
 * {
 *   "id": "6512f3a4c8d9e1b2f3a4c8d9",
 *   "stationId": "6512f3a4c8d9e1b2f3a4c8da",
 *   "stationName": "Colombo North Hub",
 *   "slotDate": "2026-09-25T00:00:00Z",
 *   "startTime": "2026-09-25T14:00:00Z",
 *   "endTime": "2026-09-25T15:00:00Z",
 *   "capacityKw": 5.5,
 *   "isBooked": false,
 *   "createdAt": "2026-09-20T10:00:00Z",
 *   "createdBy": "admin@smartsolar.com",
 *   "updatedAt": "2026-09-20T10:00:00Z"
 * }
 *
 * Notes:
 * - slotDate is the calendar date (00:00 UTC) for grouping.
 * - startTime/endTime are the actual window used for booking.
 * - Booked slots cannot be deleted or modified.
 */
data class SlotResponse(
    @SerializedName("id")          val id: String,
    @SerializedName("stationId")   val stationId: String,
    @SerializedName("stationName") val stationName: String,
    @SerializedName("slotDate")    val slotDate: String,
    @SerializedName("startTime")   val startTime: String,
    @SerializedName("endTime")     val endTime: String,
    @SerializedName("capacityKw")  val capacityKw: Double,
    @SerializedName("isBooked")    val isBooked: Boolean,
    @SerializedName("createdAt")   val createdAt: String,
    @SerializedName("createdBy")   val createdBy: String?,
    @SerializedName("updatedAt")   val updatedAt: String?
)

/**
 * Response body for GET /api/reports/my-dashboard (Prosumer only).
 *
 * Example JSON:
 * {
 *   "pendingCount": 2,
 *   "approvedFutureCount": 3,
 *   "completedCount": 15,
 *   "cancelledCount": 1,
 *   "nextReservation": { ...ReservationResponse... } | null
 * }
 *
 * Notes:
 * - Counts are computed live from MongoDB on every call.
 * - nextReservation is the earliest Approved reservation with a future slot
 *   (ordered by slotStartTime ascending). Null if none.
 */
data class ProsumerDashboardResponse(
    @SerializedName("pendingCount")        val pendingCount: Int,
    @SerializedName("approvedFutureCount") val approvedFutureCount: Int,
    @SerializedName("completedCount")      val completedCount: Int,
    @SerializedName("cancelledCount")      val cancelledCount: Int,
    @SerializedName("nextReservation")     val nextReservation: ReservationResponse?
)

/**
 * Response body for GET /api/reports/operator-dashboard.
 *
 * Example JSON:
 * {
 *   "pendingToday": 3,
 *   "approvedToday": 5,
 *   "completedToday": 12,
 *   "approvedFutureCount": 8,
 *   "upcomingApproved": [ { ...ReservationResponse... }, ... ]
 * }
 *
 * Notes:
 * - "today" means current UTC day (server-side).
 * - upcomingApproved is limited to 10 items, sorted by slotStartTime ascending.
 * - GridOperators automatically scoped to their assigned station server-side.
 * - Backoffice callers may omit stationId for a system-wide view.
 */
data class OperatorDashboardResponse(
    @SerializedName("pendingToday")        val pendingToday: Int,
    @SerializedName("approvedToday")       val approvedToday: Int,
    @SerializedName("completedToday")      val completedToday: Int,
    @SerializedName("approvedFutureCount") val approvedFutureCount: Int,
    @SerializedName("upcomingApproved")    val upcomingApproved: List<ReservationResponse>
)