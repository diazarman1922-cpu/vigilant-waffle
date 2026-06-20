package com.example.tiktokunreposter.ui

import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.CookieHeaderBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SessionDiagnosticsActivity : AppCompatActivity() {
    private lateinit var sessionManager: TikTokSessionManager
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = TikTokSessionManager(this)
        buildUi()
        refresh()
    }

    private fun buildUi() {
        output = TextView(this).apply { textSize = 15f }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(output)
        button("Refresh From Saved Session") { refresh() }
        button("Clear Session") {
            sessionManager.clearSession(clearAppWebViewCookies = true)
            refresh("Session cleared. App WebView cookies also cleared.")
        }
        button("Open TikTok Login") { startActivity(Intent(this, LoginWebViewActivity::class.java)) }
        button("Validate Cookie Header") { validateCookieHeader() }
        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun refresh(extra: String? = null) {
        val info = sessionManager.describeSessionSafely()
        output.text = buildString {
            appendLine("TikTok cookies: ${present(info.hasCookies)}")
            appendLine("Cookie count: ${info.cookieCount}")
            appendLine("msToken: ${present(info.hasMsToken)}")
            appendLine("csrf token: ${present(info.hasCsrf)}")
            appendLine("secUid: ${present(info.hasSecUid)}")
            appendLine("Session saved: ${info.savedAt?.let { formatTime(it) } ?: "not found"}")
            appendLine("Sensitive values: hidden")
            extra?.let { appendLine("\n$it") }
        }
    }

    private fun validateCookieHeader() {
        val cookie = sessionManager.getCookieHeader()
        val sanitized = CookieHeaderBuilder.sanitizeCookieHeader(cookie)
        val valid = sanitized.isNotBlank() && sanitized == cookie
        refresh("Cookie header validation: ${if (valid) "valid/sanitized" else "missing or sanitized differently"}. Values are not shown.")
    }

    private fun present(value: Boolean) = if (value) "present" else "not found"

    private fun formatTime(ts: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(ts))
}
