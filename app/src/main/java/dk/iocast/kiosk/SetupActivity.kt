package dk.iocast.kiosk

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject

/**
 * Setup screen for initial device configuration.
 * Supports QR code scanning or manual entry.
 *
 * QR Code JSON format:
 * {
 *   "broker": "mqtt://host:port",
 *   "username": "user",
 *   "password": "pass",
 *   "deviceId": "device-123",
 *   "startUrl": "https://example.com/display"
 * }
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var etBrokerUrl: TextInputEditText
    private lateinit var etDeviceId: TextInputEditText
    private lateinit var etStartUrl: TextInputEditText

    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
        if (result.contents != null) {
            parseQrCode(result.contents)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        etBrokerUrl = findViewById(R.id.etBrokerUrl)
        etDeviceId = findViewById(R.id.etDeviceId)
        etStartUrl = findViewById(R.id.etStartUrl)

        // Load existing values if any
        val prefs = IOCastApp.instance.prefs
        etBrokerUrl.setText(prefs.brokerUrl)
        etDeviceId.setText(prefs.deviceId)
        etStartUrl.setText(prefs.startUrl)

        findViewById<MaterialButton>(R.id.btnScanQr).setOnClickListener {
            startQrScanner()
        }

        findViewById<MaterialButton>(R.id.btnSave).setOnClickListener {
            saveAndStart()
        }
    }

    private fun startQrScanner() {
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Scan QR-kode fra admin dashboard")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        qrScanLauncher.launch(options)
    }

    private fun parseQrCode(contents: String) {
        try {
            val json = JSONObject(contents)

            // Parse broker URL
            val brokerUrl = json.optString("broker", "")
            if (brokerUrl.isNotEmpty()) {
                etBrokerUrl.setText(brokerUrl)
            }

            // Parse device ID
            val deviceId = json.optString("deviceId", "")
            if (deviceId.isNotEmpty()) {
                etDeviceId.setText(deviceId)
            }

            // Parse start URL
            val startUrl = json.optString("startUrl", "")
            if (startUrl.isNotEmpty()) {
                etStartUrl.setText(startUrl)
            }

            // Parse credentials
            val prefs = IOCastApp.instance.prefs
            val username = json.optString("username", "")
            if (username.isNotEmpty()) {
                prefs.mqttUsername = username
            }

            val password = json.optString("password", "")
            if (password.isNotEmpty()) {
                prefs.mqttPassword = password
            }

            Toast.makeText(this, "QR-kode scannet!", Toast.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Toast.makeText(this, "Ugyldig QR-kode: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveAndStart() {
        val brokerUrl = etBrokerUrl.text?.toString()?.trim() ?: ""
        val deviceId = etDeviceId.text?.toString()?.trim() ?: ""
        val startUrl = etStartUrl.text?.toString()?.trim() ?: ""

        // Validate
        if (brokerUrl.isEmpty()) {
            etBrokerUrl.error = "Påkrævet"
            return
        }
        if (deviceId.isEmpty()) {
            etDeviceId.error = "Påkrævet"
            return
        }
        if (startUrl.isEmpty()) {
            etStartUrl.error = "Påkrævet"
            return
        }

        // Save to preferences
        val prefs = IOCastApp.instance.prefs
        prefs.brokerUrl = brokerUrl
        prefs.deviceId = deviceId
        prefs.startUrl = startUrl
        prefs.currentUrl = startUrl
        prefs.isConfigured = true

        // Start main activity
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
