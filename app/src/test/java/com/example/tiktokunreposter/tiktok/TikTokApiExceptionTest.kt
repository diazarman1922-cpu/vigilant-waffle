package com.example.tiktokunreposter.tiktok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TikTokApiExceptionTest {
    @Test
    fun mapsStatusCodeSafely() {
        assertEquals(TikTokApiErrorCategory.LoginExpired, TikTokApiException.fromStatusCode(401).category)
        assertEquals(TikTokApiErrorCategory.ChallengeRequired, TikTokApiException.fromStatusCode(403).category)
        assertEquals(TikTokApiErrorCategory.RateLimited, TikTokApiException.fromStatusCode(429).category)
    }

    @Test
    fun messageDoesNotContainSensitiveValues() {
        val e = TikTokApiException(TikTokApiErrorCategory.EndpointDisabled, endpointName = "removeRepost")
        assertFalse(e.message.orEmpty().contains("cookie", ignoreCase = true))
    }
}
