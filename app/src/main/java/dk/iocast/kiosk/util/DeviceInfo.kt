package dk.iocast.kiosk.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.os.Build
import android.os.StatFs
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import dk.iocast.kiosk.BuildConfig
import dk.iocast.kiosk.IOCastApp
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.InputStreamReader

/**
 * Collects device information for telemetry
 * Extended with serial number, temperature, and GPS position
 */
object DeviceInfo {

    private const val TAG = "DeviceInfo"

    fun collect(context: Context, currentUrl: String? = null): JSONObject {
        val json = JSONObject()

        // Helper to safely put values
        fun safePut(key: String, value: Any?) {
            try {
                if (value != null) {
                    json.put(key, value)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to add $key: ${e.message}")
            }
        }

        // Core identifiers (always include)
        safePut("deviceId", IOCastApp.instance.prefs.deviceId)
        safePut("timestamp", System.currentTimeMillis() / 1000)

        // App info
        safePut("appVersion", BuildConfig.VERSION_NAME)
        safePut("appVersionCode", BuildConfig.VERSION_CODE)

        // Android info - all safe static fields
        safePut("androidVersion", Build.VERSION.RELEASE)
        safePut("androidSdk", Build.VERSION.SDK_INT)
        safePut("manufacturer", Build.MANUFACTURER)
        safePut("model", Build.MODEL)
        safePut("device", Build.DEVICE)
        safePut("product", Build.PRODUCT)
        safePut("hardware", Build.HARDWARE)
        safePut("bootloader", Build.BOOTLOADER)

        // Serial number (may fail on Android 10+ without READ_PRIVILEGED_PHONE_STATE)
        safePut("serialNumber", getSerialNumber())

        // Android ID (unique per device/app)
        safePut("androidId", getAndroidId(context))

        // Battery info
        try {
            val batteryInfo = getBatteryInfo(context)
            safePut("batteryLevel", batteryInfo.first)
            safePut("batteryCharging", batteryInfo.second)
        } catch (e: Exception) {
            Log.w(TAG, "Battery info failed: ${e.message}")
        }
        safePut("batteryTemperature", getBatteryTemperature(context))

        // CPU Temperature
        safePut("cpuTemperature", getCpuTemperature())

        // Network info
        safePut("networkConnected", isNetworkConnected(context))
        safePut("wifiSsid", getWifiSsid(context))
        safePut("wifiSignal", getWifiSignalStrength(context))
        safePut("wifiBssid", getWifiBssid(context))
        safePut("ipAddress", getIpAddress(context))
        safePut("macAddress", getMacAddress(context))

        // Screen info
        safePut("screenOn", isScreenOn(context))
        safePut("screenBrightness", getScreenBrightness(context))

        // Memory info
        try {
            val memInfo = getMemoryInfo()
            safePut("memoryTotal", memInfo.first)
            safePut("memoryFree", memInfo.second)
        } catch (e: Exception) {
            Log.w(TAG, "Memory info failed: ${e.message}")
        }

        // Storage info
        try {
            val storageInfo = getStorageInfo()
            safePut("storageTotal", storageInfo.first)
            safePut("storageFree", storageInfo.second)
        } catch (e: Exception) {
            Log.w(TAG, "Storage info failed: ${e.message}")
        }

        // Location (if permission granted)
        try {
            val location = getLastKnownLocation(context)
            if (location != null) {
                safePut("latitude", location.latitude)
                safePut("longitude", location.longitude)
                safePut("locationAccuracy", location.accuracy)
                safePut("locationTime", location.time / 1000)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Location failed: ${e.message}")
        }

        // Uptime
        safePut("uptime", android.os.SystemClock.elapsedRealtime() / 1000)
        safePut("bootTime", (System.currentTimeMillis() - android.os.SystemClock.elapsedRealtime()) / 1000)

        // Current URL
        currentUrl?.let { safePut("currentUrl", it) }

        return json
    }

    private fun getBatteryInfo(context: Context): Pair<Int, Boolean> {
        val batteryIntent = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1

        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        return Pair(batteryPct, isCharging)
    }

    private fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun getWifiSsid(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            wifiInfo.ssid?.replace("\"", "") ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun getWifiSignalStrength(context: Context): Int {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.connectionInfo.rssi
        } catch (e: Exception) {
            0
        }
    }

    private fun isScreenOn(context: Context): Boolean {
        return try {
            val powerManager = context.getSystemService(Context.POWER_SERVICE)
                    as android.os.PowerManager
            powerManager.isInteractive
        } catch (e: Exception) {
            true
        }
    }

    private fun getMemoryInfo(): Pair<Long, Long> {
        val runtime = Runtime.getRuntime()
        val total = runtime.maxMemory() / (1024 * 1024) // MB
        val free = (runtime.maxMemory() - runtime.totalMemory() + runtime.freeMemory()) / (1024 * 1024)
        return Pair(total, free)
    }

    private fun getStorageInfo(): Pair<Long, Long> {
        return try {
            val stat = StatFs(File("/data").path)
            val total = stat.blockCountLong * stat.blockSizeLong / (1024 * 1024) // MB
            val free = stat.availableBlocksLong * stat.blockSizeLong / (1024 * 1024)
            Pair(total, free)
        } catch (e: Exception) {
            Pair(0L, 0L)
        }
    }

    /**
     * Get device serial number
     * Note: Requires READ_PHONE_STATE permission on API 26+ or device owner
     */
    @SuppressLint("HardwareIds")
    private fun getSerialNumber(): String {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // API 26+ requires READ_PRIVILEGED_PHONE_STATE or device owner
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        } catch (e: SecurityException) {
            // Try alternative methods
            getSerialFromProp()
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Try to get serial from system properties
     */
    private fun getSerialFromProp(): String {
        return try {
            val process = Runtime.getRuntime().exec("getprop ro.serialno")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val serial = reader.readLine()?.trim()
            reader.close()
            if (!serial.isNullOrEmpty() && serial != "unknown") serial else "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get Android ID (unique per device/app combination)
     */
    @SuppressLint("HardwareIds")
    private fun getAndroidId(context: Context): String {
        return try {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get battery temperature in Celsius
     */
    private fun getBatteryTemperature(context: Context): Float {
        return try {
            val batteryIntent = context.registerReceiver(
                null,
                IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            )
            val temp = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
            if (temp > 0) temp / 10.0f else -1f
        } catch (e: Exception) {
            -1f
        }
    }

    /**
     * Get CPU temperature from thermal zones
     * Android TV devices often expose this
     */
    private fun getCpuTemperature(): Float {
        // Try common thermal zone paths
        val thermalPaths = listOf(
            "/sys/class/thermal/thermal_zone0/temp",
            "/sys/devices/virtual/thermal/thermal_zone0/temp",
            "/sys/class/hwmon/hwmon0/temp1_input",
            "/sys/devices/system/cpu/cpu0/cpufreq/cpu_temp",
            "/sys/devices/platform/omap/omap_temp_sensor.0/temperature"
        )

        for (path in thermalPaths) {
            try {
                val file = File(path)
                if (file.exists()) {
                    val reader = BufferedReader(FileReader(file))
                    val tempString = reader.readLine()
                    reader.close()
                    val temp = tempString?.toIntOrNull() ?: continue
                    // Values over 1000 are in millidegrees
                    return if (temp > 1000) temp / 1000.0f else temp.toFloat()
                }
            } catch (e: Exception) {
                continue
            }
        }
        return -1f
    }

    /**
     * Get WiFi BSSID (access point MAC address)
     */
    private fun getWifiBssid(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiManager.connectionInfo.bssid ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get device IP address
     */
    private fun getIpAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ipInt = wifiManager.connectionInfo.ipAddress
            if (ipInt == 0) return "0.0.0.0"
            String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get WiFi MAC address
     */
    @SuppressLint("HardwareIds")
    private fun getMacAddress(context: Context): String {
        return try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            @Suppress("DEPRECATION")
            wifiManager.connectionInfo.macAddress ?: "02:00:00:00:00:00"
        } catch (e: Exception) {
            "unknown"
        }
    }

    /**
     * Get screen brightness (0-255)
     */
    private fun getScreenBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Get last known GPS location (if permission granted)
     */
    @SuppressLint("MissingPermission")
    private fun getLastKnownLocation(context: Context): Location? {
        // Check if we have location permission
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

            // Try GPS first, then network
            var location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            }
            if (location == null) {
                location = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            }
            location
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get location: ${e.message}")
            null
        }
    }
}
