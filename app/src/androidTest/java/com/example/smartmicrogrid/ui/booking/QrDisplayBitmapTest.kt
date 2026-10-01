package com.example.smartmicrogrid.ui.booking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.smartmicrogrid.R
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QrDisplayBitmapTest {

    @Test
    fun reservationQrDecodesBeforeAndAfterDisplayRendering() {
        // Two GUIDs without hyphens, matching the reservation QR token contract.
        val token = "f47ac10b58cc4372a5670e02b2c3d4799a3f37de5cd44378ba476b477e1377aa"
        assertEquals(64, token.length)

        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var activity: QrDisplayActivity
        instrumentation.runOnMainSync { activity = QrDisplayActivity() }
        val bitmap = requireNotNull(activity.encodeQr(token))
        assertEquals(Color.WHITE, bitmap.getPixel(0, 0))
        assertTrue((0 until bitmap.width).any { x ->
            (0 until bitmap.height).any { y -> bitmap.getPixel(x, y) == Color.BLACK }
        })
        assertEquals(token, decode(bitmap))
        assertEquals(token, decode(Bitmap.createScaledBitmap(bitmap, 256, 256, true)))

        val context = ContextThemeWrapper(
            instrumentation.targetContext,
            R.style.Theme_SmartMicrogrid
        )
        var displayed: Bitmap? = null
        instrumentation.runOnMainSync {
            val root = LayoutInflater.from(context).inflate(R.layout.activity_qr_display, null)
            root.findViewById<ScrollView>(R.id.contentScroll).visibility = View.VISIBLE
            root.findViewById<View>(R.id.progressBar).visibility = View.GONE
            val image = root.findViewById<ImageView>(R.id.ivQr)
            image.setImageBitmap(bitmap)

            val density = context.resources.displayMetrics.density
            val width = (360 * density).toInt()
            val height = (640 * density).toInt()
            root.measure(
                View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
            )
            root.layout(0, 0, width, height)
            displayed = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
            image.draw(Canvas(displayed!!))
        }
        assertEquals(token, decode(requireNotNull(displayed)))
    }

    private fun decode(bitmap: Bitmap): String {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        val source = RGBLuminanceSource(bitmap.width, bitmap.height, pixels)
        return MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(source))).text
    }
}
