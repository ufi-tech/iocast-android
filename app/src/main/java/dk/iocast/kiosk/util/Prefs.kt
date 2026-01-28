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

    private fun generateDeviceId(): String {
        return "iocast-${UUID.randomUUID().toString().take(8)}"
    }

    fun clear() {
        prefs.edit().clear().apply()
    }
}
