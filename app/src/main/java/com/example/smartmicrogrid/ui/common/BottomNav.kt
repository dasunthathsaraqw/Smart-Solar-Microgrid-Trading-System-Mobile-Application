package com.example.smartmicrogrid.ui.common

import android.app.Activity
import android.content.Intent
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.annotation.IdRes
import androidx.annotation.MenuRes
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.ui.booking.MyBookingsActivity
import com.example.smartmicrogrid.ui.maps.NearbyStationsActivity
import com.example.smartmicrogrid.ui.operator.CompletedHistoryActivity
import com.example.smartmicrogrid.ui.operator.OperatorHomeActivity
import com.example.smartmicrogrid.ui.operator.PendingApprovalsActivity
import com.example.smartmicrogrid.ui.operator.SlotManagementActivity
import com.example.smartmicrogrid.ui.profile.ProfileActivity
import com.example.smartmicrogrid.ui.prosumer.ProsumerHomeActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

/**
 * File: BottomNav.kt
 * Purpose: Adds the bottom navigation bar to a top-level screen without touching its layout. The
 *          screen's content is wrapped in a vertical LinearLayout with the bar underneath. Home is
 *          the task root, so the Home tab clears back to it; any other tab replaces the current
 *          tab screen, so Back from a tab always returns to Home.
 * Author: Mobile Team
 * Date: 2026
 */

private val PROSUMER_DESTINATIONS = mapOf(
    R.id.nav_home to ProsumerHomeActivity::class.java,
    R.id.nav_bookings to MyBookingsActivity::class.java,
    R.id.nav_nearby to NearbyStationsActivity::class.java,
    R.id.nav_profile to ProfileActivity::class.java
)

private val OPERATOR_DESTINATIONS = mapOf(
    R.id.nav_home to OperatorHomeActivity::class.java,
    R.id.nav_pending to PendingApprovalsActivity::class.java,
    R.id.nav_history to CompletedHistoryActivity::class.java,
    R.id.nav_slots to SlotManagementActivity::class.java
)

/** Call right after setContentView(). [selectedId] is this screen's own tab. */
fun Activity.attachProsumerBottomNav(@IdRes selectedId: Int) =
    attachBottomNav(R.menu.menu_bottom_nav_prosumer, PROSUMER_DESTINATIONS, selectedId)

/** Call right after setContentView(). [selectedId] is this screen's own tab. */
fun Activity.attachOperatorBottomNav(@IdRes selectedId: Int) =
    attachBottomNav(R.menu.menu_bottom_nav_operator, OPERATOR_DESTINATIONS, selectedId)

private fun Activity.attachBottomNav(
    @MenuRes menuRes: Int,
    destinations: Map<Int, Class<out Activity>>,
    @IdRes selectedId: Int
) {
    val contentRoot = findViewById<ViewGroup>(android.R.id.content)
    val content = contentRoot.getChildAt(0)
    contentRoot.removeView(content)

    val bar = BottomNavigationView(this).apply { inflateMenu(menuRes) }

    contentRoot.addView(
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                content,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(
                bar,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        },
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    )

    bar.selectedItemId = selectedId
    bar.setOnItemSelectedListener { item ->
        val target = destinations[item.itemId]
        if (target != null && item.itemId != selectedId) {
            val intent = Intent(this, target)
            if (item.itemId == R.id.nav_home) {
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            } else if (selectedId != R.id.nav_home) {
                // Tab to tab: replace this screen rather than stacking tabs.
                finish()
            }
            startActivity(intent)
            overridePendingTransition(0, 0)
        }
        // Keep the highlight where it is; the new screen shows its own selection.
        false
    }
}
