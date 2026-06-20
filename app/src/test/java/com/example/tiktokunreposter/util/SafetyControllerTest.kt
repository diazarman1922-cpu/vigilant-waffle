package com.example.tiktokunreposter.util

import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyControllerTest {
    @Test
    fun stopsForChallengeAndRateLimit() {
        val safety = SafetyController(maxRemovePerBatch = 10)
        assertTrue(safety.canContinue())
        safety.onFailure(TikTokApiErrorCategory.ChallengeRequired)
        assertFalse(safety.canContinue())
    }

    @Test
    fun backoffIsPositive() {
        val safety = SafetyController()
        assertTrue(safety.backoffMs(1) > 0)
    }
}
