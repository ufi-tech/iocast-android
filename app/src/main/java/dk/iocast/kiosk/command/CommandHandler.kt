package dk.iocast.kiosk.command

import android.app.AlarmManager
import android.app.DownloadManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.InetAddress
import java.util.TimeZone
import dk.iocast.kiosk.IOCastApp
import dk.iocast.kiosk.util.DeviceInfo
import dk.iocast.kiosk.util.TvController
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Handles incoming MQTT commands and dispatches to appropriate actions
 */
class CommandHandler(private val context: Context) {

    companion object {
        private const val TAG = "CommandHandler"
    }

    data class CommandResult(
        val success: Boolean,
        val message: String,
        val data: JSONObject? = null
    )

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    init {
        // Initialize Text-to-Speech
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("da", "DK")
                ttsReady = true
            }
        }
    }

    fun handle(command: String, payload: String): CommandResult {
        Log.d(TAG, "Handling command: $command with payload: $payload")

        return try {
            val json = if (payload.isNotEmpty()) JSONObject(payload) else JSONObject()

            when (command) {
                "loadUrl" -> handleLoadUrl(json)
                "reload" -> handleReload()
                "loadStartUrl" -> handleLoadStartUrl()
                "goBack" -> handleGoBack()
                "goForward" -> handleGoForward()
                "screenOn" -> handleScreenOn()
                "screenOff" -> handleScreenOff()
                "reboot" -> handleReboot()
                "setVolume" -> handleSetVolume(json)
                "speak" -> handleSpeak(json)
                "stopSpeak" -> handleStopSpeak()
                "getInfo" -> handleGetInfo()
                "screenshot" -> handleScreenshot()
                "clearCache" -> handleClearCache()
                "setStartUrl" -> handleSetStartUrl(json)
                "openWifiSettings" -> handleOpenWifiSettings()
                "openSettings" -> handleOpenSettings(json)
                // TV Control commands
                "setBrightness" -> handleSetBrightness(json)
                "getBrightness" -> handleGetBrightness()
                "sleep" -> handleSleep()
                "wakeUp" -> handleWakeUp()
                "setScreenTimeout" -> handleSetScreenTimeout(json)
                "shutdown" -> handleShutdown()
                // HDMI-CEC commands (TV control)
                "tvOn" -> handleTvOn()
                "tvOff" -> handleTvOff()
                "tvVolumeUp" -> handleTvVolumeUp()
                "tvVolumeDown" -> handleTvVolumeDown()
                "tvMute" -> handleTvMute()
                "cecStatus" -> handleCecStatus()
                // Network commands
                "ping" -> handlePing(json)
                "getWifiNetworks" -> handleGetWifiNetworks()
                "getNetworkInfo" -> handleGetNetworkInfo()
                // System commands
                "setOrientation" -> handleSetOrientation(json)
                "getOrientation" -> handleGetOrientation()
                "setTimezone" -> handleSetTimezone(json)
                "getTimezone" -> handleGetTimezone()
                "setLocale" -> handleSetLocale(json)
                "getLocale" -> handleGetLocale()
                // App management
                "getApps" -> handleGetApps()
                "launchApp" -> handleLaunchApp(json)
                "restartApp" -> handleRestartApp()
                // Kiosk mode
                "setKioskMode" -> handleSetKioskMode(json)
                "getKioskMode" -> handleGetKioskMode()
                // Scheduling
                "scheduleReboot" -> handleScheduleReboot(json)
                "cancelScheduledReboot" -> handleCancelScheduledReboot()
                "setDisplaySchedule" -> handleSetDisplaySchedule(json)
                "getDisplaySchedule" -> handleGetDisplaySchedule()
                // Debug/logs
                "getLogs" -> handleGetLogs(json)
                "runShell" -> handleRunShell(json)
                // Storage
                "getStorage" -> handleGetStorage()
                "clearAppData" -> handleClearAppData()
                // Volume control
                "getVolume" -> handleGetVolume()
                "setMute" -> handleSetMute(json)
                "getMute" -> handleGetMute()
                // OTA Update command
                "update" -> handleUpdate(json)
                else -> CommandResult(false, "Unknown command: $command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command error: ${e.message}")
            CommandResult(false, "Error: ${e.message}")
        }
    }

    private fun handleLoadUrl(json: JSONObject): CommandResult {
        val url = json.optString("url", "")
        if (url.isEmpty()) {
            return CommandResult(false, "Missing 'url' parameter")
        }

        sendCommandToActivity("loadUrl", url)
        IOCastApp.instance.prefs.currentUrl = url
        return CommandResult(true, "Loading URL: $url")
    }

    private fun handleReload(): CommandResult {
        sendCommandToActivity("reload", null)
        return CommandResult(true, "Reloading page")
    }

    private fun handleLoadStartUrl(): CommandResult {
        val startUrl = IOCastApp.instance.prefs.startUrl
        sendCommandToActivity("loadUrl", startUrl)
        IOCastApp.instance.prefs.currentUrl = startUrl
        return CommandResult(true, "Loading start URL: $startUrl")
    }

    private fun handleGoBack(): CommandResult {
        sendCommandToActivity("goBack", null)
        return CommandResult(true, "Going back")
    }

    private fun handleGoForward(): CommandResult {
        sendCommandToActivity("goForward", null)
        return CommandResult(true, "Going forward")
    }

    private fun handleScreenOn(): CommandResult {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                    "iocast:screenon"
                )
                wakeLock.acquire(1000)
                wakeLock.release()
            }
            CommandResult(true, "Screen turned on")
        } catch (e: Exception) {
            CommandResult(false, "Failed to turn screen on: ${e.message}")
        }
    }

    private fun handleScreenOff(): CommandResult {
        // Note: Turning screen off requires device admin privileges
        return CommandResult(false, "Screen off requires device admin privileges")
    }

    private fun handleReboot(): CommandResult {
        return try {
            // Requires root or device owner
            Runtime.getRuntime().exec("reboot")
            CommandResult(true, "Rebooting device")
        } catch (e: Exception) {
            CommandResult(false, "Reboot failed: ${e.message}")
        }
    }

    private fun handleSetVolume(json: JSONObject): CommandResult {
        val level = json.optInt("level", -1)
        if (level < 0 || level > 100) {
            return CommandResult(false, "Volume level must be 0-100")
        }

        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val newVolume = (level * maxVolume) / 100
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVolume, 0)
            CommandResult(true, "Volume set to $level%")
        } catch (e: Exception) {
            CommandResult(false, "Failed to set volume: ${e.message}")
        }
    }

    private fun handleSpeak(json: JSONObject): CommandResult {
        val text = json.optString("text", "")
        if (text.isEmpty()) {
            return CommandResult(false, "Missing 'text' parameter")
        }

        return if (ttsReady) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "iocast-tts")
            CommandResult(true, "Speaking: $text")
        } else {
            CommandResult(false, "Text-to-speech not ready")
        }
    }

    private fun handleStopSpeak(): CommandResult {
        tts?.stop()
        return CommandResult(true, "Speech stopped")
    }

    private fun handleGetInfo(): CommandResult {
        val info = DeviceInfo.collect(context, IOCastApp.instance.prefs.currentUrl)
        return CommandResult(true, "Device info collected", info)
    }

    private fun handleScreenshot(): CommandResult {
        sendCommandToActivity("screenshot", null)
        return CommandResult(true, "Screenshot requested")
    }

    private fun handleClearCache(): CommandResult {
        sendCommandToActivity("clearCache", null)
        return CommandResult(true, "Cache cleared")
    }

    private fun handleSetStartUrl(json: JSONObject): CommandResult {
        val url = json.optString("url", "")
        if (url.isEmpty()) {
            return CommandResult(false, "Missing 'url' parameter")
        }

        IOCastApp.instance.prefs.startUrl = url
        return CommandResult(true, "Start URL set to: $url")
    }

    private fun handleOpenWifiSettings(): CommandResult {
        return try {
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(true, "WiFi settings opened")
        } catch (e: Exception) {
            CommandResult(false, "Failed to open WiFi settings: ${e.message}")
        }
    }

    private fun handleOpenSettings(json: JSONObject): CommandResult {
        val settingsType = json.optString("type", "wifi")
        return try {
            val action = when (settingsType) {
                "wifi" -> Settings.ACTION_WIFI_SETTINGS
                "bluetooth" -> Settings.ACTION_BLUETOOTH_SETTINGS
                "display" -> Settings.ACTION_DISPLAY_SETTINGS
                "sound" -> Settings.ACTION_SOUND_SETTINGS
                "date" -> Settings.ACTION_DATE_SETTINGS
                "location" -> Settings.ACTION_LOCATION_SOURCE_SETTINGS
                "apps" -> Settings.ACTION_APPLICATION_SETTINGS
                "network" -> Settings.ACTION_WIRELESS_SETTINGS
                else -> Settings.ACTION_SETTINGS
            }
            val intent = Intent(action).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            CommandResult(true, "Settings ($settingsType) opened")
        } catch (e: Exception) {
            CommandResult(false, "Failed to open settings: ${e.message}")
        }
    }

    // ========== TV Control Commands ==========

    private fun handleSetBrightness(json: JSONObject): CommandResult {
        val level = json.optInt("level", -1)
        if (level < 0 || level > 100) {
            return CommandResult(false, "Brightness level must be 0-100")
        }

        return if (TvController.setBrightnessPercent(context, level)) {
            val data = JSONObject().apply {
                put("brightness", level)
                put("brightnessRaw", TvController.getBrightness(context))
            }
            CommandResult(true, "Brightness set to $level%", data)
        } else {
            CommandResult(false, "Failed to set brightness - permission may be required")
        }
    }

    private fun handleGetBrightness(): CommandResult {
        val brightness = TvController.getBrightnessPercent(context)
        val brightnessRaw = TvController.getBrightness(context)
        val screenTimeout = TvController.getScreenTimeout(context)
        val screenOn = TvController.isScreenOn(context)

        val data = JSONObject().apply {
            put("brightness", brightness)
            put("brightnessRaw", brightnessRaw)
            put("screenTimeout", screenTimeout)
            put("screenOn", screenOn)
        }

        return CommandResult(true, "Brightness: $brightness%", data)
    }

    private fun handleSleep(): CommandResult {
        return if (TvController.goToSleep(context)) {
            CommandResult(true, "Device going to sleep")
        } else {
            CommandResult(false, "Failed to put device to sleep - may require system privileges")
        }
    }

    private fun handleWakeUp(): CommandResult {
        return if (TvController.wakeUp(context)) {
            CommandResult(true, "Screen woken up")
        } else {
            CommandResult(false, "Failed to wake screen")
        }
    }

    private fun handleSetScreenTimeout(json: JSONObject): CommandResult {
        // Timeout in seconds, convert to milliseconds
        val timeoutSeconds = json.optInt("timeout", -1)
        if (timeoutSeconds < 0) {
            return CommandResult(false, "Missing or invalid 'timeout' parameter (seconds)")
        }

        val timeoutMs = timeoutSeconds * 1000
        return if (TvController.setScreenTimeout(context, timeoutMs)) {
            val data = JSONObject().apply {
                put("timeoutSeconds", timeoutSeconds)
                put("timeoutMs", timeoutMs)
            }
            CommandResult(true, "Screen timeout set to ${timeoutSeconds}s", data)
        } else {
            CommandResult(false, "Failed to set screen timeout")
        }
    }

    private fun handleShutdown(): CommandResult {
        return if (TvController.shutdown()) {
            CommandResult(true, "Shutting down device")
        } else {
            CommandResult(false, "Shutdown failed - requires root or device owner")
        }
    }

    // ========== HDMI-CEC Commands (TV Control) ==========

    private fun handleTvOn(): CommandResult {
        return if (TvController.sendHdmiCecCommand("on")) {
            CommandResult(true, "TV turning on via CEC")
        } else {
            CommandResult(false, "CEC command failed - TV may not support CEC or device lacks permission")
        }
    }

    private fun handleTvOff(): CommandResult {
        return if (TvController.sendHdmiCecCommand("standby")) {
            CommandResult(true, "TV going to standby via CEC")
        } else {
            CommandResult(false, "CEC command failed - TV may not support CEC or device lacks permission")
        }
    }

    private fun handleTvVolumeUp(): CommandResult {
        return if (TvController.sendHdmiCecCommand("volume_up")) {
            CommandResult(true, "TV volume increased via CEC")
        } else {
            CommandResult(false, "CEC volume command failed")
        }
    }

    private fun handleTvVolumeDown(): CommandResult {
        return if (TvController.sendHdmiCecCommand("volume_down")) {
            CommandResult(true, "TV volume decreased via CEC")
        } else {
            CommandResult(false, "CEC volume command failed")
        }
    }

    private fun handleTvMute(): CommandResult {
        return if (TvController.sendHdmiCecCommand("mute")) {
            CommandResult(true, "TV muted via CEC")
        } else {
            CommandResult(false, "CEC mute command failed")
        }
    }

    private fun handleCecStatus(): CommandResult {
        val status = TvController.getHdmiCecStatus()
        val data = JSONObject().apply {
            put("cecStatus", status)
        }
        return CommandResult(true, "CEC status retrieved", data)
    }

    // ========== Network Commands ==========

    private fun handlePing(json: JSONObject): CommandResult {
        val host = json.optString("host", "8.8.8.8")
        val timeout = json.optInt("timeout", 5000)

        return try {
            val reachable = InetAddress.getByName(host).isReachable(timeout)
            val data = JSONObject().apply {
                put("host", host)
                put("reachable", reachable)
                put("timeout", timeout)
            }
            CommandResult(reachable, if (reachable) "Host $host is reachable" else "Host $host is not reachable", data)
        } catch (e: Exception) {
            CommandResult(false, "Ping failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun handleGetWifiNetworks(): CommandResult {
        return try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val results = wifiManager.scanResults
            val networks = JSONArray()

            results.forEach { result ->
                networks.put(JSONObject().apply {
                    put("ssid", result.SSID)
                    put("bssid", result.BSSID)
                    put("level", result.level)
                    put("frequency", result.frequency)
                    put("capabilities", result.capabilities)
                })
            }

            val data = JSONObject().apply {
                put("networks", networks)
                put("count", results.size)
            }
            CommandResult(true, "Found ${results.size} networks", data)
        } catch (e: Exception) {
            CommandResult(false, "WiFi scan failed: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun handleGetNetworkInfo(): CommandResult {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

            val data = JSONObject().apply {
                // Active network info
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val network = connectivityManager.activeNetwork
                    val capabilities = connectivityManager.getNetworkCapabilities(network)
                    put("hasInternet", capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true)
                    put("hasWifi", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true)
                    put("hasCellular", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true)
                    put("hasEthernet", capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true)
                }

                // WiFi specific info
                val wifiInfo = wifiManager.connectionInfo
                put("wifiSsid", wifiInfo.ssid?.replace("\"", "") ?: "unknown")
                put("wifiBssid", wifiInfo.bssid ?: "unknown")
                put("wifiSignal", wifiInfo.rssi)
                put("wifiSpeed", wifiInfo.linkSpeed)
                put("wifiFrequency", wifiInfo.frequency)
                put("ipAddress", android.text.format.Formatter.formatIpAddress(wifiInfo.ipAddress))
                put("macAddress", wifiInfo.macAddress ?: "unknown")
            }
            CommandResult(true, "Network info retrieved", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to get network info: ${e.message}")
        }
    }

    // ========== System Commands ==========

    private fun handleSetOrientation(json: JSONObject): CommandResult {
        val orientation = json.optString("orientation", "landscape")
        return try {
            val orientationValue = when (orientation.lowercase()) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                "reverse_landscape" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
                "reverse_portrait" -> ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
                "sensor" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR
                "auto" -> ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
                else -> return CommandResult(false, "Unknown orientation: $orientation")
            }

            IOCastApp.instance.prefs.screenOrientation = orientationValue
            sendCommandToActivity("setOrientation", orientationValue.toString())

            CommandResult(true, "Orientation set to $orientation")
        } catch (e: Exception) {
            CommandResult(false, "Failed to set orientation: ${e.message}")
        }
    }

    private fun handleGetOrientation(): CommandResult {
        val orientation = IOCastApp.instance.prefs.screenOrientation
        val orientationName = when (orientation) {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE -> "landscape"
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT -> "portrait"
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE -> "reverse_landscape"
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT -> "reverse_portrait"
            ActivityInfo.SCREEN_ORIENTATION_SENSOR -> "sensor"
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR -> "auto"
            else -> "unknown"
        }
        val data = JSONObject().apply {
            put("orientation", orientationName)
            put("orientationValue", orientation)
        }
        return CommandResult(true, "Current orientation: $orientationName", data)
    }

    private fun handleSetTimezone(json: JSONObject): CommandResult {
        val timezone = json.optString("timezone", "")
        if (timezone.isEmpty()) {
            return CommandResult(false, "Missing 'timezone' parameter")
        }

        return try {
            // Note: Setting system timezone requires WRITE_SETTINGS permission
            // This stores it for app-level use
            IOCastApp.instance.prefs.timezone = timezone
            val data = JSONObject().apply {
                put("timezone", timezone)
                put("note", "App timezone set. System timezone requires WRITE_SETTINGS permission.")
            }
            CommandResult(true, "Timezone set to $timezone", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to set timezone: ${e.message}")
        }
    }

    private fun handleGetTimezone(): CommandResult {
        val systemTimezone = TimeZone.getDefault()
        val appTimezone = IOCastApp.instance.prefs.timezone
        val data = JSONObject().apply {
            put("systemTimezone", systemTimezone.id)
            put("appTimezone", appTimezone ?: systemTimezone.id)
            put("displayName", systemTimezone.displayName)
            put("offset", systemTimezone.rawOffset / 3600000)
        }
        return CommandResult(true, "Timezone: ${systemTimezone.id}", data)
    }

    private fun handleSetLocale(json: JSONObject): CommandResult {
        val language = json.optString("language", "da")
        val country = json.optString("country", "DK")

        return try {
            IOCastApp.instance.prefs.locale = "${language}_${country}"
            val data = JSONObject().apply {
                put("language", language)
                put("country", country)
                put("locale", "${language}_${country}")
            }
            CommandResult(true, "Locale set to ${language}_${country}", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to set locale: ${e.message}")
        }
    }

    private fun handleGetLocale(): CommandResult {
        val locale = Locale.getDefault()
        val appLocale = IOCastApp.instance.prefs.locale
        val data = JSONObject().apply {
            put("systemLanguage", locale.language)
            put("systemCountry", locale.country)
            put("systemLocale", locale.toString())
            put("appLocale", appLocale ?: locale.toString())
            put("displayLanguage", locale.displayLanguage)
            put("displayCountry", locale.displayCountry)
        }
        return CommandResult(true, "Locale: ${locale}", data)
    }

    // ========== App Management ==========

    private fun handleGetApps(): CommandResult {
        return try {
            val pm = context.packageManager
            val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val appList = JSONArray()

            apps.filter {
                // Only user-installed apps, not system apps
                (it.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0
            }.forEach { app ->
                appList.put(JSONObject().apply {
                    put("packageName", app.packageName)
                    put("name", pm.getApplicationLabel(app).toString())
                    put("enabled", app.enabled)
                })
            }

            val data = JSONObject().apply {
                put("apps", appList)
                put("count", appList.length())
            }
            CommandResult(true, "Found ${appList.length()} user apps", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to get apps: ${e.message}")
        }
    }

    private fun handleLaunchApp(json: JSONObject): CommandResult {
        val packageName = json.optString("package", "")
        if (packageName.isEmpty()) {
            return CommandResult(false, "Missing 'package' parameter")
        }

        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                CommandResult(true, "Launched app: $packageName")
            } else {
                CommandResult(false, "App not found: $packageName")
            }
        } catch (e: Exception) {
            CommandResult(false, "Failed to launch app: ${e.message}")
        }
    }

    private fun handleRestartApp(): CommandResult {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)

            // Schedule kill of current process
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                android.os.Process.killProcess(android.os.Process.myPid())
            }, 500)

            CommandResult(true, "Restarting app...")
        } catch (e: Exception) {
            CommandResult(false, "Failed to restart app: ${e.message}")
        }
    }

    // ========== Kiosk Mode ==========

    private fun handleSetKioskMode(json: JSONObject): CommandResult {
        val enabled = json.optBoolean("enabled", true)
        IOCastApp.instance.prefs.kioskMode = enabled
        sendCommandToActivity("setKioskMode", enabled.toString())

        val data = JSONObject().apply {
            put("kioskMode", enabled)
        }
        return CommandResult(true, "Kiosk mode ${if (enabled) "enabled" else "disabled"}", data)
    }

    private fun handleGetKioskMode(): CommandResult {
        val enabled = IOCastApp.instance.prefs.kioskMode
        val data = JSONObject().apply {
            put("kioskMode", enabled)
        }
        return CommandResult(true, "Kiosk mode: ${if (enabled) "enabled" else "disabled"}", data)
    }

    // ========== Scheduling ==========

    private fun handleScheduleReboot(json: JSONObject): CommandResult {
        val hour = json.optInt("hour", 3)  // Default 03:00
        val minute = json.optInt("minute", 0)
        val daily = json.optBoolean("daily", true)

        return try {
            IOCastApp.instance.prefs.scheduledRebootHour = hour
            IOCastApp.instance.prefs.scheduledRebootMinute = minute
            IOCastApp.instance.prefs.scheduledRebootEnabled = true

            // Schedule via AlarmManager
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, Class.forName("dk.iocast.kiosk.receiver.RebootReceiver")).apply {
                action = "dk.iocast.kiosk.SCHEDULED_REBOOT"
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Calculate next trigger time
            val calendar = java.util.Calendar.getInstance().apply {
                set(java.util.Calendar.HOUR_OF_DAY, hour)
                set(java.util.Calendar.MINUTE, minute)
                set(java.util.Calendar.SECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(java.util.Calendar.DAY_OF_MONTH, 1)
                }
            }

            if (daily) {
                alarmManager.setRepeating(
                    AlarmManager.RTC_WAKEUP,
                    calendar.timeInMillis,
                    AlarmManager.INTERVAL_DAY,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(AlarmManager.RTC_WAKEUP, calendar.timeInMillis, pendingIntent)
            }

            val data = JSONObject().apply {
                put("hour", hour)
                put("minute", minute)
                put("daily", daily)
                put("nextReboot", calendar.time.toString())
            }
            CommandResult(true, "Reboot scheduled for $hour:${minute.toString().padStart(2, '0')}", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to schedule reboot: ${e.message}")
        }
    }

    private fun handleCancelScheduledReboot(): CommandResult {
        return try {
            IOCastApp.instance.prefs.scheduledRebootEnabled = false

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, Class.forName("dk.iocast.kiosk.receiver.RebootReceiver"))
            val pendingIntent = PendingIntent.getBroadcast(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            alarmManager.cancel(pendingIntent)

            CommandResult(true, "Scheduled reboot cancelled")
        } catch (e: Exception) {
            CommandResult(false, "Failed to cancel scheduled reboot: ${e.message}")
        }
    }

    private fun handleSetDisplaySchedule(json: JSONObject): CommandResult {
        val onHour = json.optInt("onHour", 7)
        val onMinute = json.optInt("onMinute", 0)
        val offHour = json.optInt("offHour", 22)
        val offMinute = json.optInt("offMinute", 0)
        val enabled = json.optBoolean("enabled", true)

        IOCastApp.instance.prefs.apply {
            displayScheduleEnabled = enabled
            displayOnHour = onHour
            displayOnMinute = onMinute
            displayOffHour = offHour
            displayOffMinute = offMinute
        }

        val data = JSONObject().apply {
            put("enabled", enabled)
            put("onTime", "$onHour:${onMinute.toString().padStart(2, '0')}")
            put("offTime", "$offHour:${offMinute.toString().padStart(2, '0')}")
        }
        return CommandResult(true, "Display schedule: ON at $onHour:${onMinute.toString().padStart(2, '0')}, OFF at $offHour:${offMinute.toString().padStart(2, '0')}", data)
    }

    private fun handleGetDisplaySchedule(): CommandResult {
        val prefs = IOCastApp.instance.prefs
        val data = JSONObject().apply {
            put("enabled", prefs.displayScheduleEnabled)
            put("onHour", prefs.displayOnHour)
            put("onMinute", prefs.displayOnMinute)
            put("offHour", prefs.displayOffHour)
            put("offMinute", prefs.displayOffMinute)
            put("onTime", "${prefs.displayOnHour}:${prefs.displayOnMinute.toString().padStart(2, '0')}")
            put("offTime", "${prefs.displayOffHour}:${prefs.displayOffMinute.toString().padStart(2, '0')}")
        }
        return CommandResult(true, "Display schedule retrieved", data)
    }

    // ========== Debug/Logs ==========

    private fun handleGetLogs(json: JSONObject): CommandResult {
        val lines = json.optInt("lines", 100)
        val filter = json.optString("filter", "IOCast")

        return try {
            val process = Runtime.getRuntime().exec("logcat -d -t $lines *:V")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val logs = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                if (filter.isEmpty() || line!!.contains(filter, ignoreCase = true)) {
                    logs.appendLine(line)
                }
            }
            reader.close()

            val data = JSONObject().apply {
                put("logs", logs.toString())
                put("lines", lines)
                put("filter", filter)
            }
            CommandResult(true, "Logs retrieved", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to get logs: ${e.message}")
        }
    }

    private fun handleRunShell(json: JSONObject): CommandResult {
        val command = json.optString("command", "")
        if (command.isEmpty()) {
            return CommandResult(false, "Missing 'command' parameter")
        }

        // Security: Only allow safe commands
        val allowedCommands = listOf("getprop", "dumpsys", "pm list", "settings get", "cat /proc", "df", "free", "uptime", "date", "id")
        val isAllowed = allowedCommands.any { command.startsWith(it) }

        if (!isAllowed) {
            return CommandResult(false, "Command not allowed for security reasons. Allowed: ${allowedCommands.joinToString(", ")}")
        }

        return try {
            val process = Runtime.getRuntime().exec(command)
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            reader.close()

            val exitCode = process.waitFor()
            val data = JSONObject().apply {
                put("command", command)
                put("output", output.toString())
                put("exitCode", exitCode)
            }
            CommandResult(exitCode == 0, "Command executed", data)
        } catch (e: Exception) {
            CommandResult(false, "Shell command failed: ${e.message}")
        }
    }

    // ========== Storage ==========

    private fun handleGetStorage(): CommandResult {
        return try {
            val internalStorage = Environment.getDataDirectory()
            val externalStorage = Environment.getExternalStorageDirectory()

            fun getStorageInfo(file: File): JSONObject {
                return JSONObject().apply {
                    put("total", file.totalSpace / (1024 * 1024)) // MB
                    put("free", file.freeSpace / (1024 * 1024))
                    put("used", (file.totalSpace - file.freeSpace) / (1024 * 1024))
                    put("usedPercent", ((file.totalSpace - file.freeSpace) * 100 / file.totalSpace).toInt())
                }
            }

            val data = JSONObject().apply {
                put("internal", getStorageInfo(internalStorage))
                put("external", getStorageInfo(externalStorage))
            }
            CommandResult(true, "Storage info retrieved", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to get storage info: ${e.message}")
        }
    }

    private fun handleClearAppData(): CommandResult {
        return try {
            // Clear WebView cache
            sendCommandToActivity("clearCache", null)

            // Clear app's cache directory
            context.cacheDir.deleteRecursively()

            // Clear external cache
            context.externalCacheDir?.deleteRecursively()

            CommandResult(true, "App cache cleared")
        } catch (e: Exception) {
            CommandResult(false, "Failed to clear app data: ${e.message}")
        }
    }

    // ========== Volume Control ==========

    private fun handleGetVolume(): CommandResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            val data = JSONObject().apply {
                put("music", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
                put("musicMax", audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                put("musicPercent", audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) * 100 / audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC))
                put("ring", audioManager.getStreamVolume(AudioManager.STREAM_RING))
                put("alarm", audioManager.getStreamVolume(AudioManager.STREAM_ALARM))
                put("notification", audioManager.getStreamVolume(AudioManager.STREAM_NOTIFICATION))
                put("system", audioManager.getStreamVolume(AudioManager.STREAM_SYSTEM))
            }
            CommandResult(true, "Volume info retrieved", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to get volume: ${e.message}")
        }
    }

    private fun handleSetMute(json: JSONObject): CommandResult {
        val mute = json.optBoolean("mute", true)
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.adjustStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    if (mute) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE,
                    0
                )
            } else {
                @Suppress("DEPRECATION")
                audioManager.setStreamMute(AudioManager.STREAM_MUSIC, mute)
            }

            CommandResult(true, if (mute) "Audio muted" else "Audio unmuted")
        } catch (e: Exception) {
            CommandResult(false, "Failed to set mute: ${e.message}")
        }
    }

    private fun handleGetMute(): CommandResult {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val isMuted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                audioManager.isStreamMute(AudioManager.STREAM_MUSIC)
            } else {
                audioManager.getStreamVolume(AudioManager.STREAM_MUSIC) == 0
            }

            val data = JSONObject().apply {
                put("muted", isMuted)
            }
            CommandResult(true, if (isMuted) "Audio is muted" else "Audio is not muted", data)
        } catch (e: Exception) {
            CommandResult(false, "Failed to get mute status: ${e.message}")
        }
    }

    // ========== OTA Update Command ==========

    private fun handleUpdate(json: JSONObject): CommandResult {
        val apkUrl = json.optString("url", "")
        if (apkUrl.isEmpty()) {
            return CommandResult(false, "Missing 'url' parameter")
        }

        return try {
            Log.i(TAG, "Starting OTA update download from: $apkUrl")

            // Delete old APK if exists
            val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "iocast-update.apk")
            if (apkFile.exists()) {
                apkFile.delete()
                Log.d(TAG, "Deleted old APK file")
            }

            // Create download request
            val request = DownloadManager.Request(Uri.parse(apkUrl)).apply {
                setTitle("IOCast Update")
                setDescription("Downloader opdatering...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, "iocast-update.apk")
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
            }

            // Start download
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            // Save download ID so DownloadReceiver can track it
            IOCastApp.instance.prefs.pendingUpdateDownloadId = downloadId

            Log.i(TAG, "Update download started with ID: $downloadId")

            val data = JSONObject().apply {
                put("downloadId", downloadId)
                put("url", apkUrl)
            }
            CommandResult(true, "Update download started", data)

        } catch (e: Exception) {
            Log.e(TAG, "Update download failed: ${e.message}", e)
            CommandResult(false, "Update failed: ${e.message}")
        }
    }

    private fun sendCommandToActivity(command: String, payload: String?) {
        val intent = Intent("dk.iocast.kiosk.COMMAND").apply {
            putExtra("command", command)
            payload?.let { putExtra("payload", it) }
        }
        context.sendBroadcast(intent)
    }

    fun destroy() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
