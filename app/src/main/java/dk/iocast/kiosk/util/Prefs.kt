package dk.iocast.kiosk.util

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * SharedPreferences wrapper for IOCast settings
 */
class Prefs(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("iocast_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_SETUP_COMPLETE = "setup_complete"
        private const val KEY_BROKER_URL = "broker_url"
        private const val KEY_USERNAME = "username"
        private const val KEY_PASSWORD = "password"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_START_URL = "start_url"
        private const val KEY_CURRENT_URL = "current_url"
        private const val KEY_KIOSK_MODE = "kiosk_mode"
        private const val KEY_SCREEN_ON = "screen_on"
        private const val KEY_PENDING_UPDATE_DOWNLOAD_ID = "pending_update_download_id"
        // New settings for extended commands
        private const val KEY_SCREEN_ORIENTATION = "screen_orientation"
        private const val KEY_TIMEZONE = "timezone"
        private const val KEY_LOCALE = "locale"
        private const val KEY_SCHEDULED_REBOOT_ENABLED = "scheduled_reboot_enabled"
        private const val KEY_SCHEDULED_REBOOT_HOUR = "scheduled_reboot_hour"
        private const val KEY_SCHEDULED_REBOOT_MINUTE = "scheduled_reboot_minute"
        private const val KEY_DISPLAY_SCHEDULE_ENABLED = "display_schedule_enabled"
        private const val KEY_DISPLAY_ON_HOUR = "display_on_hour"
        private const val KEY_DISPLAY_ON_MINUTE = "display_on_minute"
        private const val KEY_DISPLAY_OFF_HOUR = "display_off_hour"
        private const val KEY_DISPLAY_OFF_MINUTE = "display_off_minute"
    }

    var isSetupComplete: Boolean
        get() = prefs.getBoolean(KEY_SETUP_COMPLETE, false)
        set(value) = prefs.edit().putBoolean(KEY_SETUP_COMPLETE, value).apply()

    // Alias for isSetupComplete (used by BootReceiver)
    var isConfigured: Boolean
        get() = isSetupComplete
        set(value) { isSetupComplete = value }

    var brokerUrl: String
        get() = prefs.getString(KEY_BROKER_URL, "") ?: ""
        set(value) = prefs.edit().putString(KEY_BROKER_URL, value).apply()

    var username: String
        get() = prefs.getString(KEY_USERNAME, "") ?: ""
        set(value) = prefs.edit().putString(KEY_USERNAME, value).apply()

    // Alias for username (used by SetupActivity)
    var mqttUsername: String
        get() = username
        set(value) { username = value }

    var password: String
        get() = prefs.getString(KEY_PASSWORD, "") ?: ""
        set(value) = prefs.edit().putString(KEY_PASSWORD, value).apply()

    // Alias for password (used by SetupActivity)
    var mqttPassword: String
        get() = password
        set(value) { password = value }

    var deviceId: String
        get() {
            var id = prefs.getString(KEY_DEVICE_ID, null)
            if (id.isNullOrEmpty()) {
                id = generateDeviceId()
                deviceId = id
            }
            return id
        }
        set(value) = prefs.edit().putString(KEY_DEVICE_ID, value).apply()

    var startUrl: String
        get() = prefs.getString(KEY_START_URL, "https://iocast.dk") ?: "https://iocast.dk"
        set(value) = prefs.edit().putString(KEY_START_URL, value).apply()

    var currentUrl: String
        get() = prefs.getString(KEY_CURRENT_URL, startUrl) ?: startUrl
        set(value) = prefs.edit().putString(KEY_CURRENT_URL, value).apply()

    var kioskMode: Boolean
        get() = prefs.getBoolean(KEY_KIOSK_MODE, true)
        set(value) = prefs.edit().putBoolean(KEY_KIOSK_MODE, value).apply()

    var keepScreenOn: Boolean
        get() = prefs.getBoolean(KEY_SCREEN_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_SCREEN_ON, value).apply()

    var pendingUpdateDownloadId: Long
        get() = prefs.getLong(KEY_PENDING_UPDATE_DOWNLOAD_ID, -1L)
        set(value) = prefs.edit().putLong(KEY_PENDING_UPDATE_DOWNLOAD_ID, value).apply()

    // Screen orientation (ActivityInfo.SCREEN_ORIENTATION_* values)
    var screenOrientation: Int
        get() = prefs.getInt(KEY_SCREEN_ORIENTATION, android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE)
        set(value) = prefs.edit().putInt(KEY_SCREEN_ORIENTATION, value).apply()

    // Timezone override
    var timezone: String?
        get() = prefs.getString(KEY_TIMEZONE, null)
        set(value) = prefs.edit().putString(KEY_TIMEZONE, value).apply()

    // Locale override
    var locale: String?
        get() = prefs.getString(KEY_LOCALE, null)
        set(value) = prefs.edit().putString(KEY_LOCALE, value).apply()

    // Scheduled reboot settings
    var scheduledRebootEnabled: Boolean
        get() = prefs.getBoolean(KEY_SCHEDULED_REBOOT_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_SCHEDULED_REBOOT_ENABLED, value).apply()

    var scheduledRebootHour: Int
        get() = prefs.getInt(KEY_SCHEDULED_REBOOT_HOUR, 3)
        set(value) = prefs.edit().putInt(KEY_SCHEDULED_REBOOT_HOUR, value).apply()

    var scheduledRebootMinute: Int
        get() = prefs.getInt(KEY_SCHEDULED_REBOOT_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_SCHEDULED_REBOOT_MINUTE, value).apply()

    // Display schedule settings
    var displayScheduleEnabled: Boolean
        get() = prefs.getBoolean(KEY_DISPLAY_SCHEDULE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DISPLAY_SCHEDULE_ENABLED, value).apply()

    var displayOnHour: Int
        get() = prefs.getInt(KEY_DISPLAY_ON_HOUR, 7)
        set(value) = prefs.edit().putInt(KEY_DISPLAY_ON_HOUR, value).apply()

    var displayOnMinute: Int
        get() = prefs.getInt(KEY_DISPLAY_ON_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_DISPLAY_ON_MINUTE, value).apply()

    var displayOffHour: Int
        get() = prefs.getInt(KEY_DISPLAY_OFF_HOUR, 22)
        set(value) = prefs.edit().putInt(KEY_DISPLAY_OFF_HOUR, value).apply()

    var displayOffMinute: Int
        get() = prefs.getInt(KEY_DISPLAY_OFF_MINUTE, 0)
        set(value) = prefs.edit().putInt(KEY_DISPLAY_OFF_MINUTE, value).apply()

    private fun generateDeviceId(): String {
        return "iocast-${UUID.randomUUID().toString().take(8)}"
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
