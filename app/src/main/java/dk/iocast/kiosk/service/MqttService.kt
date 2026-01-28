package dk.iocast.kiosk.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish
import dk.iocast.kiosk.IOCastApp
import dk.iocast.kiosk.MainActivity
import dk.iocast.kiosk.R
import dk.iocast.kiosk.command.CommandHandler
import dk.iocast.kiosk.mqtt.MqttConfig
import dk.iocast.kiosk.util.DeviceInfo
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

/**
 * Foreground service for MQTT connection
 * Handles publish, subscribe, and reconnection
 */
class MqttService : Service() {

    companion object {
        private const val TAG = "MqttService"
        private const val NOTIFICATION_ID = 1
        private const val TELEMETRY_INTERVAL_MS = 60_000L // 1 minute
    }

    private var mqttClient: Mqtt3AsyncClient? = null
    private var config: MqttConfig? = null
    private var isConnected = false

    private val handler = Handler(Looper.getMainLooper())
    private val telemetryRunnable = object : Runnable {
        override fun run() {
            publishTelemetry()
            handler.postDelayed(this, TELEMETRY_INTERVAL_MS)
        }
    }

    private lateinit var commandHandler: CommandHandler

    override fun onCreate() {
        super.onCreate()
        commandHandler = CommandHandler(this)
        Log.d(TAG, "MqttService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        config = MqttConfig.fromPrefs()
        if (config?.isValid() == true) {
            connect()
        } else {
            Log.e(TAG, "Invalid MQTT config")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(telemetryRunnable)
        disconnect()
        Log.d(TAG, "MqttService destroyed")
    }

    private fun createNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, IOCastApp.NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun connect() {
        val cfg = config ?: return

        Log.d(TAG, "Connecting to MQTT broker: ${cfg.host}:${cfg.port}")

        try {
            mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier(cfg.clientId)
                .serverHost(cfg.host)
                .serverPort(cfg.port)
                .automaticReconnectWithDefaultConfig()
                .buildAsync()

            val connectBuilder = mqttClient!!.connectWith()
                .cleanSession(true)
                .keepAlive(60)
                .willPublish()
                    .topic(cfg.lwtTopic)
                    .payload(cfg.lwtPayload.toByteArray())
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .retain(true)
                    .applyWillPublish()

            // Add auth if configured
            if (cfg.username.isNotEmpty()) {
                connectBuilder
                    .simpleAuth()
                    .username(cfg.username)
                    .password(cfg.password.toByteArray())
                    .applySimpleAuth()
            }

            connectBuilder.send()
                .whenComplete { connAck: Mqtt3ConnAck?, throwable: Throwable? ->
                    if (throwable != null) {
                        Log.e(TAG, "MQTT connection failed: ${throwable.message}")
                        isConnected = false
                    } else {
                        Log.d(TAG, "MQTT connected: $connAck")
                        isConnected = true
                        onConnected()
                    }
                }

        } catch (e: Exception) {
            Log.e(TAG, "MQTT connection error: ${e.message}")
        }
    }

    private fun onConnected() {
        val cfg = config ?: return

        // Publish online status
        publishStatus("online")

        // Subscribe to command topics
        subscribeToCommands()

        // Start telemetry publishing
        handler.post(telemetryRunnable)

        // Publish event
        publishEvent("mqttConnected", null)
    }

    private fun subscribeToCommands() {
        val cfg = config ?: return

        mqttClient?.subscribeWith()
            ?.topicFilter(cfg.cmdSubscribeTopic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish: Mqtt3Publish ->
                handleCommand(publish)
            }
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Subscribe failed: ${throwable.message}")
                } else {
                    Log.d(TAG, "Subscribed to: ${cfg.cmdSubscribeTopic}")
                }
            }
    }

    private fun handleCommand(publish: Mqtt3Publish) {
        val topic = publish.topic.toString()
        val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)

        Log.d(TAG, "Received command: $topic -> $payload")

        // Extract command name from topic: devices/{id}/cmd/{command}
        val parts = topic.split("/")
        if (parts.size >= 4) {
            val command = parts[3]
            val result = commandHandler.handle(command, payload)

            // Publish acknowledgment
            publishAck(command, result)
        }
    }

    fun publishStatus(status: String) {
        val cfg = config ?: return
        val payload = JSONObject().apply {
            put("status", status)
            put("deviceId", cfg.deviceId)
            put("timestamp", System.currentTimeMillis() / 1000)
        }.toString()

        publish(cfg.statusTopic, payload, retain = true)
    }

    fun publishTelemetry() {
        val cfg = config ?: return
        val payload = DeviceInfo.collect(this, IOCastApp.instance.prefs.currentUrl).toString()
        publish(cfg.telemetryTopic, payload, retain = true)
    }

    fun publishEvent(eventType: String, data: JSONObject?) {
        val cfg = config ?: return
        val payload = JSONObject().apply {
            put("event", eventType)
            put("deviceId", cfg.deviceId)
            put("timestamp", System.currentTimeMillis() / 1000)
            data?.let { put("data", it) }
        }.toString()

        publish(cfg.eventsTopic, payload, retain = false)
    }

    private fun publishAck(command: String, result: CommandHandler.CommandResult) {
        val cfg = config ?: return
        val payload = JSONObject().apply {
            put("command", command)
            put("success", result.success)
            put("message", result.message)
            put("timestamp", System.currentTimeMillis() / 1000)
        }.toString()

        publish(cfg.cmdAckTopic(command), payload, retain = false)
    }

    private fun publish(topic: String, payload: String, retain: Boolean = false) {
        if (!isConnected) {
            Log.w(TAG, "Not connected, skipping publish to $topic")
            return
        }

        mqttClient?.publishWith()
            ?.topic(topic)
            ?.payload(payload.toByteArray())
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.retain(retain)
            ?.send()
            ?.whenComplete { _, throwable ->
                if (throwable != null) {
                    Log.e(TAG, "Publish failed to $topic: ${throwable.message}")
                } else {
                    Log.d(TAG, "Published to $topic")
                }
            }
    }

    private fun disconnect() {
        handler.removeCallbacks(telemetryRunnable)

        if (isConnected) {
            publishStatus("offline")
        }

        mqttClient?.disconnect()
        mqttClient = null
        isConnected = false
    }
}
