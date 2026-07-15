package com.fizzy.signage

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private val handler = Handler(Looper.getMainLooper())
    private var isPageLoaded = false

    private val PLAYER_URL = "https://signage.techseventeen.com/signage-tv"

    // Reload the page if nothing loads within 30 seconds
    private val watchdogRunnable = Runnable {
        if (!isPageLoaded) {
            webView.reload()
        }
    }

    // Periodic health-check: reload every 5 minutes in case content stalls
    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            if (isPageLoaded) {
                webView.evaluateJavascript("typeof window.__alive !== 'undefined'") { result ->
                    // Page responds — all good
                }
            }
            handler.postDelayed(this, 5 * 60 * 1000L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Keep screen on forever — this is a signage display
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        )

        goFullscreen()

        // Crash recovery: if the process dies, restart in 3 seconds
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            scheduleRestart()
            android.os.Process.killProcess(android.os.Process.myPid())
        }

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            // Allow mixed content (http assets on https page) — needed for some media URLs
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }

        webView.webChromeClient = object : WebChromeClient() {}

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                isPageLoaded = true
                handler.removeCallbacks(watchdogRunnable)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // Only reload on main-frame errors, not sub-resource failures
                if (request?.isForMainFrame == true) {
                    isPageLoaded = false
                    handler.postDelayed({ view?.loadUrl(PLAYER_URL) }, 5000)
                }
            }
        }

        webView.loadUrl(PLAYER_URL)

        // Start watchdog and health-check
        handler.postDelayed(watchdogRunnable, 30_000)
        handler.postDelayed(healthCheckRunnable, 5 * 60 * 1000L)
    }

    override fun onResume() {
        super.onResume()
        goFullscreen()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
        // If destroyed unexpectedly, relaunch in 3 seconds
        scheduleRestart()
    }

    override fun onBackPressed() {
        // Swallow back — signage should never exit
    }

    private fun goFullscreen() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
        )
    }

    private fun scheduleRestart() {
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pending = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.set(AlarmManager.RTC, System.currentTimeMillis() + 3000, pending)
    }
}
