package com.example.smartmicrogrid.data.remote

import com.example.smartmicrogrid.data.remote.dto.*
import retrofit2.Response
import retrofit2.http.*

/**
 * File: ApiService.kt
 * Purpose: Retrofit interface declaring every backend endpoint the mobile app calls.
 *
 * Conventions:
 * - Relative paths (no leading slash) — base URL ends with /
 * - suspend functions — called from coroutines
 * - Response<T> — so safeApiCall can inspect HTTP status + body
 * - @Query for URL params, @Path for path segments, @Body for JSON body
 *
 * Backend source of truth:
 * - AuthController, ProsumerSelfServiceController, ReservationsController,
 *   StationsController, SlotsController, ReportsController, ProsumerReportsController
 */
interface ApiService {

    // ========================================================
    // AUTH
    // ========================================================

    /** Authenticate with email + password. Returns JWT + identity context. */
    @POST("api/auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Response<LoginResponse>

    /** Fetch the current user's latest identity from the server. Requires JWT. */
    @GET("api/auth/me")
    suspend fun getMe(): Response<IdentityResponse>

    // ========================================================
    // PROSUMER SELF-SERVICE
    // ========================================================

    /** Register a new prosumer account (anonymous — no JWT required). */
    @POST("api/prosumers/register")
    suspend fun registerProsumer(
        @Body request: RegisterProsumerRequest
    ): Response<ProsumerResponse>

    /** Get own prosumer profile. Requires Prosumer JWT. */
    @GET("api/prosumers/me")
    suspend fun getMyProfile(): Response<ProsumerResponse>

    /** Update own prosumer profile. Requires Prosumer JWT. */
    @PUT("api/prosumers/me")
    suspend fun updateMyProfile(
        @Body request: UpdateOwnProfileRequest
    ): Response<ProsumerResponse>

    /** Change password. Returns 204 No Content on success. */
    @PUT("api/prosumers/me/password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<Unit>

    /** Request account deactivation. Returns 204 No Content on success. */
    @PUT("api/prosumers/me/request-deactivation")
    suspend fun requestDeactivation(): Response<Unit>

    // ========================================================
    // DASHBOARDS
    // ========================================================

    /** Prosumer dashboard counts + next approved reservation. */
    @GET("api/reports/my-dashboard")
    suspend fun getMyDashboard(): Response<ProsumerDashboardResponse>

    /** Operator dashboard — today's activity + upcoming approved. */
    @GET("api/reports/operator-dashboard")
    suspend fun getOperatorDashboard(): Response<OperatorDashboardResponse>

    /** Pending approval queue (management roles). */
    @GET("api/reports/pending-approvals")
    suspend fun getPendingApprovals(
        @Query("count") count: Int = 20
    ): Response<List<RecentBookingDto>>

    // ========================================================
    // RESERVATIONS — PROSUMER
    // ========================================================

    /** List own reservations, optionally filtered by status. */
    @GET("api/reservations/my")
    suspend fun getMyReservations(
        @Query("status") status: String? = null
    ): Response<List<ReservationResponse>>

    /** Get one own reservation by id. */
    @GET("api/reservations/my/{id}")
    suspend fun getMyReservationById(
        @Path("id") id: String
    ): Response<ReservationResponse>

    /** Create a new reservation. Returns action summary. */
    @POST("api/reservations/my")
    suspend fun createMyReservation(
        @Body request: CreateOwnReservationRequest
    ): Response<ReservationActionResponse>

    /** Update an existing pending reservation (move to new slot). */
    @PUT("api/reservations/my/{id}")
    suspend fun updateMyReservation(
        @Path("id") id: String,
        @Body request: UpdateReservationRequest
    ): Response<ReservationActionResponse>

    /** Cancel a pending or approved reservation. */
    @PUT("api/reservations/my/{id}/cancel")
    suspend fun cancelMyReservation(
        @Path("id") id: String,
        @Body request: CancelReservationRequest
    ): Response<ReservationActionResponse>

    /** Search own reservations with filters + pagination. */
    @POST("api/reservations/my/search")
    suspend fun searchMyReservations(
        @Body request: ReservationSearchRequest
    ): Response<PagedResult<ReservationResponse>>

    /** Get QR token for an approved reservation (returns { qrToken: "..." }). */
    @GET("api/reservations/my/{id}/qr")
    suspend fun getMyReservationQr(
        @Path("id") id: String
    ): Response<QrTokenResponse>

    // ========================================================
    // RESERVATIONS — OPERATOR
    // ========================================================

    /** Operator's completed reservation history (paginated). */
    @GET("api/reservations/operator/history")
    suspend fun getOperatorHistory(
        @Query("stationId") stationId: String? = null,
        @Query("dateFrom") dateFrom: String? = null,
        @Query("dateTo") dateTo: String? = null,
        @Query("page") page: Int = 1,
        @Query("pageSize") pageSize: Int = 10
    ): Response<PagedResult<ReservationResponse>>

    /** Verify a QR token (dry-run; does NOT mutate). */
    @POST("api/reservations/verify-qr")
    suspend fun verifyQr(
        @Body request: VerifyQrRequest
    ): Response<ReservationResponse>

    /** Verify QR AND atomically complete the transfer. */
    @POST("api/reservations/scan-complete")
    suspend fun scanComplete(
        @Body request: VerifyQrRequest
    ): Response<ReservationResponse>

    // ========================================================
    // STATIONS
    // ========================================================

    /** List all active stations. */
    @GET("api/stations")
    suspend fun getStations(
        @Query("status") status: String? = null
    ): Response<List<StationResponse>>

    /** Get one station by id. */
    @GET("api/stations/{id}")
    suspend fun getStationById(
        @Path("id") id: String
    ): Response<StationResponse>

    /** Nearby active stations within radius (distance-ordered). */
    @GET("api/stations/nearby")
    suspend fun getNearbyStations(
        @Query("latitude")  latitude: Double,
        @Query("longitude") longitude: Double,
        @Query("radiusKm")  radiusKm: Double = 10.0,
        @Query("limit")     limit: Int = 20
    ): Response<List<NearbyStationResponse>>

    // ========================================================
    // SLOTS
    // ========================================================

    /** Unbooked, future slots for a station, within the next 7 days. */
    @GET("api/slots/station/{stationId}/available")
    suspend fun getAvailableSlots(
        @Path("stationId") stationId: String
    ): Response<List<SlotResponse>>

    /** Update a slot (capacity/timing). Operator only. */
    @PUT("api/slots/{id}")
    suspend fun updateSlot(
        @Path("id") id: String,
        @Body request: UpdateSlotRequest
    ): Response<SlotResponse>
}

/**
 * Small helper DTO for the two endpoints that return { qrToken: "..." }:
 * - GET /api/reservations/my/{id}/qr (prosumer)
 * - GET /api/reservations/{id}/qr    (management, not used on mobile)
 */
data class QrTokenResponse(
    @com.google.gson.annotations.SerializedName("qrToken")
    val qrToken: String
)

/**
 * Small helper DTO for /api/reports/pending-approvals rows.
 * Mirrors the C# RecentBooking shape used by the operator dashboard.
 */
data class RecentBookingDto(
    @com.google.gson.annotations.SerializedName("id")            val id: String,
    @com.google.gson.annotations.SerializedName("prosumerNic")   val prosumerNic: String,
    @com.google.gson.annotations.SerializedName("prosumerName")  val prosumerName: String,
    @com.google.gson.annotations.SerializedName("stationName")   val stationName: String,
    @com.google.gson.annotations.SerializedName("slotStartTime") val slotStartTime: String,
    @com.google.gson.annotations.SerializedName("slotEndTime")   val slotEndTime: String,
    @com.google.gson.annotations.SerializedName("capacityKw")    val capacityKw: Double,
    @com.google.gson.annotations.SerializedName("status")        val status: String,
    @com.google.gson.annotations.SerializedName("createdAt")     val createdAt: String
)

/**
 * Request body for PUT /api/slots/{id}.
 * All fields optional — send only what you're changing.
 */
data class UpdateSlotRequest(
    @com.google.gson.annotations.SerializedName("startTime")
    val startTime: String? = null,

    @com.google.gson.annotations.SerializedName("endTime")
    val endTime: String? = null,

    @com.google.gson.annotations.SerializedName("capacityKw")
    val capacityKw: Double? = null
)