package com.example.tiktokunreposter.tiktok

interface TikTokClient {
    suspend fun checkLogin(): LoginStatus
    suspend fun fetchRepostedVideos(cursor: String? = null): RepostPage
    suspend fun removeRepost(videoId: String): RemoveResult
    suspend fun refreshSessionIfNeeded(): LoginStatus
    fun detectChallengeOrLoginExpired(response: TikTokHttpResponse): TikTokApiErrorCategory?
    fun buildSafeHeaders(cookie: String): SafeHeaders
}
