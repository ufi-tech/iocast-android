package dk.iocast.kiosk

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import dk.iocast.kiosk.service.MqttService
import dk.iocast.kiosk.util.ScreenshotHelper
import dk.iocast.kiosk.webview.JsInterface

/**
 * Main kiosk activity with fullscreen WebView
 * Features:
 * - Offline clock display when network is unavailable
 * - Auto-reload when network returns
 * - WiFi setup access
 */
class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var offlineClockLayout: LinearLayout
    private lateinit var offlineStatus: TextView
    private lateinit var wifiSetupButton: Button

    private val handler = Handler(Looper.getMainLooper())
    private var isNetworkAvailable = true
    private var pendingUrlToLoad: String? = null

    // Network callback for monitoring connectivity changes
    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available")
            handler.post { onNetworkRestored() }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost")
            handler.post { onNetworkLost() }
        }

        override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            Log.d(TAG, "Network capabilities changed: internet=$hasInternet, validated=$validated")
            if (hasInternet && validated) {
                handler.post { onNetworkRestored() }
            }
        }
    }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.let { handleCommand(it) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if setup is complete - use TV-friendly setup with numpad
        if (!IOCastApp.instance.prefs.isSetupComplete) {
            startActivity(Intent(this, SetupTvActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)

        setupFullscreen()
        setupViews()
        setupWebView()
        setupNetworkMonitor()
        registerCommandReceiver()
        startMqttService()

        // Check initial network state and load URL
        if (isNetworkConnected()) {
            loadUrl(IOCastApp.instance.prefs.currentUrl)
        } else {
            showOfflineClock()
            pendingUrlToLoad = IOCastApp.instance.prefs.currentUrl
        }
    }

    private fun setupViews() {
        offlineClockLayout = findViewById(R.id.offlineClockLayout)
        offlineStatus = findViewById(R.id.offlineStatus)
        wifiSetupButton = findViewById(R.id.wifiSetupButton)

        wifiSetupButton.setOnClickListener {
            openWifiSettings()
        }
    }

    private fun setupNetworkMonitor() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)

        // Set initial state
        isNetworkAvailable = isNetworkConnected()
    }

    private fun isNetworkConnected(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    private fun onNetworkRestored() {
        if (!isNetworkAvailable) {
            Log.d(TAG, "Network restored - reloading content")
            isNetworkAvailable = true
            showWebView()

            // Reload the pending URL or current URL
            val urlToLoad = pendingUrlToLoad ?: IOCastApp.instance.prefs.currentUrl
            pendingUrlToLoad = null

            // Small delay to ensure network is stable
            handler.postDelayed({
                if (isNetworkAvailable) {
                    loadUrl(urlToLoad)
                }
            }, 1500)
        }
    }

    private fun onNetworkLost() {
        if (isNetworkAvailable) {
            Log.d(TAG, "Network lost - showing offline clock")
            isNetworkAvailable = false
            pendingUrlToLoad = webView.url ?: IOCastApp.instance.prefs.currentUrl
            showOfflineClock()
        }
    }

    private fun showOfflineClock() {
        runOnUiThread {
            webView.visibility = View.GONE
            progressBar.visibility = View.GONE
            offlineClockLayout.visibility = View.VISIBLE
            offlineStatus.text = "Ingen internetforbindelse"
        }
    }

    private fun showWebView() {
        runOnUiThread {
            offlineClockLayout.visibility = View.GONE
            webView.visibility = View.VISIBLE
        }
    }

    private fun openWifiSettings() {
        try {
            // Try Android TV WiFi settings first
            val intent = Intent(Settings.ACTION_WIFI_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Could not open WiFi settings: ${e.message}")
            try {
                // Fallback to general wireless settings
                val fallbackIntent = Intent(Settings.ACTION_WIRELESS_SETTINGS)
                startActivity(fallbackIntent)
            } catch (e2: Exception) {
                Toast.makeText(this, "Kan ikke åbne WiFi indstillinger", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setupFullscreen() {
        // Keep screen on
        if (IOCastApp.instance.prefs.keepScreenOn) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        // Hide system UI
        WindowCompat.setDecorFitsSystemWindows(window, false)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.systemBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = false
            displayZoomControls = false
            allowFileAccess = true
            allowContentAccess = true
        }

        // Add JavaScript interface
        webView.addJavascriptInterface(JsInterface(this), "iocast")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                url?.let { IOCastApp.instance.prefs.currentUrl = it }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    progressBar.visibility = View.GONE
                    Log.d(TAG, "WebView error: ${error?.description}")

                    // Show offline clock on network errors
                    if (!isNetworkConnected()) {
                        pendingUrlToLoad = request.url?.toString() ?: IOCastApp.instance.prefs.currentUrl
                        showOfflineClock()
                    }
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.progress = newProgress
            }
        }
    }

    private fun registerCommandReceiver() {
        val filter = IntentFilter().apply {
            addAction("dk.iocast.kiosk.COMMAND")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(commandReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(commandReceiver, filter)
        }
    }

    private fun startMqttService() {
        val serviceIntent = Intent(this, MqttService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    private fun handleCommand(intent: Intent) {
        val command = intent.getStringExtra("command") ?: return
        val payload = intent.getStringExtra("payload")

        when (command) {
            "loadUrl" -> {
                payload?.let { loadUrl(it) }
            }
            "reload" -> {
                webView.reload()
            }
            "goBack" -> {
                if (webView.canGoBack()) webView.goBack()
            }
            "goForward" -> {
                if (webView.canGoForward()) webView.goForward()
            }
            "loadStartUrl" -> {
                loadUrl(IOCastApp.instance.prefs.startUrl)
            }
            "clearCache" -> {
                webView.clearCache(true)
                webView.reload()
            }
            "screenshot" -> {
                takeScreenshot()
            }
        }
    }

    fun loadUrl(url: String) {
        runOnUiThread {
            webView.loadUrl(url)
        }
    }

    fun reload() {
        runOnUiThread {
            webView.reload()
        }
    }

    fun getCurrentUrl(): String {
        return webView.url ?: IOCastApp.instance.prefs.startUrl
    }

    private fun takeScreenshot() {
        Log.d(TAG, "Taking screenshot...")

        // Capture the WebView content
        ScreenshotHelper.captureWebView(webView, object : ScreenshotHelper.ScreenshotCallback {
            override fun onSuccess(base64: String, file: java.io.File?) {
                Log.d(TAG, "Screenshot captured, size: ${base64.length} bytes")

                // Send broadcast with screenshot data for MqttService to publish
                val intent = Intent("dk.iocast.kiosk.SCREENSHOT_RESULT").apply {
                    putExtra("base64", base64)
                    putExtra("success", true)
                    file?.let { putExtra("filePath", it.absolutePath) }
                }
                sendBroadcast(intent)
            }

            override fun onError(message: String) {
                Log.e(TAG, "Screenshot failed: $message")
                val intent = Intent("dk.iocast.kiosk.SCREENSHOT_RESULT").apply {
                    putExtra("success", false)
                    putExtra("error", message)
                }
                sendBroadcast(intent)
            }
        })
    }

    /**
     * Get WebView for external screenshot capture
     */
    fun getWebView(): WebView? {
        return if (::webView.isInitialized) webView else null
    }

    // Block back button in kiosk mode
    override fun onBackPressed() {
        if (IOCastApp.instance.prefs.kioskMode) {
            // In kiosk mode, go back in WebView history or do nothing
            if (webView.canGoBack()) {
                webView.goBack()
            }
            // Don't call super to prevent app exit
        } else {
            super.onBackPressed()
        }
    }

    // Block volume keys optionally
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (IOCastApp.instance.prefs.kioskMode) {
            when (keyCode) {
                KeyEvent.KEYCODE_HOME,
                KeyEvent.KEYCODE_APP_SWITCH -> return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        setupFullscreen()
        if (::webView.isInitialized) {
            webView.onResume()
        }
    }

    override fun onPause() {
        super.onPause()
        if (::webView.isInitialized) {
            webView.onPause()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(commandReceiver)
        } catch (e: Exception) {
            // Receiver not registered
        }
        try {
            val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            connectivityManager.unregisterNetworkCallback(networkCallback)
        } catch (e: Exception) {
            // Callback not registered
        }
        handler.removeCallbacksAndMessages(null)
        if (::webView.isInitialized) {
            webView.destroy()
        }
    }
}
