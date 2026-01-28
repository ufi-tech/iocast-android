package dk.iocast.kiosk.command

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.PowerManager
import android.speech.tts.TextToSpeech
import android.util.Log
import dk.iocast.kiosk.IOCastApp
import dk.iocast.kiosk.util.DeviceInfo
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
