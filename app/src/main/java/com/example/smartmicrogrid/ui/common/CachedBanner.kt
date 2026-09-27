package com.example.smartmicrogrid.ui.common

import android.view.View
import com.example.smartmicrogrid.R
import com.example.smartmicrogrid.databinding.ViewCachedBannerBinding
import com.example.smartmicrogrid.utils.DateUtils

/**
 * File: CachedBanner.kt
 * Purpose: One line that every cache-aware screen uses to show or hide the "Showing offline data
 *          from <time>" banner (view_cached_banner.xml), driven straight from a state's
 *          lastSyncedAt.
 * Author: Mobile Team
 * Date: 2026
 */

// ==================== CACHED BANNER ====================

/**
 * Shows the banner with the sync time when [lastSyncedAt] is non-null (the screen is displaying
 * cached data), and hides it when it is null (fresh data, or nothing on screen to describe).
 * Call it with every state, so a later fresh result clears the banner by itself.
 */
fun ViewCachedBannerBinding.showIfCached(lastSyncedAt: Long?) {
    if (lastSyncedAt == null) {
        root.visibility = View.GONE
        return
    }
    root.text = root.context.getString(
        R.string.label_showing_cached_data,
        DateUtils.formatMillisForDisplay(lastSyncedAt)
    )
    root.visibility = View.VISIBLE
}
