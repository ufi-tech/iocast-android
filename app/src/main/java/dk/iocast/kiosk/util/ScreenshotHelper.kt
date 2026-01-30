package dk.iocast.kiosk.util

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.PixelCopy
import android.view.View
import android.webkit.WebView
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Helper class for capturing screenshots and uploading them
 */
object ScreenshotHelper {
    private const val TAG = "ScreenshotHelper"

    interface ScreenshotCallback {
        fun onSuccess(base64: String, file: File?)
        fun onError(message: String)
    }

    /**
     * Capture screenshot of the WebView content
     */
    fun captureWebView(webView: WebView, callback: ScreenshotCallback) {
        try {
            val bitmap = Bitmap.createBitmap(
                webView.width,
                webView.height,
                Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(bitmap)
            webView.draw(canvas)

            // Convert to base64
            val base64 = bitmapToBase64(bitmap)

            // Save to file
            val file = saveBitmapToFile(webView.context.cacheDir, bitmap)

            callback.onSuccess(base64, file)
            bitmap.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Screenshot capture failed: ${e.message}")
            callback.onError("Screenshot failed: ${e.message}")
        }
    }

    /**
     * Capture screenshot using PixelCopy (API 26+) for better quality
     */
    fun captureActivity(activity: Activity, callback: ScreenshotCallback) {
        val view = activity.window.decorView.rootView

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use PixelCopy for better quality
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

            PixelCopy.request(
                activity.window,
                bitmap,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        val base64 = bitmapToBase64(bitmap)
                        val file = saveBitmapToFile(activity.cacheDir, bitmap)
                        callback.onSuccess(base64, file)
                    } else {
                        // Fallback to canvas method
                        captureView(view, activity.cacheDir, callback)
                    }
                    bitmap.recycle()
                },
                Handler(Looper.getMainLooper())
            )
        } else {
            captureView(view, activity.cacheDir, callback)
        }
    }

    /**
     * Capture screenshot using Canvas (fallback method)
     */
    private fun captureView(view: View, cacheDir: File, callback: ScreenshotCallback) {
        try {
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            view.draw(canvas)

            val base64 = bitmapToBase64(bitmap)
            val file = saveBitmapToFile(cacheDir, bitmap)

            callback.onSuccess(base64, file)
            bitmap.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "View capture failed: ${e.message}")
            callback.onError("Capture failed: ${e.message}")
        }
    }

    /**
     * Convert Bitmap to Base64 encoded JPEG string
     */
    private fun bitmapToBase64(bitmap: Bitmap, quality: Int = 85): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val bytes = outputStream.toByteArray()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    /**
     * Save Bitmap to file in cache directory
     */
    private fun saveBitmapToFile(cacheDir: File, bitmap: Bitmap): File? {
        return try {
            val screenshotDir = File(cacheDir, "screenshots")
            if (!screenshotDir.exists()) screenshotDir.mkdirs()

            val filename = "screenshot_${System.currentTimeMillis()}.jpg"
            val file = File(screenshotDir, filename)

            FileOutputStream(file).use { fos ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
            }

            Log.d(TAG, "Screenshot saved to: ${file.absolutePath}")
            file
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save screenshot: ${e.message}")
            null
        }
    }

    /**
     * Upload screenshot to a URL (POST with multipart/form-data or JSON)
     */
    fun uploadScreenshot(
        uploadUrl: String,
        base64Data: String,
        deviceId: String,
        callback: (success: Boolean, message: String) -> Unit
    ) {
        thread {
            try {
                val url = URL(uploadUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    doOutput = true
                    connectTimeout = 30000
                    readTimeout = 30000
                }

                // Create JSON payload
                val payload = JSONObject().apply {
                    put("deviceId", deviceId)
                    put("timestamp", System.currentTimeMillis() / 1000)
                    put("screenshot", base64Data)
                    put("format", "jpeg")
                }.toString()

                connection.outputStream.use { os ->
                    os.write(payload.toByteArray(Charsets.UTF_8))
                }

                val responseCode = connection.responseCode
                val responseMessage = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().readText()
                } else {
                    connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                }

                connection.disconnect()

                Handler(Looper.getMainLooper()).post {
                    if (responseCode in 200..299) {
                        callback(true, "Screenshot uploaded successfully")
                    } else {
                        callback(false, "Upload failed: $responseCode - $responseMessage")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Upload failed: ${e.message}")
                Handler(Looper.getMainLooper()).post {
                    callback(false, "Upload error: ${e.message}")
                }
            }
        }
    }

    /**
     * Create a data object with screenshot info for MQTT publishing
     */
    fun createScreenshotData(base64Data: String, deviceId: String): JSONObject {
        return JSONObject().apply {
            put("deviceId", deviceId)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("screenshot", base64Data)
            put("format", "jpeg")
            put("sizeBytes", base64Data.length)
        }
    }

    /**
     * Clean up old screenshots from cache
     */
    fun cleanupOldScreenshots(cacheDir: File, maxAgeMs: Long = 24 * 60 * 60 * 1000) {
        thread {
            try {
                val screenshotDir = File(cacheDir, "screenshots")
                if (!screenshotDir.exists()) return@thread

                val now = System.currentTimeMillis()
                screenshotDir.listFiles()?.forEach { file ->
                    if (now - file.lastModified() > maxAgeMs) {
                        file.delete()
                        Log.d(TAG, "Deleted old screenshot: ${file.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Cleanup failed: ${e.message}")
            }
        }
    }
}
