package dk.iocast.kiosk.util

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Controller for TV-specific features like brightness, sleep, and power
 * Works with Android TV devices including Thomson 240G
 */
object TvController {
    private const val TAG = "TvController"

    /**
     * Set screen brightness (0-255)
     */
    fun setBrightness(context: Context, level: Int): Boolean {
        val clampedLevel = level.coerceIn(0, 255)
        return try {
            // Check if we can write settings
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.System.canWrite(context)) {
                    Log.w(TAG, "Cannot write system settings - permission not granted")
                    // Try to request permission
                    val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    return false
                }
            }

            // Disable auto-brightness first
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )

            // Set brightness
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS,
                clampedLevel
            )

            Log.d(TAG, "Brightness set to $clampedLevel")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set brightness: ${e.message}")
            false
        }
    }

    /**
     * Get current brightness level (0-255)
     */
    fun getBrightness(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Set brightness as percentage (0-100)
     */
    fun setBrightnessPercent(context: Context, percent: Int): Boolean {
        val level = (percent.coerceIn(0, 100) * 255) / 100
        return setBrightness(context, level)
    }

    /**
     * Get brightness as percentage (0-100)
     */
    fun getBrightnessPercent(context: Context): Int {
        val brightness = getBrightness(context)
        return if (brightness >= 0) (brightness * 100) / 255 else -1
    }

    /**
     * Enable/disable auto-brightness
     */
    fun setAutoBrightness(context: Context, enabled: Boolean): Boolean {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_BRIGHTNESS_MODE,
                if (enabled) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set auto-brightness: ${e.message}")
            false
        }
    }

    /**
     * Put device to sleep/standby
     * Requires Device Admin or root
     */
    fun goToSleep(context: Context): Boolean {
        return try {
            // Try using KeyEvent (requires system privileges)
            val keyEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SLEEP)
            // This usually requires system app status

            // Alternative: Try shell command
            val process = Runtime.getRuntime().exec("input keyevent KEYCODE_SLEEP")
            val result = process.waitFor()

            if (result == 0) {
                Log.d(TAG, "Sleep command sent via keyevent")
                true
            } else {
                // Try power button simulation
                Runtime.getRuntime().exec("input keyevent KEYCODE_POWER")
                Log.d(TAG, "Power key sent as fallback")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to put device to sleep: ${e.message}")
            false
        }
    }

    /**
     * Wake up the screen
     */
    fun wakeUp(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            @Suppress("DEPRECATION")
            val wakeLock = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "iocast:wakeup"
            )
            wakeLock.acquire(1000)
            wakeLock.release()
            Log.d(TAG, "Screen woken up")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to wake screen: ${e.message}")
            false
        }
    }

    /**
     * Check if screen is on
     */
    fun isScreenOn(context: Context): Boolean {
        return try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.isInteractive
        } catch (e: Exception) {
            true
        }
    }

    /**
     * Set screen timeout (milliseconds)
     */
    fun setScreenTimeout(context: Context, timeoutMs: Int): Boolean {
        return try {
            Settings.System.putInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                timeoutMs
            )
            Log.d(TAG, "Screen timeout set to ${timeoutMs}ms")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set screen timeout: ${e.message}")
            false
        }
    }

    /**
     * Get screen timeout (milliseconds)
     */
    fun getScreenTimeout(context: Context): Int {
        return try {
            Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_OFF_TIMEOUT)
        } catch (e: Exception) {
            -1
        }
    }

    /**
     * Send HDMI-CEC command (if supported)
     * Requires system app or root
     */
    fun sendHdmiCecCommand(command: String): Boolean {
        return try {
            // Common CEC commands via shell
            val process = when (command.lowercase()) {
                "standby" -> Runtime.getRuntime().exec("cmd hdmi_control onetouchplay off")
                "on" -> Runtime.getRuntime().exec("cmd hdmi_control onetouchplay on")
                "volume_up" -> Runtime.getRuntime().exec("cmd hdmi_control volume up")
                "volume_down" -> Runtime.getRuntime().exec("cmd hdmi_control volume down")
                "mute" -> Runtime.getRuntime().exec("cmd hdmi_control volume mute")
                else -> return false
            }
            process.waitFor() == 0
        } catch (e: Exception) {
            Log.e(TAG, "CEC command failed: ${e.message}")
            false
        }
    }

    /**
     * Reboot device (requires root or device owner)
     */
    fun reboot(): Boolean {
        return try {
            Runtime.getRuntime().exec("reboot")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Reboot failed: ${e.message}")
            false
        }
    }

    /**
     * Shutdown device (requires root or device owner)
     */
    fun shutdown(): Boolean {
        return try {
            Runtime.getRuntime().exec("reboot -p")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Shutdown failed: ${e.message}")
            false
        }
    }

    /**
     * Get HDMI-CEC status
     */
    fun getHdmiCecStatus(): String {
        return try {
            val process = Runtime.getRuntime().exec("dumpsys hdmi_control")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.appendLine(line)
            }
            reader.close()
            output.toString()
        } catch (e: Exception) {
            "CEC status unavailable: ${e.message}"
        }
    }
}
