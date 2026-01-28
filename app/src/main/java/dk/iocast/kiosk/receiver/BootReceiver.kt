package dk.iocast.kiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dk.iocast.kiosk.IOCastApp
import dk.iocast.kiosk.MainActivity

/**
 * Receives BOOT_COMPLETED broadcast to auto-start the kiosk app.
 * Only starts if the app has been configured (setup completed).
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d(TAG, "Boot completed, checking if configured...")

            val prefs = IOCastApp.instance.prefs
            if (prefs.isConfigured) {
                Log.d(TAG, "App is configured, starting MainActivity")

                val launchIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                }
                context.startActivity(launchIntent)
            } else {
                Log.d(TAG, "App not configured, skipping auto-start")
            }
        }
    }
}
