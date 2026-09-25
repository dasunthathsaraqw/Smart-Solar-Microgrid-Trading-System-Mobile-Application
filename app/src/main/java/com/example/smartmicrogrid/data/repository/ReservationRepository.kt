package com.example.smartmicrogrid.data.repository

import android.content.Context
import com.example.smartmicrogrid.data.local.AppDatabase
import com.example.smartmicrogrid.data.local.dao.ReservationDao
import com.example.smartmicrogrid.data.local.toEntity
import com.example.smartmicrogrid.data.local.toResponse
import com.example.smartmicrogrid.data.remote.ApiService
import com.example.smartmicrogrid.data.remote.RetrofitClient
import com.example.smartmicrogrid.data.remote.dto.CancelReservationRequest
import com.example.smartmicrogrid.data.remote.dto.CreateOwnReservationRequest
import com.example.smartmicrogrid.data.remote.dto.PagedResult
import com.example.smartmicrogrid.data.remote.dto.QrTokenResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationActionResponse
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.data.remote.dto.UpdateReservationRequest
import com.example.smartmicrogrid.data.remote.dto.VerifyQrRequest

/**
 * File: ReservationRepository.kt
 * Purpose: Reservation operations. Prosumer side: create, list, detail, update (move to a new
 *          slot), cancel, and fetch the QR token. Operator side: the read-only pending approval
 *          queue and completed history, and the QR check-in (verify a scanned token, then
 *          complete it). Wraps every Retrofit call in safeApiCall so callers only ever deal with
 *          ApiResult. The server enforces every booking rule; its message comes back in
 *          ApiResult.Error.
 * Author: Mobile Team
 * Date: 2026
 *
 * OFFLINE CACHE (prosumer reads only): getMyReservations and getReservationDetail are
 * NETWORK-FIRST with the Room cache as fallback and return a CachedResult. Mutations
 * (create/update/cancel) still return ApiResult and are never cached or queued — but when one
 * succeeds, the reservation the server returns is written through to the cache so an offline
 * list never shows a booking as Pending after it was cancelled.
 * NEVER cached: the QR token (time- and security-sensitive) and every operator call (an operator
 * must act on live data).
 */
class ReservationRepository(context: Context) {

    private val api: ApiService = RetrofitClient.getApiService(context)
    private val reservationDao: ReservationDao = AppDatabase.getInstance(context).reservationDao()

    // ==================== CREATE ====================

    /**
     * POST /api/reservations/my — books [slotId] at [stationId] for the signed-in prosumer.
     * The server takes the NIC from the JWT and enforces every rule (active prosumer/station,
     * slot not booked, in the future, within 7 days); its message comes back in ApiResult.Error.
     */
    suspend fun createReservation(
        stationId: String,
        slotId: String
    ): ApiResult<ReservationActionResponse> =
        safeApiCall { api.createMyReservation(CreateOwnReservationRequest(stationId, slotId)) }
            .also { cacheResultingReservation(it) }

    // ==================== READ (CACHE-AWARE) ====================

    /**
     * GET /api/reservations/my — own reservations. [status] is one of Pending / Approved /
     * Completed / Cancelled (case-sensitive); null or blank means all.
     *
     * Network-first. A successful fetch is authoritative for what it asked for: an all-statuses
     * fetch replaces the whole cache, a single-status fetch replaces only that status's rows, so
     * the cache never keeps a reservation the server stopped returning. Offline, the same filter
     * is applied to the cache. An empty cache slice counts as "nothing cached" (-> Failed).
     */
    suspend fun getMyReservations(status: String? = null): CachedResult<List<ReservationResponse>> {
        val filter = status?.takeIf { it.isNotBlank() }
        return networkFirst(
            fetch = { safeApiCall { api.getMyReservations(filter) } },
            save = { reservations ->
                val syncedAt = System.currentTimeMillis()
                val rows = reservations.map { it.toEntity(syncedAt) }
                if (filter == null) reservationDao.replaceAll(rows)
                else reservationDao.replaceForStatus(filter, rows)
            },
            readCache = {
                val rows = if (filter == null) reservationDao.getAll() else reservationDao.getByStatus(filter)
                if (rows.isEmpty()) null else (rows.map { it.toResponse() } to rows.maxOf { it.lastSyncedAt })
            }
        )
    }

