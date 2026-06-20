package com.example.tiktokunreposter.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.CookieHeaderBuilder
import com.example.tiktokunreposter.tiktok.TikTokEndpointValidator
import com.example.tiktokunreposter.tiktok.TikTokWebTokenExtractor

class LoginWebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var sessionManager: TikTokSessionManager
    private var lastSecUid: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = TikTokSessionManager(this)
        status = TextView(this).apply { textSize = 14f }
        webView = WebView(this)

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                val allowed = uri.scheme == "https" && TikTokEndpointValidator.isAllowedTikTokHost(uri.host.orEmpty())
                if (!allowed) {
                    status.text = "Blocked navigation outside TikTok: ${uri.host.orEmpty()}"
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                lastSecUid = TikTokWebTokenExtractor.extractSecUidFromHtmlOrUrl(url)
                    ?: lastSecUid
                extractSecUidFromDomSafely()
                status.text = "Loaded TikTok page. Kalau sudah login, tap Save Session. Cookie/token tidak ditampilkan."
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 32, 20, 20)
        }
        root.addView(status)
        root.addView(Button(this).apply {
            text = "I'm logged in — Save Session Locally"
            setOnClickListener { saveSession() }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(Button(this).apply {
            text = "Open TikTok Home"
            setOnClickListener { webView.loadUrl("https://www.tiktok.com/") }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(Button(this).apply {
            text = "Open TikTok Profile"
            setOnClickListener { webView.loadUrl("https://www.tiktok.com/profile") }
        }, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        root.addView(webView, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        })

        webView.loadUrl("https://www.tiktok.com/")
    }

    private fun extractSecUidFromDomSafely() {
        // No JavaScript interface is exposed. This only reads page text locally and extracts secUid if visible.
        webView.evaluateJavascript("document.documentElement.innerHTML.slice(0, 200000)") { encoded ->
            val decoded = encoded
                ?.removePrefix("\"")
                ?.removeSuffix("\"")
                ?.replace("\\\"", "\"")
                ?.replace("\\u0026", "&")
            lastSecUid = TikTokWebTokenExtractor.extractSecUidFromHtmlOrUrl(decoded) ?: lastSecUid
        }
    }

    private fun saveSession() {
        val cookie = CookieHeaderBuilder.readTikTokCookieFromWebView()
        if (cookie.isNullOrBlank()) {
            status.text = "Tidak ada TikTok cookie dari WebView app ini. Login dulu di halaman TikTok."
            return
        }
        CookieManager.getInstance().flush()
        runCatching {
            sessionManager.saveSession(cookie, lastSecUid)
        }.onSuccess {
            val info = sessionManager.describeSessionSafely()
            status.text = "Session saved encrypted. cookies=${info.cookieCount} msToken=${present(info.hasMsToken)} csrf=${present(info.hasCsrf)} secUid=${present(info.hasSecUid)}"
        }.onFailure {
            status.text = "Gagal simpan session secara aman. Cookie/token tidak ditampilkan."
        }
    }

    private fun present(value: Boolean) = if (value) "present" else "not found"

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
