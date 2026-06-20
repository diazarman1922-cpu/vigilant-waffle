package com.example.tiktokunreposter.util

import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory
import kotlin.math.min
import kotlin.random.Random

class SafetyController(
    val maxRemovePerBatch: Int = 100,
    val minDelayBetweenRequestsMs: Long = 4_000L,
    private val jitterMs: Long = 2_500L,
    private val maxConsecutiveFailures: Int = 5,
    private val maxBackoffMs: Long = 60_000L
) {
    private var removedThisBatch = 0
    private var consecutiveFailures = 0
    private var hardStopReason: String? = null

    fun canContinue(): Boolean =
        hardStopReason == null &&
            removedThisBatch < maxRemovePerBatch &&
            consecutiveFailures < maxConsecutiveFailures

    fun onSuccess() {
        removedThisBatch++
        consecutiveFailures = 0
    }

    fun onFailure(category: TikTokApiErrorCategory = TikTokApiErrorCategory.Unknown) {
        consecutiveFailures++
        if (shouldStopFor(category)) {
            hardStopReason = when (category) {
                TikTokApiErrorCategory.LoginExpired,
                TikTokApiErrorCategory.NotLoggedIn -> "Login expired or not logged in"
                TikTokApiErrorCategory.ChallengeRequired -> "TikTok challenge/captcha/verification required"
                TikTokApiErrorCategory.RateLimited -> "Rate limited / too many requests"
                TikTokApiErrorCategory.EndpointDisabled -> "Experimental endpoint disabled"
                else -> hardStopReason
            }
        }
    }

    fun stopNow(reason: String) {
        hardStopReason = reason
    }

    fun shouldStopFor(category: TikTokApiErrorCategory): Boolean = when (category) {
        TikTokApiErrorCategory.NotLoggedIn,
        TikTokApiErrorCategory.LoginExpired,
        TikTokApiErrorCategory.ChallengeRequired,
        TikTokApiErrorCategory.RateLimited,
        TikTokApiErrorCategory.EndpointDisabled -> true
        TikTokApiErrorCategory.NetworkError,
        TikTokApiErrorCategory.ParseError,
        TikTokApiErrorCategory.Unknown -> false
    }

    fun stopReasonOrNull(): String? = when {
        hardStopReason != null -> hardStopReason
        removedThisBatch >= maxRemovePerBatch -> "Reached local maxRemovePerBatch=$maxRemovePerBatch"
        consecutiveFailures >= maxConsecutiveFailures -> "Too many consecutive failures"
        else -> null
    }

    fun nextDelayMs(): Long = minDelayBetweenRequestsMs + Random.nextLong(0, jitterMs + 1)

    fun backoffMs(attempt: Int): Long {
        val capped = min(attempt, 6)
        val raw = (1_000L shl capped) + Random.nextLong(0, 1_000L)
        return raw.coerceAtMost(maxBackoffMs)
    }
}
