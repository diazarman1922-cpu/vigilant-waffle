package com.example.tiktokunreposter.tiktok

import okhttp3.HttpUrl

object TikTokEndpointValidator {
    private val allowedHosts = setOf("tiktok.com", "www.tiktok.com")

    fun validateUrl(url: HttpUrl): ValidationResult {
        if (url.scheme != "https") {
            val localhost = url.host == "localhost" || url.host == "127.0.0.1"
            if (!localhost) return ValidationResult(false, "Only HTTPS TikTok endpoints are allowed")
        }
        if (!isAllowedTikTokHost(url.host)) {
            return ValidationResult(false, "Host is not allowed: ${url.host}")
        }
        return ValidationResult(true)
    }

    fun validateRedirect(from: HttpUrl, to: HttpUrl): ValidationResult {
        val target = validateUrl(to)
        if (!target.allowed) return ValidationResult(false, "Blocked redirect from ${from.host} to ${to.host}")
        return ValidationResult(true)
    }

    fun canSendCookieTo(url: HttpUrl): Boolean = isAllowedTikTokHost(url.host)

    fun isAllowedTikTokHost(host: String): Boolean {
        val normalized = host.lowercase().trimEnd('.')
        if (normalized in allowedHosts) return true
        return normalized.endsWith(".tiktok.com")
    }
}

data class ValidationResult(
    val allowed: Boolean,
    val reason: String? = null
)
