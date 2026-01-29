package dk.iocast.kiosk.config

/**
 * Hardcoded MQTT broker configuration for device provisioning.
 * These settings are baked into the APK - devices connect to this broker
 * to register themselves using customer codes.
 */
object ProvisionConfig {
    // MQTT Broker settings (Synology MQTT broker)
    const val MQTT_BROKER_HOST = "188.228.60.134"
    const val MQTT_BROKER_PORT = 1883
    const val MQTT_USERNAME = "admin"
    const val MQTT_PASSWORD = "BZs9UBDViukWaZu+1O6Hd77qr+Dshomu"

    // Topic prefixes
    const val PROVISION_TOPIC_PREFIX = "provision/"

    /**
     * Topic where device publishes its provision request.
     * Format: provision/{customerCode}/request
     */
    fun provisionRequestTopic(customerCode: String) = "${PROVISION_TOPIC_PREFIX}${customerCode}/request"

    /**
     * Topic where device listens for provision response.
     * Format: provision/{customerCode}/response/{deviceId}
     */
    fun provisionResponseTopic(customerCode: String, deviceId: String) =
        "${PROVISION_TOPIC_PREFIX}${customerCode}/response/${deviceId}"
}
