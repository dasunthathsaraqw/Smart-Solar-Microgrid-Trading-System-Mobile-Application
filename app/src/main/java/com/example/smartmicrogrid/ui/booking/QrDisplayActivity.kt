package com.example.smartmicrogrid.ui.booking

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.databinding.ActivityQrDisplayBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.viewmodel.QrDisplayViewModel
import com.example.smartmicrogrid.viewmodel.QrState
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * File: QrDisplayActivity.kt
 * Purpose: Fetches the QR token of an Approved reservation (GET /api/reservations/my/{id}/qr)
 *          and shows it as a scannable QR code for the station operator.
 * Author: Mobile Team
 * Date: 2026
 */
class QrDisplayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrDisplayBinding
    private val viewModel by viewModels<QrDisplayViewModel>()

    private lateinit var reservationId: String

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Without an id there is nothing to show — bail out rather than call the API.
        val id = intent.getStringExtra(EXTRA_RESERVATION_ID)
        if (id.isNullOrBlank()) {
            finish()
            return
        }
        reservationId = id

        binding = ActivityQrDisplayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnRetry.setOnClickListener { viewModel.loadQr(reservationId) }
        showReservationInfo()

        observeState()

        // Fetch only when the ViewModel has nothing yet (rotation replays its last state).
        if (viewModel.state.value == null) {
            viewModel.loadQr(reservationId)
        }
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                QrState.Loading -> showOnly(binding.progressBar)

                is QrState.Success -> renderQr(state.qrToken)

                is QrState.Error -> {
                    // 401 = token rejected/expired; retrying can't fix that.
                    if (state.code == 401) {
                        handleSessionExpired()
                    } else {
                        // Includes the server's reason if the reservation isn't Approved.
                        showError(state.message)
                    }
                }
            }
        }
    }

    // ==================== QR ====================

    /** Encodes [token] off the main thread, then shows it; an encoding failure shows the error. */
    private fun renderQr(token: String) {
        showOnly(binding.progressBar)
        lifecycleScope.launch {
            val bitmap = withContext(Dispatchers.Default) { encodeQr(token) }
            if (bitmap != null) {
                binding.ivQr.setImageBitmap(bitmap)
                showOnly(binding.contentScroll)
            } else {
                showError(getString(R.string.error_loading_qr))
            }
        }
    }

    /**
     * Renders [token] as a black-on-white QR bitmap. The size is larger than the on-screen
     * tile so it stays sharp when the ImageView scales it. Returns null if encoding fails.
     */
    private fun encodeQr(token: String): Bitmap? = try {
        BarcodeEncoder().encodeBitmap(token, BarcodeFormat.QR_CODE, QR_SIZE_PX, QR_SIZE_PX)
    } catch (e: Exception) {
        null
    }

    // ==================== HELPERS ====================

    /** Station and slot time come from the detail screen; the QR endpoint returns only a token. */
    private fun showReservationInfo() {
        binding.tvStationName.text = intent.getStringExtra(EXTRA_STATION_NAME).orEmpty()
        binding.tvSlotTime.text = DateUtils.formatSlotRange(
            this,
            intent.getStringExtra(EXTRA_SLOT_START),
            intent.getStringExtra(EXTRA_SLOT_END)
        )
    }

    private fun showError(message: String) {
        binding.tvErrorMessage.text = message
        showOnly(binding.errorContainer)
    }

    /** Shows exactly one of: content, spinner, error block. */
    private fun showOnly(visible: View) {
        listOf(binding.contentScroll, binding.progressBar, binding.errorContainer)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }

    // ==================== INTENT ====================

    companion object {
        private const val QR_SIZE_PX = 800

        private const val EXTRA_RESERVATION_ID = "extra_reservation_id"
        private const val EXTRA_STATION_NAME = "extra_station_name"
        private const val EXTRA_SLOT_START = "extra_slot_start"
        private const val EXTRA_SLOT_END = "extra_slot_end"

        /** Slot times are the backend's raw ISO strings, formatted on this screen. */
        fun newIntent(
            context: Context,
            reservationId: String,
            stationName: String,
            slotStartIso: String,
            slotEndIso: String
        ): Intent = Intent(context, QrDisplayActivity::class.java)
            .putExtra(EXTRA_RESERVATION_ID, reservationId)
            .putExtra(EXTRA_STATION_NAME, stationName)
            .putExtra(EXTRA_SLOT_START, slotStartIso)
            .putExtra(EXTRA_SLOT_END, slotEndIso)
    }
}
