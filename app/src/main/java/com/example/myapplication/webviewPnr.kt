package com.example.myapplication

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class webviewPnr : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loader: ProgressBar
    private lateinit var originalPnrUrl: String
    private val handler = Handler(Looper.getMainLooper())
    private var verificationCompleted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_webview_pnr)

        // Get references
        loader = findViewById(R.id.loader)

        val pnr = intent.getStringExtra("PNR") ?: ""
        val surname = intent.getStringExtra("SURNAME") ?: ""
        originalPnrUrl = "https://www.spicejet.com/checkin/trip-details?pnr=$pnr&last=$surname"

        // Create hidden WebView
        webView = WebView(this).apply {
            layoutParams = ViewGroup.LayoutParams(1, 1)
            alpha = 0f
        }
        (findViewById<ViewGroup>(android.R.id.content)).addView(webView)

        setupWebView()
        startVerification()
    }

    private fun setupWebView() {
        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = true
        }

        webView.webViewClient = object : WebViewClient() {
            private var currentUrl: String? = null

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                currentUrl = request.url.toString()
                Log.d("URL_TRACKING", "Loading: $currentUrl")
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                currentUrl = url
                Log.d("PAGE_LOADED", "Finished: $url")

                if (!verificationCompleted) {
                    handler.postDelayed({
                        getFinalUrl()
                    }, 10000) // wait for all redirects to finish
                }
            }
        }
    }

    private fun startVerification() {
        Toast.makeText(this, "Verifying PNR...", Toast.LENGTH_SHORT).show()
        verificationCompleted = false
        loader.visibility = View.VISIBLE
        webView.loadUrl(originalPnrUrl)
    }

    private fun getFinalUrl() {
        webView.evaluateJavascript("window.location.href") { finalUrl ->
            val cleanUrl = finalUrl.replace("\"", "")
            Log.d("FINAL_URL", "JavaScript URL: $cleanUrl")
            verifyPnr(cleanUrl)
        }
    }

    private fun verifyPnr(finalUrl: String) {
        verificationCompleted = true

        val isValid = finalUrl == originalPnrUrl
        Log.d("VERIFICATION", "Original: $originalPnrUrl\nFinal: $finalUrl\nValid: $isValid")

        runOnUiThread {
            loader.visibility = View.GONE
            if (isValid) {
                Toast.makeText(this, "✅ Valid PNR", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "❌ Invalid PNR", Toast.LENGTH_LONG).show()
            }
            finish()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        webView.destroy()
        super.onDestroy()
    }
}
