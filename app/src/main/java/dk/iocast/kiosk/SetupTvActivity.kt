package dk.iocast.kiosk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.hivemq.client.mqtt.MqttClient
import com.hivemq.client.mqtt.datatypes.MqttQos
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient
import dk.iocast.kiosk.config.ProvisionConfig
import dk.iocast.kiosk.util.DeviceInfo
import dk.iocast.kiosk.util.DeviceType
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TV-friendly Setup Activity using numpad for entering provision codes.
 * Designed for Android TV remote control navigation.
 *
 * Flow:
 * 1. User enters 4-digit customer code using numpad or D-pad
 * 2. App connects to central MQTT broker
 * 3. Sends provision request with device info
 * 4. Waits for admin approval
 * 5. Receives configuration and starts main activity
 */
class SetupTvActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SetupTvActivity"
        private const val CODE_LENGTH = 4
    }

    // UI elements
    private lateinit var codeDisplay: TextView
    private lateinit var statusText: TextView
    private lateinit var loadingContainer: View
    private lateinit var loadingText: TextView
    private lateinit var waitingContainer: View
    private lateinit var waitingDeviceId: TextView
    private lateinit var waitingCustomerName: TextView
    private lateinit var numpadGrid: View

    // State
    private var enteredCode = ""
    private var mqttClient: Mqtt3AsyncClient? = null
    private var deviceId: String = ""
    private var isTV = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup_tv)

        // Initialize device ID
        deviceId = IOCastApp.instance.prefs.deviceId

        // Detect device type
        isTV = DeviceType.isTV(this)
        Log.d(TAG, "Device type: ${DeviceType.getDescription(this)}, isTV=$isTV")

        // Find views
        codeDisplay = findViewById(R.id.codeDisplay)
        statusText = findViewById(R.id.statusText)
        loadingContainer = findViewById(R.id.loadingContainer)
        loadingText = findViewById(R.id.loadingText)
        waitingContainer = findViewById(R.id.waitingContainer)
        waitingDeviceId = findViewById(R.id.waitingDeviceId)
        waitingCustomerName = findViewById(R.id.waitingCustomerName)
        numpadGrid = findViewById(R.id.numpadGrid)

        // Setup UI based on device type
        setupForDeviceType()

        // Setup numpad buttons (always, for touch fallback)
        setupNumpadButtons()

        // Update display
        updateCodeDisplay()
    }

    /**
     * Configure UI based on device type:
     * - TV: Hide numpad, use remote number keys directly
     * - Tablet/Phone: Show numpad for touch input
     */
    private fun setupForDeviceType() {
        if (isTV) {
            // On TV: Hide numpad, user uses remote control number keys
            numpadGrid.visibility = View.GONE
            statusText.text = "Brug fjernbetjeningen til at indtaste koden"

            // Find subtitle and update it
            findViewById<TextView>(R.id.subtitleText)?.text =
                "Tryk på tallene på fjernbetjeningen"
        } else {
            // On tablet/phone: Show numpad for touch
            numpadGrid.visibility = View.VISIBLE
            statusText.text = "Tryk på tallene for at indtaste koden"
        }
    }

    private fun setupNumpadButtons() {
        // Number buttons 0-9
        val numberButtons = listOf(
            R.id.btn0, R.id.btn1, R.id.btn2, R.id.btn3, R.id.btn4,
            R.id.btn5, R.id.btn6, R.id.btn7, R.id.btn8, R.id.btn9
        )

        numberButtons.forEachIndexed { index, buttonId ->
            findViewById<Button>(buttonId).setOnClickListener {
                onNumberPressed(index)
            }
        }

        // Backspace button
        findViewById<Button>(R.id.btnBackspace).setOnClickListener {
            onBackspacePressed()
        }

        // Confirm button
        findViewById<Button>(R.id.btnConfirm).setOnClickListener {
            onConfirmPressed()
        }

        // Request focus on button 1 for D-pad navigation
        findViewById<Button>(R.id.btn1).requestFocus()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Handle number keys from remote
        when (keyCode) {
            KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_NUMPAD_0 -> {
                onNumberPressed(0)
                return true
            }
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> {
                onNumberPressed(1)
                return true
            }
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> {
                onNumberPressed(2)
                return true
            }
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> {
                onNumberPressed(3)
                return true
            }
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> {
                onNumberPressed(4)
                return true
            }
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> {
                onNumberPressed(5)
                return true
            }
            KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_NUMPAD_6 -> {
                onNumberPressed(6)
                return true
            }
            KeyEvent.KEYCODE_7, KeyEvent.KEYCODE_NUMPAD_7 -> {
                onNumberPressed(7)
                return true
            }
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_NUMPAD_8 -> {
                onNumberPressed(8)
                return true
            }
            KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_NUMPAD_9 -> {
                onNumberPressed(9)
                return true
            }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_BACK -> {
                if (enteredCode.isNotEmpty()) {
                    onBackspacePressed()
                    return true
                }
            }
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (enteredCode.length == CODE_LENGTH) {
                    onConfirmPressed()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun onNumberPressed(number: Int) {
        if (enteredCode.length < CODE_LENGTH) {
            enteredCode += number.toString()
            updateCodeDisplay()

            // Auto-submit when 4 digits entered
            if (enteredCode.length == CODE_LENGTH) {
                statusText.text = "Tryk ✓ for at bekræfte eller ← for at rette"
            }
        }
    }

    private fun onBackspacePressed() {
        if (enteredCode.isNotEmpty()) {
            enteredCode = enteredCode.dropLast(1)
            updateCodeDisplay()
            statusText.text = "Tryk på tal-knapper eller naviger med D-pad"
        }
    }

    private fun updateCodeDisplay() {
        val display = StringBuilder()
        for (i in 0 until CODE_LENGTH) {
            display.append(if (i < enteredCode.length) enteredCode[i] else "_")
            if (i < CODE_LENGTH - 1) display.append(" ")
        }
        codeDisplay.text = display.toString()
    }

    private fun onConfirmPressed() {
        if (enteredCode.length != CODE_LENGTH) {
            Toast.makeText(this, "Indtast alle 4 cifre", Toast.LENGTH_SHORT).show()
            return
        }

        startProvisioning(enteredCode)
    }

    private fun startProvisioning(customerCode: String) {
        Log.d(TAG, "Starting provisioning with code: $customerCode")

        // Show loading state
        showLoading("Forbinder til server...")

        // Connect to MQTT and send provision request
        connectAndProvision(customerCode)
    }

    private fun connectAndProvision(customerCode: String) {
        try {
            // Create MQTT client
            mqttClient = MqttClient.builder()
                .useMqttVersion3()
                .identifier("iocast-setup-${UUID.randomUUID().toString().take(8)}")
                .serverHost(ProvisionConfig.MQTT_BROKER_HOST)
                .serverPort(ProvisionConfig.MQTT_BROKER_PORT)
                .buildAsync()

            // Connect with credentials
            mqttClient?.connectWith()
                ?.simpleAuth()
                ?.username(ProvisionConfig.MQTT_USERNAME)
                ?.password(ProvisionConfig.MQTT_PASSWORD.toByteArray())
                ?.applySimpleAuth()
                ?.send()
                ?.whenComplete { _, throwable ->
                    runOnUiThread {
                        if (throwable != null) {
                            Log.e(TAG, "MQTT connection failed", throwable)
                            showError("Forbindelse fejlede: ${throwable.message}")
                        } else {
                            Log.d(TAG, "MQTT connected, subscribing to response topic")
                            subscribeAndSendRequest(customerCode)
                        }
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create MQTT client", e)
            showError("Kunne ikke oprette forbindelse: ${e.message}")
        }
    }

    private fun subscribeAndSendRequest(customerCode: String) {
        val responseTopic = ProvisionConfig.provisionResponseTopic(customerCode, deviceId)

        // Subscribe to response topic first
        mqttClient?.subscribeWith()
            ?.topicFilter(responseTopic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.callback { publish ->
                val payload = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
                Log.d(TAG, "Received provision response: $payload")
                runOnUiThread {
                    handleProvisionResponse(payload)
                }
            }
            ?.send()
            ?.whenComplete { _, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        Log.e(TAG, "Subscribe failed", throwable)
                        showError("Abonnement fejlede: ${throwable.message}")
                    } else {
                        Log.d(TAG, "Subscribed to: $responseTopic")
                        sendProvisionRequest(customerCode)
                    }
                }
            }
    }

    private fun sendProvisionRequest(customerCode: String) {
        showLoading("Sender anmodning...")

        // Collect device info
        val deviceInfo = DeviceInfo.collect(this)

        // Create provision request
        val request = JSONObject().apply {
            put("deviceId", deviceId)
            put("customerCode", customerCode)
            put("timestamp", System.currentTimeMillis() / 1000)
            put("deviceInfo", deviceInfo)
        }

        val requestTopic = ProvisionConfig.provisionRequestTopic(customerCode)

        mqttClient?.publishWith()
            ?.topic(requestTopic)
            ?.qos(MqttQos.AT_LEAST_ONCE)
            ?.payload(request.toString().toByteArray())
            ?.send()
            ?.whenComplete { _, throwable ->
                runOnUiThread {
                    if (throwable != null) {
                        Log.e(TAG, "Publish failed", throwable)
                        showError("Kunne ikke sende anmodning: ${throwable.message}")
                    } else {
                        Log.d(TAG, "Provision request sent to: $requestTopic")
                        showWaitingForApproval(customerCode)
                    }
                }
            }
    }

    private fun handleProvisionResponse(payload: String) {
        try {
            val response = JSONObject(payload)

            // Handle both formats:
            // Format 1: {"status": "approved", "config": {...}}
            // Format 2: {"approved": true, "startUrl": "...", ...}
            val status = response.optString("status", "")
            val isApproved = response.optBoolean("approved", false)

            when {
                status == "approved" -> {
                    Log.d(TAG, "Device approved (status field)!")
                    val config = response.optJSONObject("config")
                    if (config != null) {
                        saveConfigAndStart(config)
                    } else {
                        showError("Ugyldig konfiguration modtaget")
                    }
                }
                isApproved -> {
                    Log.d(TAG, "Device approved (approved field)!")
                    // Config is at root level, not nested
                    saveConfigAndStart(response)
                }
                status == "rejected" -> {
                    val reason = response.optString("reason", "Ukendt årsag")
                    showError("Anmodning afvist: $reason")
                }
                status == "pending" -> {
                    val customerName = response.optString("customerName", "")
                    if (customerName.isNotEmpty()) {
                        waitingCustomerName.text = "Kunde: $customerName"
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown status in response: $payload")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse provision response", e)
            showError("Fejl i svar fra server")
        }
    }

    private fun saveConfigAndStart(config: JSONObject) {
        Log.d(TAG, "Saving configuration and starting main activity")

        val prefs = IOCastApp.instance.prefs

        // Save all configuration
        config.optString("startUrl").takeIf { it.isNotEmpty() }?.let {
            prefs.startUrl = it
            prefs.currentUrl = it
        }

        config.optString("brokerUrl").takeIf { it.isNotEmpty() }?.let {
            prefs.brokerUrl = it
        }

        config.optString("username").takeIf { it.isNotEmpty() }?.let {
            prefs.mqttUsername = it
        }

        config.optString("password").takeIf { it.isNotEmpty() }?.let {
            prefs.mqttPassword = it
        }

        // Mark as configured
        prefs.isConfigured = true

        // Disconnect MQTT
        mqttClient?.disconnect()
        mqttClient = null

        // Show success briefly
        Toast.makeText(this, "Godkendt! Starter...", Toast.LENGTH_SHORT).show()

        // Start main activity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showLoading(message: String) {
        numpadGrid.visibility = View.GONE
        codeDisplay.visibility = View.GONE
        statusText.visibility = View.GONE
        waitingContainer.visibility = View.GONE

        loadingText.text = message
        loadingContainer.visibility = View.VISIBLE
    }

    private fun showWaitingForApproval(customerCode: String) {
        numpadGrid.visibility = View.GONE
        codeDisplay.visibility = View.GONE
        statusText.visibility = View.GONE
        loadingContainer.visibility = View.GONE

        waitingDeviceId.text = "Device: $deviceId"
        waitingCustomerName.text = "Kunde-kode: $customerCode"
        waitingContainer.visibility = View.VISIBLE
    }

    private fun showError(message: String) {
        Log.e(TAG, "Error: $message")

        // Reset to input state
        loadingContainer.visibility = View.GONE
        waitingContainer.visibility = View.GONE

        // Show numpad only on non-TV devices
        numpadGrid.visibility = if (isTV) View.GONE else View.VISIBLE
        codeDisplay.visibility = View.VISIBLE
        statusText.visibility = View.VISIBLE

        statusText.text = message
        statusText.setTextColor(getColor(android.R.color.holo_red_light))

        // Reset code
        enteredCode = ""
        updateCodeDisplay()

        // Disconnect MQTT if connected
        mqttClient?.disconnect()
        mqttClient = null

        // Reset status color after delay
        statusText.postDelayed({
            statusText.setTextColor(getColor(android.R.color.darker_gray))
            statusText.text = if (isTV) {
                "Brug fjernbetjeningen til at indtaste koden"
            } else {
                "Tryk på tallene for at indtaste koden"
            }
        }, 5000)
    }

    override fun onDestroy() {
        super.onDestroy()
        mqttClient?.disconnect()
        mqttClient = null
    }
}
