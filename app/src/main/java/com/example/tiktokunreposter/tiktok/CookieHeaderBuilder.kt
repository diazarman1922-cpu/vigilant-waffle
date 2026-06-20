package com.example.tiktokunreposter.tiktok

import android.webkit.CookieManager
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object CookieHeaderBuilder {
    private val allowedHosts = listOf("https://www.tiktok.com", "https://tiktok.com")

    fun readTikTokCookieFromWebView(): String? {
        val manager = CookieManager.getInstance()
        val parts = mutableListOf<String>()
        for (url in allowedHosts) {
            val raw = runCatching { manager.getCookie(url) }.getOrNull()
            if (!raw.isNullOrBlank()) parts.add(raw)
        }
        return sanitizeCookieHeader(parts.joinToString("; ")).takeIf { it.isNotBlank() }
    }

    fun sanitizeCookieHeader(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .split(';')
            .mapNotNull { part ->
                val trimmed = part.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                if (!trimmed.contains('=')) return@mapNotNull null
                val name = trimmed.substringBefore('=').trim()
                val value = trimmed.substringAfter('=').trim()
                if (name.isBlank() || value.isBlank()) return@mapNotNull null
                if (name.contains('\n') || name.contains('\r') || value.contains('\n') || value.contains('\r')) return@mapNotNull null
                if (name.length > 128 || value.length > 4096) return@mapNotNull null
                "$name=$value"
            }
            .distinctBy { it.substringBefore('=') }
            .joinToString("; ")
    }

    fun countCookies(raw: String?): Int = sanitizeCookieHeader(raw).split(';').count { it.trim().contains('=') }

    fun hasCookieNamed(raw: String?, name: String): Boolean = sanitizeCookieHeader(raw)
        .split(';')
        .map { it.trim().substringBefore('=') }
        .any { it.equals(name, ignoreCase = true) }

    fun csrfToken(raw: String?): String? = sanitizeCookieHeader(raw)
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("csrf_session_id=", ignoreCase = true) || it.startsWith("tt_csrf_token=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.takeIf { it.isNotBlank() }

    fun buildForUrl(raw: String?, url: String): String? {
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (!TikTokEndpointValidator.canSendCookieTo(parsed)) return null
        return sanitizeCookieHeader(raw).takeIf { it.isNotBlank() }
    }

    fun safeDescription(raw: String?, savedAt: Long? = null, secUid: String? = null): SafeSessionInfo = SafeSessionInfo(
        hasCookies = !sanitizeCookieHeader(raw).isBlank(),
        cookieCount = countCookies(raw),
        hasMsToken = hasCookieNamed(raw, "msToken"),
        hasCsrf = csrfToken(raw) != null,
        hasSecUid = !secUid.isNullOrBlank(),
        savedAt = savedAt
    )
}

data class SafeSessionInfo(
    val hasCookies: Boolean,
    val cookieCount: Int,
    val hasMsToken: Boolean,
    val hasCsrf: Boolean,
    val hasSecUid: Boolean,
    val savedAt: Long?
)
