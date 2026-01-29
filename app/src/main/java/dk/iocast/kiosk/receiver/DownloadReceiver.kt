package dk.iocast.kiosk.receiver

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import dk.iocast.kiosk.IOCastApp
import java.io.File

/**
 * Receives download completion events and triggers APK installation
 */
class DownloadReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "DownloadReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) {
            return
        }

        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        val pendingId = IOCastApp.instance.prefs.pendingUpdateDownloadId

        Log.d(TAG, "Download complete. ID: $downloadId, Pending: $pendingId")

        // Check if this is our update download
        if (downloadId != pendingId || downloadId == -1L) {
            Log.d(TAG, "Not our download, ignoring")
            return
        }

        // Clear pending download ID
        IOCastApp.instance.prefs.pendingUpdateDownloadId = -1

        // Verify download was successful
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = downloadManager.query(query)

        if (cursor.moveToFirst()) {
            val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
            val status = cursor.getInt(statusIndex)

            if (status == DownloadManager.STATUS_SUCCESSFUL) {
                Log.i(TAG, "Download successful, triggering installation")
                triggerInstall(context)
            } else {
                Log.e(TAG, "Download failed with status: $status")
            }
        }
        cursor.close()
    }

    private fun triggerInstall(context: Context) {
        try {
            val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "iocast-update.apk")

            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found: ${apkFile.absolutePath}")
                return
            }

            Log.i(TAG, "Installing APK: ${apkFile.absolutePath} (${apkFile.length()} bytes)")

            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(installIntent)
            Log.i(TAG, "Installation intent sent")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to trigger install: ${e.message}", e)
        }
    }
}
