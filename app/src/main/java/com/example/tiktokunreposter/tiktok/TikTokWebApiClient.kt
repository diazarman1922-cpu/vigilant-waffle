package com.example.tiktokunreposter.tiktok

import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.util.SafeLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.system.measureTimeMillis

class TikTokWebApiClient(
    private val sessionManager: TikTokSessionManager,
    private val allowUnofficialEndpoints: Boolean = false,
    private val allowRemove: Boolean = false,
    private val httpClient: OkHttpClient = defaultHttpClient()
) : TikTokClient {

    override suspend fun checkLogin(): LoginStatus = withContext(Dispatchers.IO) {
        val cookie = sessionManager.getCookieHeader()
        if (cookie.isNullOrBlank()) {
            return@withContext LoginStatus(false, TikTokApiErrorCategory.NotLoggedIn, message = "No saved WebView session")
        }
        val url = TikTokEndpoints.BASE_WEB
        val request = safeRequestBuilder(url, TikTokEndpoints.ENDPOINT_CHECK_LOGIN, cookie)
            .get()
            .build()
        val started = System.currentTimeMillis()
        runCatching { httpClient.newCall(request).execute().use { response ->
            val elapsed = System.currentTimeMillis() - started
            SafeLogger.api(TikTokEndpoints.ENDPOINT_CHECK_LOGIN, response.code, elapsed)
            val category = detectChallengeOrLoginExpired(response.toSafeResponse(TikTokEndpoints.ENDPOINT_CHECK_LOGIN))
            LoginStatus(
                loggedIn = response.isSuccessful && category == null,
                category = category,
                statusCode = response.code,
                message = if (response.isSuccessful) "Session request accepted" else "Session check failed"
            )
        } }.getOrElse { error ->
            SafeLogger.error(TikTokEndpoints.ENDPOINT_CHECK_LOGIN, TikTokApiErrorCategory.NetworkError, throwable = error)
            LoginStatus(false, TikTokApiErrorCategory.NetworkError, message = "Network error")
        }
    }

    override suspend fun fetchRepostedVideos(cursor: String?): RepostPage = withContext(Dispatchers.IO) {
        ensureUnofficialEnabled(TikTokEndpoints.ENDPOINT_FETCH_REPOSTS)
        val cookie = requireCookie()
        val url = TikTokEndpoints.repostListUrl(cursor, sessionManager.getSecUid())
        val request = safeRequestBuilder(url, TikTokEndpoints.ENDPOINT_FETCH_REPOSTS, cookie)
            .get()
            .build()
        executeForPage(request, TikTokEndpoints.ENDPOINT_FETCH_REPOSTS)
    }

    override suspend fun removeRepost(videoId: String): RemoveResult = withContext(Dispatchers.IO) {
        ensureUnofficialEnabled(TikTokEndpoints.ENDPOINT_REMOVE_REPOST)
        if (!allowRemove) {
            throw TikTokApiException(TikTokApiErrorCategory.EndpointDisabled, endpointName = TikTokEndpoints.ENDPOINT_REMOVE_REPOST)
        }
        val safeVideoId = videoId.trim()
        if (safeVideoId.isBlank() || safeVideoId.length > 128) {
            throw TikTokApiException(TikTokApiErrorCategory.Unknown, endpointName = TikTokEndpoints.ENDPOINT_REMOVE_REPOST)
        }
        val cookie = requireCookie()
        val url = TikTokEndpoints.removeRepostUrl(safeVideoId)
        val csrf = CookieHeaderBuilder.csrfToken(cookie)
        val request = safeRequestBuilder(url, TikTokEndpoints.ENDPOINT_REMOVE_REPOST, cookie)
            .post(FormBody.Builder().build())
            .apply {
                if (!csrf.isNullOrBlank()) header("x-secsdk-csrf-token", csrf)
            }
            .build()
        executeForRemove(request, safeVideoId, TikTokEndpoints.ENDPOINT_REMOVE_REPOST)
    }

    override suspend fun refreshSessionIfNeeded(): LoginStatus = checkLogin()

    override fun detectChallengeOrLoginExpired(response: TikTokHttpResponse): TikTokApiErrorCategory? = when (response.statusCode) {
        401 -> TikTokApiErrorCategory.LoginExpired
        403 -> TikTokApiErrorCategory.ChallengeRequired
        429 -> TikTokApiErrorCategory.RateLimited
        in 300..399 -> TikTokApiErrorCategory.LoginExpired
        else -> {
            val preview = response.bodyPreview.orEmpty().lowercase()
            when {
                "captcha" in preview || "verify" in preview || "challenge" in preview -> TikTokApiErrorCategory.ChallengeRequired
                "login" in preview && "password" in preview -> TikTokApiErrorCategory.LoginExpired
                else -> null
            }
        }
    }

    override fun buildSafeHeaders(cookie: String): SafeHeaders = SafeHeaders(
        userAgent = DEFAULT_USER_AGENT,
        referer = TikTokEndpoints.BASE_WEB + "/",
        origin = TikTokEndpoints.BASE_WEB,
        csrfTokenPresent = CookieHeaderBuilder.csrfToken(cookie) != null,
        cookiePresent = CookieHeaderBuilder.sanitizeCookieHeader(cookie).isNotBlank()
    )

    private fun safeRequestBuilder(url: String, endpointName: String, cookie: String): Request.Builder {
        val parsed = url.toHttpUrlOrThrow(endpointName)
        val validation = TikTokEndpointValidator.validateUrl(parsed)
        if (!validation.allowed) {
            throw TikTokApiException(TikTokApiErrorCategory.EndpointDisabled, endpointName = endpointName)
        }
        val cookieForUrl = CookieHeaderBuilder.buildForUrl(cookie, url)
            ?: throw TikTokApiException(TikTokApiErrorCategory.NotLoggedIn, endpointName = endpointName)
        return Request.Builder()
            .url(parsed)
            .header("User-Agent", DEFAULT_USER_AGENT)
            .header("Accept", "application/json,text/plain,*/*")
            .header("Referer", TikTokEndpoints.BASE_WEB + "/")
            .header("Origin", TikTokEndpoints.BASE_WEB)
            .header("Cookie", cookieForUrl)
    }

    private fun String.toHttpUrlOrThrow(endpointName: String) =
        runCatching { this.toHttpUrl() }
            .getOrElse { throw TikTokApiException(TikTokApiErrorCategory.EndpointDisabled, endpointName = endpointName, cause = it) }

    private fun requireCookie(): String = sessionManager.getCookieHeader()
        ?: throw TikTokApiException(TikTokApiErrorCategory.NotLoggedIn, endpointName = "session")

    private fun ensureUnofficialEnabled(endpointName: String) {
        if (!allowUnofficialEndpoints || !TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS) {
            throw TikTokApiException(TikTokApiErrorCategory.EndpointDisabled, endpointName = endpointName)
        }
    }

    private fun executeForPage(request: Request, endpointName: String): RepostPage {
        var responseCode: Int? = null
        var elapsedMs = 0L
        try {
            var page: RepostPage? = null
            elapsedMs = measureTimeMillis {
                httpClient.newCall(request).execute().use { response ->
                    responseCode = response.code
                    val bodyText = response.body.string()
                    val category = detectChallengeOrLoginExpired(response.toSafeResponse(endpointName, bodyText.safePreview()))
                    if (category != null) throw TikTokApiException(category, response.code, endpointName)
                    if (!response.isSuccessful) throw TikTokApiException.fromStatusCode(response.code, endpointName)
                    page = parseRepostPage(bodyText, response.code, endpointName)
                }
            }
            SafeLogger.api(endpointName, responseCode, elapsedMs)
            return page ?: throw TikTokApiException(TikTokApiErrorCategory.ParseError, responseCode, endpointName)
        } catch (e: TikTokApiException) {
            SafeLogger.error(endpointName, e.category, responseCode, e)
            throw e
        } catch (e: IOException) {
            SafeLogger.error(endpointName, TikTokApiErrorCategory.NetworkError, responseCode, e)
            throw TikTokApiException(TikTokApiErrorCategory.NetworkError, responseCode, endpointName, e)
        } catch (e: Exception) {
            SafeLogger.error(endpointName, TikTokApiErrorCategory.ParseError, responseCode, e)
            throw TikTokApiException(TikTokApiErrorCategory.ParseError, responseCode, endpointName, e)
        }
    }

    private fun executeForRemove(request: Request, videoId: String, endpointName: String): RemoveResult {
        var responseCode: Int? = null
        var result: RemoveResult? = null
        var elapsedMs = 0L
        try {
            elapsedMs = measureTimeMillis {
                httpClient.newCall(request).execute().use { response ->
                    responseCode = response.code
                    val bodyText = response.body.string()
                    val category = detectChallengeOrLoginExpired(response.toSafeResponse(endpointName, bodyText.safePreview()))
                    if (category != null) throw TikTokApiException(category, response.code, endpointName)
                    if (!response.isSuccessful) throw TikTokApiException.fromStatusCode(response.code, endpointName)
                    val success = bodyLooksSuccessful(bodyText)
                    result = RemoveResult(
                        videoId = videoId,
                        success = success,
                        statusCode = response.code,
                        category = if (success) null else TikTokApiErrorCategory.Unknown,
                        endpointName = endpointName,
                        elapsedMs = elapsedMs
                    )
                }
            }
            SafeLogger.api(endpointName, responseCode, elapsedMs)
            return result ?: RemoveResult(videoId, false, responseCode, TikTokApiErrorCategory.Unknown, endpointName, elapsedMs)
        } catch (e: TikTokApiException) {
            SafeLogger.error(endpointName, e.category, responseCode, e)
            throw e
        } catch (e: IOException) {
            SafeLogger.error(endpointName, TikTokApiErrorCategory.NetworkError, responseCode, e)
            throw TikTokApiException(TikTokApiErrorCategory.NetworkError, responseCode, endpointName, e)
        }
    }

    private fun parseRepostPage(bodyText: String, statusCode: Int, endpointName: String): RepostPage {
        val root = JSONObject(bodyText)
        val arrays = listOf("itemList", "item_list", "items")
            .mapNotNull { root.optJSONArray(it) }
        val arr = arrays.firstOrNull() ?: root.optJSONObject("data")?.let { data ->
            data.optJSONArray("itemList") ?: data.optJSONArray("item_list") ?: data.optJSONArray("items")
        } ?: JSONArray()
        val items = mutableListOf<RepostedVideo>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            val id = obj.optString("id")
                .ifBlank { obj.optString("item_id") }
                .ifBlank { obj.optJSONObject("item")?.optString("id").orEmpty() }
                .ifBlank { obj.optJSONObject("video")?.optString("id").orEmpty() }
            if (id.isNotBlank()) {
                items.add(RepostedVideo(videoId = id, createTime = obj.optLong("createTime").takeIf { it > 0L }, source = "tiktok-web-unofficial"))
            }
        }
        val data = root.optJSONObject("data")
        val cursor = root.optString("cursor").ifBlank { root.optString("maxCursor") }.ifBlank { data?.optString("cursor").orEmpty() }.takeIf { it.isNotBlank() }
        val hasMore = root.optBoolean("hasMore", false) || root.optInt("hasMore", 0) == 1 || data?.optBoolean("hasMore", false) == true
        return RepostPage(items = items, cursor = cursor, hasMore = hasMore, statusCode = statusCode, endpointName = endpointName)
    }

    private fun bodyLooksSuccessful(bodyText: String): Boolean {
        if (bodyText.isBlank()) return true
        return runCatching {
            val obj = JSONObject(bodyText)
            val statusCode = obj.optInt("status_code", obj.optInt("statusCode", 0))
            val code = obj.optInt("code", 0)
            val message = obj.optString("status_msg", obj.optString("message", "")).lowercase()
            statusCode == 0 || code == 0 || "success" in message || "ok" == message
        }.getOrDefault(true)
    }

    private fun Response.toSafeResponse(endpointName: String, bodyPreview: String? = null) = TikTokHttpResponse(
        statusCode = code,
        endpointName = endpointName,
        bodyPreview = bodyPreview
    )

    private fun String.safePreview(): String = take(500)

    companion object {
        private const val DEFAULT_USER_AGENT = "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Mobile Safari/537.36"

        fun defaultHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
    }
}
