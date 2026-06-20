package com.example.tiktokunreposter.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.CookieHeaderBuilder
import com.example.tiktokunreposter.tiktok.TikTokWebTokenExtractor
import com.example.tiktokunreposter.tiktok.TokenExtractionResult

class LoginWebViewActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var sessionManager: TikTokSessionManager
    private val cookieHeaderBuilder = CookieHeaderBuilder()
    private val tokenExtractor = TikTokWebTokenExtractor

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = TikTokSessionManager(this)

        status = TextView(this).apply {
            text = "Login TikTok di WebView ini. Setelah login, buka profile kamu di TikTok Web, lalu tap Save Session. Cookie disimpan lokal terenkripsi dan tidak ditampilkan."
            textSize = 14f
        }
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            webViewClient = WebViewClient()
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        val openHome = Button(this).apply {
            text = "Open TikTok Home"
            setOnClickListener { webView.loadUrl("https://www.tiktok.com/") }
        }
        val openProfile = Button(this).apply {
            text = "Open /profile"
            setOnClickListener { webView.loadUrl("https://www.tiktok.com/profile") }
        }
        val save = Button(this).apply {
            text = "I'm logged in — Save Session Locally"
            setOnClickListener { saveLocalSession() }
        }
        val clear = Button(this).apply {
            text = "Clear WebView Cookies + Local Session"
            setOnClickListener {
                sessionManager.clearSession()
                status.text = "Session cleared."
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            addView(status)
            addView(openHome)
            addView(openProfile)
            addView(save)
            addView(clear)
            addView(webView, LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        setContentView(root)
        webView.loadUrl("https://www.tiktok.com/")
    }

    private fun saveLocalSession() {
        CookieManager.getInstance().flush()
        val cookie = cookieHeaderBuilder.fromWebViewCookieManager()
        if (cookie.isNullOrBlank()) {
            status.text = "Cookie TikTok belum ada. Login dulu di WebView ini."
            return
        }
        if (!cookieHeaderBuilder.looksLikeTikTokLoginCookie(cookie)) {
            status.text = "Cookie TikTok ada, tapi belum kelihatan seperti session login. Pastikan sudah login dan halaman profile terbuka."
            return
        }

        webView.evaluateJavascript(tokenExtractor.buildDomExtractionScript()) { raw ->
            val domTokens = tokenExtractor.parseDomExtractionResult(raw)
            val cookieTokens = tokenExtractor.extractFromCookieHeader(cookie)
            val secUid = domTokens.secUidOrNull() ?: cookieTokens.secUidOrNull()
            sessionManager.saveSession(
                cookieHeader = cookie,
                secUid = secUid,
                userAgent = webView.settings.userAgentString
            )
            status.text = if (secUid.isNullOrBlank()) {
                "Session saved encrypted. secUid belum kebaca; buka profile sendiri lalu tap Save lagi kalau experimental endpoint dipakai."
            } else {
                "Session saved encrypted. secUid captured. Cookie/token tidak ditampilkan."
            }
        }
    }

    private fun TokenExtractionResult.secUidOrNull(): String? = when (this) {
        is TokenExtractionResult.Supported -> tokens.secUid
        is TokenExtractionResult.Unsupported -> tokens.secUid
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
