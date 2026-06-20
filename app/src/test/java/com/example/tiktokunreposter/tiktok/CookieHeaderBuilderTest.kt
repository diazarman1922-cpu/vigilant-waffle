package com.example.tiktokunreposter.tiktok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CookieHeaderBuilderTest {
    @Test
    fun sanitizeDropsInvalidAndKeepsValidCookies() {
        val raw = " sessionid=abc ; badline ; msToken=xyz ; injected=bad\nvalue ; empty= "
        val sanitized = CookieHeaderBuilder.sanitizeCookieHeader(raw)
        assertTrue(sanitized.contains("sessionid=abc"))
        assertTrue(sanitized.contains("msToken=xyz"))
        assertFalse(sanitized.contains("badline"))
        assertFalse(sanitized.contains("injected"))
        assertEquals(2, CookieHeaderBuilder.countCookies(sanitized))
    }
}
