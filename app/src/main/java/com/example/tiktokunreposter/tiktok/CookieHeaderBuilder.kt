package com.example.tiktokunreposter.tiktok

import android.webkit.CookieManager
import java.net.URI

/**
 * Builds Cookie headers only for TikTok-owned hosts. Never log returned values.
 */
class CookieHeaderBuilder {
    fun fromWebViewCookieManager(): String? {
        val cm = CookieManager.getInstance()
        cm.flush()
        val merged = listOfNotNull(
            cm.getCookie("https://www.tiktok.com/"),
            cm.getCookie("https://tiktok.com/"),
            cm.getCookie("https://m.tiktok.com/")
        ).joinToString("; ")
        return sanitize(merged)
    }

    fun buildForTikTokUrl(rawCookieHeader: String?, targetUrl: String): String? {
        if (!isTikTokHost(targetUrl)) return null
        return sanitize(rawCookieHeader)
    }

    fun sanitize(rawCookieHeader: String?): String? {
        if (rawCookieHeader.isNullOrBlank()) return null
        val safePairs = linkedMapOf<String, String>()
        rawCookieHeader
            .replace("\r", "")
            .replace("\n", "")
            .split(';')
            .asSequence()
            .map { it.trim() }
            .filter { it.contains('=') }
            .forEach { pair ->
                val idx = pair.indexOf('=')
                val name = pair.substring(0, idx).trim()
                val value = pair.substring(idx + 1).trim()
                if (isSafeCookieName(name) && isSafeCookieValue(value)) {
                    safePairs[name] = value
                }
            }
        if (safePairs.isEmpty()) return null
        return safePairs.entries
            .take(MAX_COOKIE_PAIRS)
            .joinToString("; ") { (name, value) -> "$name=$value" }
            .take(MAX_COOKIE_HEADER_LENGTH)
    }

    fun looksLikeTikTokLoginCookie(rawCookieHeader: String?): Boolean {
        val sanitized = sanitize(rawCookieHeader) ?: return false
        val names = sanitized.split(';').mapNotNull { it.trim().substringBefore('=', "").takeIf(String::isNotBlank) }.toSet()
        return SESSION_COOKIE_NAMES.any { it in names }
    }

    fun redactedCookieSummary(rawCookieHeader: String?): String {
        val sanitized = sanitize(rawCookieHeader) ?: return "no-cookie"
        val names = sanitized.split(';')
            .mapNotNull { it.trim().substringBefore('=', "").takeIf(String::isNotBlank) }
            .take(12)
        return "cookies=${names.size} names=${names.joinToString(",") { it.take(24) }} values=<redacted>"
    }

    private fun isTikTokHost(targetUrl: String): Boolean {
        val host = runCatching { URI(targetUrl).host?.lowercase() }.getOrNull() ?: return false
        return host == "tiktok.com" || host.endsWith(".tiktok.com")
    }

    private fun isSafeCookieName(name: String): Boolean {
        if (name.isBlank() || name.length > 96) return false
        return name.all { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
    }

    private fun isSafeCookieValue(value: String): Boolean {
        if (value.length > 4096) return false
        return value.none { it == '\r' || it == '\n' || it == ';' }
    }

    companion object {
        private const val MAX_COOKIE_HEADER_LENGTH = 32_000
        private const val MAX_COOKIE_PAIRS = 96
        private val SESSION_COOKIE_NAMES = setOf("sessionid", "sid_tt", "sid_guard", "sessionid_ss", "uid_tt")
    }
}
