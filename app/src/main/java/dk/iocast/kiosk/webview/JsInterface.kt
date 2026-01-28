package dk.iocast.kiosk.webview

import android.content.Context
import android.webkit.JavascriptInterface
import dk.iocast.kiosk.IOCastApp
import dk.iocast.kiosk.util.DeviceInfo
import org.json.JSONObject

/**
 * JavaScript interface exposed to WebView as 'iocast' object.
 * Allows the displayed webpage to interact with native Android features.
 *
 * Usage in webpage:
 *   if (typeof iocast !== 'undefined') {
 *       const deviceId = iocast.getDeviceId();
 *       iocast.speak("Hej verden");
 *   }
 */
class JsInterface(private val context: Context) {

    companion object {
        const val INTERFACE_NAME = "iocast"
    }

    /**
     * Get the device ID
     */
    @JavascriptInterface
    fun getDeviceId(): String {
        return IOCastApp.instance.prefs.deviceId
    }

    /**
     * Get the current URL being displayed
     */
    @JavascriptInterface
    fun getCurrentUrl(): String {
        return IOCastApp.instance.prefs.currentUrl
    }

    /**
     * Get the configured start URL
     */
    @JavascriptInterface
    fun getStartUrl(): String {
        return IOCastApp.instance.prefs.startUrl
    }

    /**
     * Get the app version
     */
    @JavascriptInterface
    fun getAppVersion(): String {
        return try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get battery level (0-100)
     */
    @JavascriptInterface
    fun getBatteryLevel(): Int {
        val info = DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl)
        return info.optInt("batteryLevel", -1)
    }

    /**
     * Check if device is charging
     */
    @JavascriptInterface
    fun isCharging(): Boolean {
        val info = DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl)
        return info.optBoolean("batteryCharging", false)
    }

    /**
     * Get device info as JSON string
     */
    @JavascriptInterface
    fun getDeviceInfo(): String {
        return DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl).toString()
    }

    /**
     * Get WiFi SSID
     */
    @JavascriptInterface
    fun getWifiSsid(): String {
        val info = DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl)
        return info.optString("wifiSsid", "")
    }

    /**
     * Get WiFi signal strength in dBm
     */
    @JavascriptInterface
    fun getWifiSignal(): Int {
        val info = DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl)
        return info.optInt("wifiSignal", 0)
    }

    /**
     * Check if screen is on
     */
    @JavascriptInterface
    fun isScreenOn(): Boolean {
        val info = DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl)
        return info.optBoolean("screenOn", true)
    }

    /**
     * Log a message (for debugging from webpage)
     */
    @JavascriptInterface
    fun log(message: String) {
        android.util.Log.d("JsInterface", "WebPage log: $message")
    }

    /**
     * Show a toast message
     */
    @JavascriptInterface
    fun showToast(message: String) {
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }
}
