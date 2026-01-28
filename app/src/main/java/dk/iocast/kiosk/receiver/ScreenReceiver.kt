package dk.iocast.kiosk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives screen on/off events for telemetry tracking.
 * Used to monitor device activity and report to admin platform.
 */
class ScreenReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "ScreenReceiver"
        const val ACTION_SCREEN_STATE = "dk.iocast.kiosk.SCREEN_STATE"
        const val EXTRA_SCREEN_ON = "screen_on"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_SCREEN_ON -> {
                Log.d(TAG, "Screen turned ON")
                broadcastScreenState(context, true)
            }
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned OFF")
                broadcastScreenState(context, false)
            }
        }
    }

    private fun broadcastScreenState(context: Context, isOn: Boolean) {
        val stateIntent = Intent(ACTION_SCREEN_STATE).apply {
            putExtra(EXTRA_SCREEN_ON, isOn)
        }
        context.sendBroadcast(stateIntent)
    }
}
