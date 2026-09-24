package com.example.smartmicrogrid.data.repository

/**
 * File: ApiResult.kt
 * Purpose: A sealed wrapper for API call outcomes so repositories and ViewModels
 *          can handle success and failure uniformly without try/catch everywhere.
 *
 * Usage pattern:
 *
 *   suspend fun login(email: String, password: String): ApiResult<LoginResponse> {
 *       return safeApiCall {
 *           api.login(LoginRequest(email, password))
 *       }
 *   }
 *
 * Then in the ViewModel:
 *
 *   when (val result = repo.login(email, password)) {
 *       is ApiResult.Success -> navigate(result.data)
 *       is ApiResult.Error   -> showError(result.message)
 *   }
 */
sealed class ApiResult<out T> {

    /**
     * The call succeeded and returned usable data.
     */
    data class Success<T>(val data: T) : ApiResult<T>()

    /**
     * The call failed. Contains a user-friendly message and optional HTTP status.
     *
     * @param message  Human-readable error text (already extracted from ApiError)
     * @param code     HTTP status code (401, 403, 409, 500, etc.) or null for network failure
     * @param isNetworkError  true if the failure was network-related (no internet, timeout)
     */
    data class Error(
        val message: String,
        val code: Int? = null,
        val isNetworkError: Boolean = false
    ) : ApiResult<Nothing>()
}