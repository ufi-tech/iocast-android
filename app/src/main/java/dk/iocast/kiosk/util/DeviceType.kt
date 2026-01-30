package dk.iocast.kiosk.util

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.util.DisplayMetrics

/**
 * Utility to detect device type (TV, tablet, or phone)
 * Used to adapt UI for different form factors
 */
object DeviceType {

    enum class Type {
        TV,         // Android TV - has d-pad, no touch
        TABLET,     // Large screen tablet - has touch
        PHONE       // Phone - has touch, small screen
    }

    /**
     * Detect if this is an Android TV device
     */
    fun isTV(context: Context): Boolean {
        // Check UI mode first (most reliable)
        val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
        if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) {
            return true
        }

        // Check for Leanback feature (Android TV specific)
        if (context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            context.packageManager.hasSystemFeature("android.software.leanback")) {
            return true
        }

        // Check for touchscreen absence (TVs typically don't have touch)
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
            return true
        }

        // Check common TV manufacturers/models
        val manufacturer = Build.MANUFACTURER.lowercase()
        val model = Build.MODEL.lowercase()
        val tvKeywords = listOf("tv", "box", "stick", "chromecast", "firetv", "shield", "mibox", "nexus player")
        if (tvKeywords.any { manufacturer.contains(it) || model.contains(it) }) {
            return true
        }

        return false
    }

    /**
     * Detect if this is a tablet (large screen with touch)
     */
    fun isTablet(context: Context): Boolean {
        // If it's a TV, it's not a tablet
        if (isTV(context)) return false

        // Check screen size - tablets are typically 7"+ (xlarge or large)
        val screenLayout = context.resources.configuration.screenLayout and Configuration.SCREENLAYOUT_SIZE_MASK
        if (screenLayout >= Configuration.SCREENLAYOUT_SIZE_LARGE) {
            return true
        }

        // Alternative: Check smallest screen width (600dp+ is typically tablet)
        val metrics = context.resources.displayMetrics
        val smallestWidthDp = minOf(
            metrics.widthPixels / metrics.density,
            metrics.heightPixels / metrics.density
        )
        if (smallestWidthDp >= 600) {
            return true
        }

        return false
    }

    /**
     * Get the device type
     */
    fun getType(context: Context): Type {
        return when {
            isTV(context) -> Type.TV
            isTablet(context) -> Type.TABLET
            else -> Type.PHONE
        }
    }

    /**
     * Check if device has touchscreen
     */
    fun hasTouchscreen(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }

    /**
     * Check if device likely has a physical/IR remote
     */
    fun hasRemote(context: Context): Boolean {
        return isTV(context) || !hasTouchscreen(context)
    }

    /**
     * Get a human-readable description of the device type
     */
    fun getDescription(context: Context): String {
        return when (getType(context)) {
            Type.TV -> "Android TV"
            Type.TABLET -> "Tablet"
            Type.PHONE -> "Phone"
        }
    }
}