    /** GET /api/reservations/my/{id} — one own reservation. Network-first, like the list. */
    suspend fun getReservationDetail(id: String): CachedResult<ReservationResponse> =
        networkFirst(
            fetch = { safeApiCall { api.getMyReservationById(id) } },
            save = { reservationDao.upsert(it.toEntity(System.currentTimeMillis())) },
            readCache = { reservationDao.getById(id)?.let { it.toResponse() to it.lastSyncedAt } }
        )

    /** GET /api/reservations/my/{id}/qr — the QR token; only available once Approved. NEVER cached. */
    suspend fun getReservationQr(id: String): ApiResult<QrTokenResponse> =
        safeApiCall { api.getMyReservationQr(id) }

    // ==================== UPDATE ====================

    /**
     * PUT /api/reservations/my/{id} — moves a reservation to [newSlotId]. The server decides
     * whether it is allowed (status, 12-hour notice, slot availability).
     */
    suspend fun updateReservation(
        id: String,
        newSlotId: String
    ): ApiResult<ReservationActionResponse> =
        safeApiCall { api.updateMyReservation(id, UpdateReservationRequest(newSlotId)) }
            .also { cacheResultingReservation(it) }

    // ==================== CANCEL ====================

    /**
     * PUT /api/reservations/my/{id}/cancel — cancels a reservation. [reason] is optional; a
     * blank reason is sent as absent. The server enforces the 12-hour notice rule.
     */
    suspend fun cancelReservation(
        id: String,
        reason: String?
    ): ApiResult<ReservationActionResponse> =
        safeApiCall {
            api.cancelMyReservation(id, CancelReservationRequest(reason?.trim()?.takeIf { it.isNotEmpty() }))
        }.also { cacheResultingReservation(it) }

    // ==================== OPERATOR (READ-ONLY, NEVER CACHED) ====================

    /**
     * GET /api/reports/pending-approvals — the queue of reservations awaiting approval, at most
     * [count]. Read-only on mobile: approving happens in the web app (there is no approve call).
     */
    suspend fun getPendingApprovals(count: Int = 20): ApiResult<List<ReservationResponse>> =
        safeApiCall { api.getPendingApprovals(count) }

    /**
     * GET /api/reservations/operator/history — Completed reservations of [stationId], newest
     * page first as the server orders them; [page] is 1-based. [dateFrom]/[dateTo] are optional
     * ISO date filters (blank = no filter).
     */
    suspend fun getCompletedHistory(
        stationId: String,
        dateFrom: String? = null,
        dateTo: String? = null,
        page: Int = 1,
        pageSize: Int = 20
    ): ApiResult<PagedResult<ReservationResponse>> =
        safeApiCall {
            api.getOperatorHistory(
                stationId = stationId,
                dateFrom = dateFrom?.takeIf { it.isNotBlank() },
                dateTo = dateTo?.takeIf { it.isNotBlank() },
                page = page,
                pageSize = pageSize
            )
        }

    // ==================== OPERATOR QR CHECK-IN (NEVER CACHED) ====================

    /**
     * POST /api/reservations/verify-qr — DRY RUN. Checks that [qrToken] is valid for
     * [stationId] and returns the reservation it belongs to. Nothing is changed.
     */
    suspend fun verifyQr(qrToken: String, stationId: String): ApiResult<ReservationResponse> =
        safeApiCall { api.verifyQr(VerifyQrRequest(qrToken.trim(), stationId)) }

    /**
     * POST /api/reservations/scan-complete — verifies [qrToken] for [stationId] AND marks the
     * reservation Completed, atomically. This is the real charging-session completion and
     * can't be undone from the app.
     */
    suspend fun scanComplete(qrToken: String, stationId: String): ApiResult<ReservationResponse> =
        safeApiCall { api.scanComplete(VerifyQrRequest(qrToken.trim(), stationId)) }

    // ==================== INTERNAL ====================

    /**
     * Write-through: after a successful create/update/cancel, save the reservation the server
     * returned so the offline cache matches. The mutation itself is never queued or replayed.
     */
    private suspend fun cacheResultingReservation(result: ApiResult<ReservationActionResponse>) {
        if (result is ApiResult.Success) {
            quietly {
                reservationDao.upsert(result.data.reservation.toEntity(System.currentTimeMillis()))
            }
        }
    }
}
