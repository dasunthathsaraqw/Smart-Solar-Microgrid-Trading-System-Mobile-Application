package com.example.smartmicrogrid.data.repository

import com.example.smartmicrogrid.data.remote.dto.ApiError
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

/**
 * File: SafeApiCall.kt
 * Purpose: A single reusable wrapper for every Retrofit call that:
 *   - runs on IO dispatcher
 *   - catches network errors (IOException)
 *   - catches HTTP errors (HttpException / non-2xx)
 *   - parses the backend's inconsistent error body (ApiError)
 *   - returns a uniform ApiResult<T>
 *
 * The block is expected to return a Retrofit Response<T>.
 * For endpoints that return Unit (204 No Content), use Response<Unit>.
 */
suspend fun <T> safeApiCall(
    block: suspend () -> Response<T>
): ApiResult<T> = withContext(Dispatchers.IO) {
    try {
        val response = block()
        if (response.isSuccessful) {
            val body = response.body()
            if (body != null) {
                ApiResult.Success(body)
            } else {
                // 204 No Content or empty body — but caller expects T.
                // We treat "success with no body" as an error of type Unit at the call site.
                // If you know your endpoint returns no body, call safeApiCallUnit instead.
                @Suppress("UNCHECKED_CAST")
                ApiResult.Success(Unit as T)
            }
        } else {
            val error = parseErrorBody(response)
            ApiResult.Error(
                message = error.extractMessage(),
                code = response.code(),
                isNetworkError = false
            )
        }
    } catch (e: IOException) {
        ApiResult.Error(
            message = "Network error. Please check your connection.",
            code = null,
            isNetworkError = true
        )
    } catch (e: HttpException) {
        ApiResult.Error(
            message = e.message() ?: "Unexpected HTTP error.",
            code = e.code(),
            isNetworkError = false
        )
    } catch (e: Exception) {
        ApiResult.Error(
            message = e.message ?: "Unexpected error.",
            code = null,
            isNetworkError = false
        )
    }
}

/**
 * Variant for endpoints that return no body (e.g., 204 No Content).
 * Example: PUT /api/prosumers/me/password → 204
 */
suspend fun safeApiCallUnit(
    block: suspend () -> Response<Unit>
): ApiResult<Unit> = withContext(Dispatchers.IO) {
    try {
        val response = block()
        if (response.isSuccessful) {
            ApiResult.Success(Unit)
        } else {
            val error = parseErrorBody(response)
            ApiResult.Error(
                message = error.extractMessage(),
                code = response.code(),
                isNetworkError = false
            )
        }
    } catch (e: IOException) {
        ApiResult.Error(
            message = "Network error. Please check your connection.",
            code = null,
            isNetworkError = true
        )
    } catch (e: Exception) {
        ApiResult.Error(
            message = e.message ?: "Unexpected error.",
            code = null,
            isNetworkError = false
        )
    }
}

/**
 * Parses the errorBody of a failed Retrofit Response into an ApiError.
 * Handles the three backend shapes and falls back to a generic message.
 */
private fun <T> parseErrorBody(response: Response<T>): ApiError {
    return try {
        val raw = response.errorBody()?.string()
        if (raw.isNullOrBlank()) {
            ApiError(message = "Request failed with status ${response.code()}")
        } else {
            Gson().fromJson(raw, ApiError::class.java) ?: ApiError()
        }
    } catch (e: Exception) {
        ApiError(message = "Request failed with status ${response.code()}")
    }
}