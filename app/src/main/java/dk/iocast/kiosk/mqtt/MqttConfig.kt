package dk.iocast.kiosk.mqtt

import dk.iocast.kiosk.IOCastApp

/**
 * MQTT Configuration and topic definitions
 */
data class MqttConfig(
    val brokerUrl: String,
    val username: String,
    val password: String,
    val deviceId: String
) {
    companion object {
        // Topic templates
        private const val TOPIC_STATUS = "devices/{deviceId}/status"
        private const val TOPIC_TELEMETRY = "devices/{deviceId}/telemetry"
        private const val TOPIC_EVENTS = "devices/{deviceId}/events"
        private const val TOPIC_CMD = "devices/{deviceId}/cmd/#"
        private const val TOPIC_CMD_ACK = "devices/{deviceId}/cmd/{cmd}/ack"

        fun fromPrefs(): MqttConfig {
            val prefs = IOCastApp.instance.prefs
            return MqttConfig(
                brokerUrl = prefs.brokerUrl,
                username = prefs.username,
                password = prefs.password,
                deviceId = prefs.deviceId
            )
        }
    }

    // Parse broker URL to get host and port
    val host: String
        get() = brokerUrl
            .replace("tcp://", "")
            .replace("ssl://", "")
            .split(":")[0]

    val port: Int
        get() = try {
            brokerUrl.split(":").lastOrNull()?.toInt() ?: 1883
        } catch (e: Exception) {
            1883
        }

    val useSsl: Boolean
        get() = brokerUrl.startsWith("ssl://")

    val clientId: String
        get() = "iocast-$deviceId"

    // Resolved topics
    val statusTopic: String
        get() = TOPIC_STATUS.replace("{deviceId}", deviceId)

    val telemetryTopic: String
        get() = TOPIC_TELEMETRY.replace("{deviceId}", deviceId)

    val eventsTopic: String
        get() = TOPIC_EVENTS.replace("{deviceId}", deviceId)

    val cmdSubscribeTopic: String
        get() = TOPIC_CMD.replace("{deviceId}", deviceId)

    fun cmdAckTopic(command: String): String {
        return TOPIC_CMD_ACK
            .replace("{deviceId}", deviceId)
            .replace("{cmd}", command)
    }

    // Last Will and Testament (LWT) for offline detection
    val lwtTopic: String
        get() = statusTopic

    val lwtPayload: String
        get() = """{"status":"offline","deviceId":"$deviceId","timestamp":${System.currentTimeMillis()/1000}}"""

    fun isValid(): Boolean {
        return brokerUrl.isNotEmpty() && deviceId.isNotEmpty()
    }
}
