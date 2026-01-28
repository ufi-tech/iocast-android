package dk.iocast.kiosk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dk.iocast.kiosk.util.Prefs

/**
 * IOCast Application class
 * Initializes app-wide components and notification channels
 */
class IOCastApp : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "iocast_service"
        lateinit var instance: IOCastApp
            private set
    }

    lateinit var prefs: Prefs
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }
}
