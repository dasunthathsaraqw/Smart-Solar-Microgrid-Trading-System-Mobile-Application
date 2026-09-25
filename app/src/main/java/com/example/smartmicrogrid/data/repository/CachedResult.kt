package com.example.smartmicrogrid.data.repository

import kotlinx.coroutines.CancellationException

/**
 * File: CachedResult.kt
 * Purpose: The result of a READ that is backed by the offline cache: fresh from the network, or
 *          served from the cache when the network is unavailable, or failed with nothing to show.
 *          Also holds the network-first helper the cache-aware repositories share.
 * Author: Mobile Team
 * Date: 2026
 *
 * WHY A SEPARATE TYPE, NOT A NEW CASE ON ApiResult: ApiResult is consumed by every ViewModel in
 * the app, most of them cache-blind (operator screens, booking actions, auth). A third ApiResult
 * subclass would make every one of their exhaustive `when (result)` blocks fail to compile and
 * would tempt them to handle a state they can never receive. CachedResult is opt-in: only the
 * five cache-aware reads return it, and everything else keeps ApiResult untouched.
 */

// ==================== RESULT ====================

sealed class CachedResult<out T> {

    /** Loaded from the server just now (and saved to the cache). */
    data class Fresh<T>(val data: T) : CachedResult<T>()

    /**
     * The server couldn't be reached, so this is what the cache held. [lastSyncedAt] is epoch
     * milliseconds of the network fetch that wrote it — what "showing offline data from …" shows.
     */
    data class Cached<T>(val data: T, val lastSyncedAt: Long) : CachedResult<T>()

    /**
     * Nothing to show: the request failed and there was no usable cache (or the failure isn't one
     * we fall back on). [error] is the original ApiResult.Error, message and HTTP code intact, so
     * a ViewModel handles it exactly as before (including a 401 -> session expired).
     */
    data class Failed(val error: ApiResult.Error) : CachedResult<Nothing>()
}

// ==================== WHEN TO FALL BACK ====================

/**
 * Only failures that say "the server is unreachable or broken" fall back to the cache: no network
 * (or an unexpected client-side failure, code null) and server errors (5xx).
 *
 * Deliberately NOT: 401 (the session is dead — the caller must log out, not show old data),
 * 403 / 404 / 400 / 409 (the server ANSWERED and said no — e.g. the reservation no longer exists —
 * so showing a stale copy as if it were current would be misleading).
 */
fun ApiResult.Error.canFallBackToCache(): Boolean =
    isNetworkError || code == null || code >= 500

// ==================== NETWORK-FIRST ====================

/**
 * The shared pattern: try the network; on success save it and return it Fresh; on a fall-back-able
 * failure return whatever [readCache] finds as Cached; otherwise Failed.
 *
 * [save] and [readCache] run "quietly": a broken cache must never turn a good network response
 * into an error, or hide the real error behind a cache exception. [readCache] returns the data
 * with its sync time, or null when the cache has nothing.
 */
internal suspend fun <T> networkFirst(
    fetch: suspend () -> ApiResult<T>,
    save: suspend (T) -> Unit,
    readCache: suspend () -> Pair<T, Long>?
): CachedResult<T> = when (val result = fetch()) {
    is ApiResult.Success -> {
        quietly { save(result.data) }
        CachedResult.Fresh(result.data)
    }
    is ApiResult.Error -> {
        val cached = if (result.canFallBackToCache()) quietly { readCache() } else null
        if (cached != null) {
            CachedResult.Cached(cached.first, cached.second)
        } else {
            CachedResult.Failed(result)
        }
    }
}

/**
 * Runs a cache operation, returning null if it throws. Coroutine cancellation is re-thrown: it
 * must still stop the caller, not be swallowed as a "cache failure".
 */
internal suspend fun <R> quietly(block: suspend () -> R): R? =
    try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        null
    }
