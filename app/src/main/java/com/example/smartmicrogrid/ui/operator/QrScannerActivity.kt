package com.example.smartmicrogrid.ui.operator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.data.remote.dto.ReservationResponse
import com.example.smartmicrogrid.databinding.ActivityQrScannerBinding
import com.example.smartmicrogrid.ui.common.handleSessionExpired
import com.example.smartmicrogrid.utils.DateUtils
import com.example.smartmicrogrid.viewmodel.QrScannerViewModel
import com.example.smartmicrogrid.viewmodel.ScanState
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult

/**
 * File: QrScannerActivity.kt
 * Purpose: Operator QR check-in. Scans a prosumer's QR code with ZXing's DecoratedBarcodeView,
 *          has the ViewModel VERIFY it (dry run) and shows the reservation it is for, and only
 *          when the operator taps "Complete Charge" COMPLETES it. The result — success, or the
 *          server's reason for refusing — is shown in a panel over the paused camera, with
 *          "Scan Again" to go round again.
 * Author: Mobile Team
 * Date: 2026
 *
 * The Activity owns the camera and the CAMERA permission; QrScannerViewModel owns the flow
 * (verify -> confirm -> complete) and holds the scanned token in between.
 */
class QrScannerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityQrScannerBinding
    private val viewModel by viewModels<QrScannerViewModel>()

    // A granted permission is picked up by onResume() (which runs when the prompt closes);
    // this callback only has to handle a refusal.
    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) showCameraDenied()
        }

    private val scanCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            val token = result.text
            // decodeSingle stops after one result, so an empty read has to restart it.
            if (token.isNullOrBlank()) startScanning() else viewModel.onQrScanned(token)
        }

        override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>) = Unit
    }

    // ==================== LIFECYCLE ====================

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityQrScannerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Our instruction card replaces the view's built-in status line.
        binding.barcodeView.statusView.visibility = View.GONE

        setupListeners()
        observeState()

        if (!hasCameraPermission()) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        // Also the path back from the system Settings screen after granting camera access there.
        if (hasCameraPermission()) {
            binding.permissionContainer.visibility = View.GONE
            binding.barcodeView.resume()
            if (viewModel.state.value is ScanState.Idle) startScanning()
        }
    }

    override fun onPause() {
        binding.barcodeView.pause()
        super.onPause()
    }

    // ==================== LISTENERS ====================

    private fun setupListeners() {
        binding.toolbar.setNavigationOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this) { goBack() }

        binding.btnCompleteCharge.setOnClickListener { viewModel.confirmComplete() }
        binding.btnScanAgain.setOnClickListener { viewModel.reset() }

        binding.btnGrantPermission.setOnClickListener {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
        binding.btnOpenSettings.setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    .setData(Uri.fromParts("package", packageName, null))
            )
        }
    }

    /**
     * Leaving mid-completion would cancel a request that can't be recalled and may already have
     * succeeded on the server, so Back is ignored until the outcome is in.
     */
    private fun goBack() {
        if (viewModel.state.value !is ScanState.Completing) finish()
    }

    // ==================== CAMERA ====================

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** Arms the camera for ONE code; called again for each new scan (see Idle). */
    private fun startScanning() {
        if (hasCameraPermission()) binding.barcodeView.decodeSingle(scanCallback)
    }

    private fun showCameraDenied() {
        binding.barcodeView.pause()
        // Grant while the system will still prompt; Settings once it has been refused for good.
        val canAsk = shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
        binding.btnGrantPermission.visibility = if (canAsk) View.VISIBLE else View.GONE
        binding.btnOpenSettings.visibility = if (canAsk) View.GONE else View.VISIBLE
        binding.permissionContainer.visibility = View.VISIBLE
    }

    // ==================== OBSERVERS ====================

    private fun observeState() {
        viewModel.state.observe(this) { state ->
            when (state) {
                ScanState.Idle -> {
                    showOverlay(binding.cardInstruction)
                    startScanning()
                }

                ScanState.Verifying -> showBusy(R.string.msg_verifying)

                is ScanState.Verified -> showResult(
                    titleRes = R.string.label_scan_result,
                    reservation = state.reservation,
                    warning = true,
                    canComplete = true
                )

                ScanState.Completing -> showBusy(R.string.msg_completing)

                is ScanState.Completed -> showResult(
                    titleRes = R.string.label_scan_result,
                    reservation = state.reservation,
                    message = getString(R.string.msg_scan_complete_success),
                    messageColor = R.color.success
                )

                is ScanState.Error -> handleError(state)
            }
        }
    }

    private fun handleError(state: ScanState.Error) {
        // 401 = token rejected/expired; retrying can't fix that.
        if (state.code == 401) {
            handleSessionExpired()
            return
        }

        val reservation = state.reservation
        if (reservation == null) {
            // Verify failed: show why and wait for "Scan Again" rather than re-reading the same
            // bad code in a loop.
            showResult(
                titleRes = R.string.error_verifying_qr,
                reservation = null,
                message = state.message,
                messageColor = R.color.error
            )
        } else {
            // Completion failed. Only a network failure (no HTTP code) is worth retrying; a
            // server refusal such as "already completed" is final, so no Complete button.
            showResult(
                titleRes = R.string.error_completing_scan,
                reservation = reservation,
                message = state.message,
                messageColor = R.color.error,
                canComplete = state.code == null
            )
        }
    }

    // ==================== SCREEN STATES ====================

    private fun showBusy(@StringRes messageRes: Int) {
        binding.tvBusyMessage.setText(messageRes)
        showOverlay(binding.cardBusy)
    }

    /**
     * The result panel. [reservation] null hides the details (a scan that verified to nothing);
     * [warning] is the "can't be undone" note, shown only while awaiting confirmation;
     * [message] is a success or failure line in [messageColor]; [canComplete] shows the
     * Complete Charge button. Scan Again is always there.
     */
    private fun showResult(
        @StringRes titleRes: Int,
        reservation: ReservationResponse?,
        warning: Boolean = false,
        message: String? = null,
        @ColorRes messageColor: Int = R.color.error,
        canComplete: Boolean = false
    ) {
        binding.tvResultTitle.setText(titleRes)

        binding.layoutDetails.visibility = if (reservation != null) View.VISIBLE else View.GONE
        reservation?.let { bindDetails(it) }

        binding.tvWarning.visibility = if (warning) View.VISIBLE else View.GONE

        if (message == null) {
            binding.tvResultMessage.visibility = View.GONE
        } else {
            binding.tvResultMessage.text = message
            binding.tvResultMessage.setTextColor(ContextCompat.getColor(this, messageColor))
            binding.tvResultMessage.visibility = View.VISIBLE
        }

        binding.btnCompleteCharge.visibility = if (canComplete) View.VISIBLE else View.GONE
        showOverlay(binding.resultPanel)
    }

    private fun bindDetails(reservation: ReservationResponse) {
        binding.tvProsumerName.text = reservation.prosumerName
        binding.tvProsumerNic.text = "${getString(R.string.label_nic)}: ${reservation.prosumerNic}"
        binding.tvStationName.text = reservation.stationName
        binding.tvSlotTime.text = DateUtils.formatSlotRange(
            this, reservation.slotStartTime, reservation.slotEndTime
        )
        binding.tvCapacity.text = getString(R.string.value_capacity_kw, reservation.capacityKw)
    }

    /** Shows exactly [visible] over the camera. */
    private fun showOverlay(visible: View) {
        listOf(binding.cardInstruction, binding.cardBusy, binding.resultPanel)
            .forEach { it.visibility = if (it === visible) View.VISIBLE else View.GONE }
    }
}
