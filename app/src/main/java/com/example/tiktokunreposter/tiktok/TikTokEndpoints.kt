package com.example.tiktokunreposter.tiktok

import com.example.tiktokunreposter.BuildConfig

object TikTokEndpoints {
    const val HOST_WWW = "www.tiktok.com"
    const val HOST_ROOT = "tiktok.com"
    const val BASE_WEB = "https://www.tiktok.com"

    // Public/unofficial endpoints seen in open-source browser-extension code.
    // Not official TikTok Developer API. OFF by default via BuildConfig.
    const val REPOST_LIST_PATH = "/api/repost/item_list/"
    const val REMOVE_REPOST_PATH = "/tiktok/v1/upvote/delete"

    const val ENDPOINT_CHECK_LOGIN = "checkLogin"
    const val ENDPOINT_FETCH_REPOSTS = "fetchRepostedVideos"
    const val ENDPOINT_REMOVE_REPOST = "removeRepost"

    val ENABLE_UNOFFICIAL_WEB_ENDPOINTS: Boolean
        get() = BuildConfig.ENABLE_UNOFFICIAL_WEB_ENDPOINTS

    fun repostListUrl(cursor: String?, secUid: String?): String {
        val safeCursor = cursor?.takeIf { it.isNotBlank() } ?: "0"
        val safeSecUid = secUid?.takeIf { it.isNotBlank() }.orEmpty()
        return "$BASE_WEB$REPOST_LIST_PATH?aid=1988&count=30&cursor=$safeCursor&secUid=$safeSecUid"
    }

    fun removeRepostUrl(videoId: String): String =
        "$BASE_WEB$REMOVE_REPOST_PATH?aid=1988&item_id=${videoId.trim()}"
}
