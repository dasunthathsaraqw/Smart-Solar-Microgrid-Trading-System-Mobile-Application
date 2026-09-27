package com.example.smartmicrogrid.data.remote

import android.content.Context
import com.example.smartmicrogrid.BuildConfig
import com.example.smartmicrogrid.utils.Constants
import com.example.smartmicrogrid.utils.SessionManager
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * File: RetrofitClient.kt
 * Purpose: A singleton that builds and configures the Retrofit instance used
 *          by every repository in the app.
 *
 * Responsibilities:
 * - Build OkHttpClient with the AuthInterceptor (JWT auto-attached)
 * - Add a logging interceptor (BODY level in debug for full request/response visibility)
 * - Configure timeouts (connect/read/write) suitable for mobile networks
 * - Attach GsonConverterFactory so request/response bodies are auto-serialized
 * - Expose a single, ready-to-use ApiService instance
 *
 * Usage from any repository:
 *
 *   class AuthRepository(context: Context) {
 *       private val api = RetrofitClient.getApiService(context)
 *       ...
 *   }
 *
 * IMPORTANT — Why Context?
 *   SessionManager needs a Context to access SharedPreferences.
 *   We accept it in getApiService() and initialize once via a lazy singleton
 *   so multiple calls reuse the same Retrofit instance.
 */
object RetrofitClient {

    @Volatile
    private var retrofit: Retrofit? = null

    @Volatile
    private var apiService: ApiService? = null

    /**
     * Returns the singleton ApiService. Initializes Retrofit on first call.
     * Use applicationContext to avoid leaking an Activity.
     */
    fun getApiService(context: Context): ApiService {
        // Double-checked locking pattern for thread safety
        return apiService ?: synchronized(this) {
            apiService ?: buildApiService(context.applicationContext).also {
                apiService = it
            }
        }
    }

    /**
     * Builds the ApiService instance. Called once — all subsequent calls
     * reuse the cached instances.
     */
    private fun buildApiService(appContext: Context): ApiService {
        // ---------- 1. Logging interceptor ----------
        // BODY logs the full request/response JSON — debug builds only. Release logs nothing.
        // The Authorization header is redacted even in debug so the JWT never reaches Logcat.
        val logging = HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
            redactHeader("Authorization")
        }

        // ---------- 2. Auth interceptor ----------
        // Reads JWT from SessionManager and attaches it to every request.
        val sessionManager = SessionManager(appContext)
        val authInterceptor = AuthInterceptor(sessionManager)

        // ---------- 3. OkHttpClient ----------
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(authInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        // ---------- 4. Retrofit instance ----------
        // BASE_URL must end with '/' — it's already set that way in Constants.
        return Retrofit.Builder()
            .baseUrl(Constants.BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}