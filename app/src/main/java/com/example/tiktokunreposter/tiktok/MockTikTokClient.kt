package com.example.tiktokunreposter.tiktok

import kotlinx.coroutines.delay
import kotlin.random.Random

class MockTikTokClient(
    private val simulateErrors: Boolean = true,
    private val random: Random = Random.Default
) : TikTokClient {
    private var fetchPage = 0
    private var removeCount = 0

    override suspend fun checkLogin(): LoginStatus {
        delay(180)
        return LoginStatus(loggedIn = true, statusCode = 200, message = "mock-login-ok")
    }

    override suspend fun fetchRepostedVideos(cursor: String?): RepostPage {
        delay(250)
        maybeThrow("mockFetch", allowLoginExpired = false)
        fetchPage++
        val base = (cursor?.toIntOrNull() ?: 0) * 30
        val items = (1..30).map { index ->
            RepostedVideo(videoId = "mock_video_${base + index}", source = "mock")
        }
        val hasMore = fetchPage < 1
        return RepostPage(
            items = items,
            cursor = if (hasMore) fetchPage.toString() else null,
            hasMore = hasMore,
            statusCode = 200,
            endpointName = TikTokEndpoints.ENDPOINT_FETCH_REPOSTS
        )
    }

    override suspend fun removeRepost(videoId: String): RemoveResult {
        delay(220)
        removeCount++
        maybeThrow("mockRemove", allowLoginExpired = true)
        val success = removeCount % 11 != 0
        return RemoveResult(
            videoId = videoId,
            success = success,
            statusCode = if (success) 200 else 500,
            category = if (success) null else TikTokApiErrorCategory.Unknown,
            endpointName = TikTokEndpoints.ENDPOINT_REMOVE_REPOST,
            elapsedMs = 220L
        )
    }

    override suspend fun refreshSessionIfNeeded(): LoginStatus = checkLogin()

    override fun detectChallengeOrLoginExpired(response: TikTokHttpResponse): TikTokApiErrorCategory? = when (response.statusCode) {
        401 -> TikTokApiErrorCategory.LoginExpired
        403 -> TikTokApiErrorCategory.ChallengeRequired
        429 -> TikTokApiErrorCategory.RateLimited
        else -> null
    }

    override fun buildSafeHeaders(cookie: String): SafeHeaders = SafeHeaders(
        userAgent = "mock-user-agent",
        referer = "https://www.tiktok.com/",
        origin = "https://www.tiktok.com",
        csrfTokenPresent = false,
        cookiePresent = cookie.isNotBlank()
    )

    private fun maybeThrow(endpointName: String, allowLoginExpired: Boolean) {
        if (!simulateErrors) return
        val roll = random.nextInt(100)
        val category = when {
            roll < 2 && allowLoginExpired -> TikTokApiErrorCategory.LoginExpired
            roll in 2..3 -> TikTokApiErrorCategory.ChallengeRequired
            roll in 4..6 -> TikTokApiErrorCategory.RateLimited
            roll in 7..10 -> TikTokApiErrorCategory.NetworkError
            else -> null
        } ?: return
        throw TikTokApiException(category = category, endpointName = endpointName)
    }
}
