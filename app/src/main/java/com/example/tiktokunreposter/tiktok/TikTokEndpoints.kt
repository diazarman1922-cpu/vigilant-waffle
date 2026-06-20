package com.example.tiktokunreposter.tiktok

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Endpoint config lives in one file so experimental unofficial endpoints are easy
 * to audit, disable, or replace. Default is intentionally false.
 */
object TikTokEndpoints {
    const val ENABLE_UNOFFICIAL_WEB_ENDPOINTS: Boolean = false

    const val OFFICIAL_LOGIN_KIT_DOC = "https://developers.tiktok.com/doc/login-kit-web/"
    const val OFFICIAL_RESEARCH_REPOSTED_VIDEOS = "https://open.tiktokapis.com/v2/research/user/reposted_videos/"
    const val OFFICIAL_CONTENT_POSTING = "https://open.tiktokapis.com/v2/post/"

    const val TIKTOK_WEB_BASE = "https://www.tiktok.com"
    const val TIKTOK_HOME = "$TIKTOK_WEB_BASE/"

    /** Publicly observed in open-source browser extensions; not a TikTok Developer API. */
    const val WEB_REPOST_LIST_NAME = "unofficial.web.repost.item_list"
    const val WEB_REPOST_LIST_PATH = "/api/repost/item_list/"

    /** Publicly observed in open-source browser extensions; not a TikTok Developer API. */
    const val WEB_REMOVE_REPOST_NAME = "unofficial.web.upvote.delete"
    const val WEB_REMOVE_REPOST_PATH = "/tiktok/v1/upvote/delete"

    const val CHECK_LOGIN_NAME = "web.home.check"

    const val UNOFFICIAL_DISABLED_MESSAGE =
        "Remove repost endpoint belum diaktifkan. Aktifkan experimental unofficial endpoint hanya kalau kamu paham risikonya."

    fun repostListUrl(secUid: String, cursor: String?, count: Int = 30): HttpUrl {
        return TIKTOK_WEB_BASE.toHttpUrl().newBuilder()
            .addPathSegments(WEB_REPOST_LIST_PATH.trim('/'))
            .addQueryParameter("aid", "1988")
            .addQueryParameter("count", count.coerceIn(1, 30).toString())
            .addQueryParameter("coverFormat", "2")
            .addQueryParameter("cursor", cursor?.takeIf { it.isNotBlank() } ?: "0")
            .addQueryParameter("needPinnedItemIds", "true")
            .addQueryParameter("post_item_list_request_type", "0")
            .addQueryParameter("secUid", secUid)
            .build()
    }

    fun removeRepostUrl(videoId: String): HttpUrl {
        return TIKTOK_WEB_BASE.toHttpUrl().newBuilder()
            .addPathSegments(WEB_REMOVE_REPOST_PATH.trim('/'))
            .addQueryParameter("aid", "1988")
            .addQueryParameter("item_id", videoId)
            .build()
    }
}
