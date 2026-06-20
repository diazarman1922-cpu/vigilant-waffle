package com.example.tiktokunreposter.util

import android.util.Log
import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory

object SafeLogger {
    private const val TAG = "TiktokUnreposter"
    private val sensitiveKeys = listOf(
        "cookie",
        "set-cookie",
        "authorization",
        "token",
        "session",
        "csrf",
        "msToken",
        "sid_tt",
        "sessionid",
        "sid_guard"
    )

    fun api(endpointName: String, statusCode: Int? = null, elapsedMs: Long? = null, note: String? = null) {
        val msg = buildString {
            append("endpoint=").append(sanitize(endpointName))
            append(" status=").append(statusCode ?: "n/a")
            append(" elapsedMs=").append(elapsedMs ?: "n/a")
            note?.let { append(" note=").append(sanitize(it)) }
        }
        Log.i(TAG, msg)
    }

    fun error(endpointName: String, category: TikTokApiErrorCategory, statusCode: Int? = null, throwable: Throwable? = null) {
        val msg = "endpoint=${sanitize(endpointName)} category=$category status=${statusCode ?: "n/a"}"
        if (throwable == null) Log.w(TAG, msg) else Log.w(TAG, msg, SafeThrowable(throwable))
    }

    fun sanitize(input: String?): String {
        if (input.isNullOrBlank()) return ""
        var out = input
        sensitiveKeys.forEach { key ->
            val regex = Regex("(?i)($key\\s*[:=]\\s*)[^;\\s&]+")
            out = out.replace(regex, "$1<redacted>")
        }
        out = out.replace(Regex("https://([^/?#]+)([^\\s]*)")) { match ->
            val host = match.groupValues[1]
            val path = match.groupValues[2].substringBefore('?').substringBefore('#')
            "https://$host$path"
        }
        return out.take(700)
    }

    private class SafeThrowable(private val source: Throwable) : Throwable() {
        override val message: String?
            get() = sanitize(source.message ?: source.javaClass.simpleName)
    }
}
