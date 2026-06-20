package com.example.tiktokunreposter.tiktok

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TikTokEndpointValidatorTest {
    @Test
    fun allowsOnlyHttpsTikTokHosts() {
        assertTrue(TikTokEndpointValidator.validateUrl("https://www.tiktok.com/api/repost/item_list/".toHttpUrl()).allowed)
        assertTrue(TikTokEndpointValidator.validateUrl("https://m.tiktok.com/".toHttpUrl()).allowed)
        assertFalse(TikTokEndpointValidator.validateUrl("http://www.tiktok.com/".toHttpUrl()).allowed)
        assertFalse(TikTokEndpointValidator.validateUrl("https://evil.example/".toHttpUrl()).allowed)
    }
}
