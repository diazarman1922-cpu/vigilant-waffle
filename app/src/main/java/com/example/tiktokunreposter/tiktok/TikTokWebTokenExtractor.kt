package com.example.tiktokunreposter.tiktok

object TikTokWebTokenExtractor {
    fun extractFromCookie(cookieHeader: String?, knownSecUid: String? = null): TokenExtractionResult {
        val sanitized = CookieHeaderBuilder.sanitizeCookieHeader(cookieHeader)
        val tokens = TikTokWebTokens(
            msTokenPresent = CookieHeaderBuilder.hasCookieNamed(sanitized, "msToken"),
            csrfTokenPresent = CookieHeaderBuilder.csrfToken(sanitized) != null,
            secUid = knownSecUid?.takeIf { it.isNotBlank() }
        )
        return TokenExtractionResult.Unsupported(
            reason = "No anti-bot/signature bypass is implemented. Only cookie-visible tokens are described safely.",
            tokens = tokens
        )
    }

    fun extractSecUidFromHtmlOrUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val patterns = listOf(
            Regex("secUid=([^&\\\"'<>\\s]+)"),
            Regex("\\\"secUid\\\"\\s*:\\s*\\\"([^\\\"]+)\\\""),
            Regex("secUid%3D([^%&\\\"'<>\\s]+)")
        )
        return patterns.asSequence()
            .mapNotNull { it.find(text)?.groupValues?.getOrNull(1) }
            .firstOrNull { it.isNotBlank() }
    }
}
