package com.example.tiktokunreposter.tiktok

import com.example.tiktokunreposter.session.TikTokSessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * TikTok Web client using only the local session created by LoginWebViewActivity.
 * Unofficial endpoints are disabled unless TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS is changed.
 */
open class TikTokWebApiClient(
    private val sessionManager: TikTokSessionManager,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build(),
    private val cookieHeaderBuilder: CookieHeaderBuilder = CookieHeaderBuilder(),
    private val unofficialEndpointsEnabled: Boolean = TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS
) : TikTokClient {

    override suspend fun checkLogin(): LoginStatus = withContext(Dispatchers.IO) {
        val cookie = sessionManager.getCookieHeader()
        if (cookie.isNullOrBlank()) {
            return@withContext LoginStatus(false, "No local WebView session", TikTokApiErrorCategory.NotLoggedIn)
        }
        if (!cookieHeaderBuilder.looksLikeTikTokLoginCookie(cookie)) {
            return@withContext LoginStatus(false, "TikTok login cookie not detected", TikTokApiErrorCategory.NotLoggedIn)
        }
        if (!unofficialEndpointsEnabled) {
            return@withContext LoginStatus(true, "Local encrypted WebView session exists; unofficial endpoints disabled")
        }
        try {
            val request = Request.Builder()
                .url(TikTokEndpoints.TIKTOK_HOME)
                .headers(buildSafeHeaders(cookie).toOkHttpHeaders())
                .get()
                .build()
            http.newCall(request).execute().use { response ->
                val preview = response.safeBodyPreview()
                val category = detectChallengeOrLoginExpired(response.toTikTokHttpResponse(TikTokEndpoints.CHECK_LOGIN_NAME, preview))
                when (category) {
                    null -> LoginStatus(response.isSuccessful, if (response.isSuccessful) "Web session accepted" else "HTTP ${response.code}")
                    TikTokApiErrorCategory.RateLimited -> LoginStatus(true, "Session exists but web check is rate-limited", category)
                    else -> LoginStatus(false, category.name, category)
                }
            }
        } catch (e: IOException) {
            LoginStatus(false, "Network error while checking login", TikTokApiErrorCategory.NetworkError)
        }
    }

    override suspend fun refreshSessionIfNeeded(): LoginStatus {
        // TikTok WebView cookies are already persisted by CookieManager. We do not refresh tokens
        // via hidden APIs or any server. User must re-open WebView if TikTok expires/challenges.
        return checkLogin()
    }

    override suspend fun fetchRepostedVideos(cursor: String?): RepostPage = withContext(Dispatchers.IO) {
        ensureUnofficialEnabled(TikTokEndpoints.WEB_REPOST_LIST_NAME)
        val cookie = requireCookie()
        val secUid = sessionManager.getSecUid()?.takeIf { it.isNotBlank() }
            ?: throw TikTokApiException(
                TikTokApiErrorCategory.NotLoggedIn,
                "Missing secUid. Open your own TikTok profile in Login WebView, then save session again.",
                endpointName = TikTokEndpoints.WEB_REPOST_LIST_NAME
            )

        val url = TikTokEndpoints.repostListUrl(secUid = secUid, cursor = cursor)
        val request = Request.Builder()
            .url(url)
            .headers(buildSafeHeaders(cookie).toOkHttpHeaders())
            .header("Accept", "application/json, text/plain, */*")
            .get()
            .build()

        val json = executeJson(request, TikTokEndpoints.WEB_REPOST_LIST_NAME)
        val status = json.optInt("status_code", -1)
        if (status != 0) throwFromTikTokStatus(status, json, TikTokEndpoints.WEB_REPOST_LIST_NAME)

        val arr = json.optJSONArray("itemList")
        val videos = buildList {
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
                    val authorUniqueId = item.optJSONObject("author")?.optString("uniqueId")?.takeIf { it.isNotBlank() }
                    add(
                        RepostVideo(
                            id = id,
                            authorName = authorUniqueId?.let { "@$it" },
                            description = item.optString("desc").takeIf { it.isNotBlank() },
                            url = authorUniqueId?.let { "https://www.tiktok.com/@$it/video/$id" }
                        )
                    )
                }
            }
        }
        RepostPage(
            videos = videos,
            nextCursor = json.optString("cursor").takeIf { it.isNotBlank() && it != cursor },
            hasMore = json.optBoolean("hasMore", false),
            endpointName = TikTokEndpoints.WEB_REPOST_LIST_NAME,
            rawCount = arr?.length() ?: 0
        )
    }

    override suspend fun removeRepost(videoId: String): RemoveResult = withContext(Dispatchers.IO) {
        if (!unofficialEndpointsEnabled) {
            return@withContext RemoveResult.EndpointDisabled(videoId)
        }
        if (videoId.isBlank()) {
            return@withContext RemoveResult.Failed(videoId, "Blank videoId", TikTokApiErrorCategory.Unknown)
        }
        val cookie = try {
            requireCookie()
        } catch (e: TikTokApiException) {
            return@withContext RemoveResult.Failed(videoId, e.safeUserMessage(), e.category, e.statusCode, e.endpointName)
        }
        val url = TikTokEndpoints.removeRepostUrl(videoId)
        val headers = buildSafeHeaders(cookie)
        val request = Request.Builder()
            .url(url)
            .headers(headers.toOkHttpHeaders())
            .header("Origin", TikTokEndpoints.TIKTOK_WEB_BASE)
            .header("Referer", TikTokEndpoints.TIKTOK_HOME)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(FormBody.Builder().build())
            .build()

        try {
            val json = executeJson(request, TikTokEndpoints.WEB_REMOVE_REPOST_NAME)
            val status = json.optInt("status_code", -1)
            if (status == 0) {
                RemoveResult.Success(videoId, TikTokEndpoints.WEB_REMOVE_REPOST_NAME, 200)
            } else {
                val category = categoryFromTikTokStatus(status, json.toString()) ?: TikTokApiErrorCategory.Unknown
                RemoveResult.Failed(
                    videoId = videoId,
                    safeMessage = "TikTok returned status_code=$status",
                    category = category,
                    endpointName = TikTokEndpoints.WEB_REMOVE_REPOST_NAME
                )
            }
        } catch (e: TikTokApiException) {
            ApiDebugMode.logFailure(TikTokEndpoints.WEB_REMOVE_REPOST_NAME, e)
            RemoveResult.Failed(videoId, e.safeUserMessage(), e.category, e.statusCode, e.endpointName)
        }
    }

    override fun buildSafeHeaders(cookie: String): SafeHeaders {
        val sanitizedCookie = cookieHeaderBuilder.buildForTikTokUrl(cookie, TikTokEndpoints.TIKTOK_HOME)
            ?: throw TikTokApiException(TikTokApiErrorCategory.NotLoggedIn, "No valid TikTok cookie", endpointName = "headers")
        val tokens = TikTokWebTokenExtractor.extractFromCookieHeader(sanitizedCookie)
        val ua = sessionManager.getUserAgent()?.takeIf { it.isNotBlank() } ?: DEFAULT_WEBVIEW_UA
        val headers = linkedMapOf(
            "Cookie" to sanitizedCookie,
            "User-Agent" to ua,
            "Accept-Language" to "en-US,en;q=0.9",
            "Accept" to "application/json, text/plain, */*",
            "Referer" to TikTokEndpoints.TIKTOK_HOME
        )
        val csrf = when (tokens) {
            is TokenExtractionResult.Supported -> tokens.tokens.csrfToken
            is TokenExtractionResult.Unsupported -> tokens.tokens.csrfToken
        }
        if (!csrf.isNullOrBlank()) headers["X-CSRFToken"] = csrf
        return SafeHeaders(endpointName = "headers", values = headers)
    }

    override fun detectChallengeOrLoginExpired(response: TikTokHttpResponse): TikTokApiErrorCategory? {
        if (response.statusCode == 401) return TikTokApiErrorCategory.LoginExpired
        if (response.statusCode == 403) return TikTokApiErrorCategory.ChallengeRequired
        if (response.statusCode == 429) return TikTokApiErrorCategory.RateLimited
        val body = response.bodyPreview.lowercase()
        val contentType = response.contentType.orEmpty().lowercase()
        return when {
            "captcha" in body || "verify" in body || "challenge" in body || "security check" in body -> TikTokApiErrorCategory.ChallengeRequired
            "login" in body && ("expired" in body || "sign in" in body || "signin" in body) -> TikTokApiErrorCategory.LoginExpired
            "too many" in body || "rate limit" in body -> TikTokApiErrorCategory.RateLimited
            response.statusCode in 200..299 && "text/html" in contentType && response.endpointName != TikTokEndpoints.CHECK_LOGIN_NAME -> TikTokApiErrorCategory.ChallengeRequired
            else -> null
        }
    }

    private fun ensureUnofficialEnabled(endpointName: String) {
        if (!unofficialEndpointsEnabled) {
            throw TikTokApiException(
                category = TikTokApiErrorCategory.EndpointDisabled,
                message = TikTokEndpoints.UNOFFICIAL_DISABLED_MESSAGE,
                endpointName = endpointName
            )
        }
    }

    private fun requireCookie(): String {
        val raw = sessionManager.getCookieHeader()
        val sanitized = cookieHeaderBuilder.buildForTikTokUrl(raw, TikTokEndpoints.TIKTOK_HOME)
        if (sanitized.isNullOrBlank()) {
            throw TikTokApiException(
                TikTokApiErrorCategory.NotLoggedIn,
                "No local WebView TikTok session",
                endpointName = "session"
            )
        }
        return sanitized
    }

    private fun executeJson(request: Request, endpointName: String): JSONObject {
        try {
            http.newCall(request).execute().use { response ->
                val raw = response.body.string()
                val preview = raw.take(600)
                val synthetic = response.toTikTokHttpResponse(endpointName, preview)
                detectChallengeOrLoginExpired(synthetic)?.let { category ->
                    throw TikTokApiException(
                        category = category,
                        message = category.name,
                        endpointName = endpointName,
                        statusCode = response.code
                    )
                }
                if (!response.isSuccessful) {
                    throw TikTokApiException(
                        category = TikTokApiErrorCategory.Unknown,
                        message = "HTTP ${response.code}",
                        endpointName = endpointName,
                        statusCode = response.code
                    )
                }
                return try {
                    JSONObject(raw)
                } catch (e: Exception) {
                    throw TikTokApiException(
                        category = TikTokApiErrorCategory.ParseError,
                        message = "Response is not JSON",
                        endpointName = endpointName,
                        statusCode = response.code,
                        cause = e
                    )
                }
            }
        } catch (e: TikTokApiException) {
            throw e
        } catch (e: IOException) {
            throw TikTokApiException(
                category = TikTokApiErrorCategory.NetworkError,
                message = "Network request failed",
                endpointName = endpointName,
                cause = e
            )
        }
    }

    private fun throwFromTikTokStatus(status: Int, json: JSONObject, endpointName: String): Nothing {
        val raw = json.toString()
        val category = categoryFromTikTokStatus(status, raw) ?: TikTokApiErrorCategory.Unknown
        throw TikTokApiException(category, "TikTok status_code=$status", endpointName = endpointName)
    }

    private fun categoryFromTikTokStatus(status: Int, raw: String): TikTokApiErrorCategory? {
        val lower = raw.lowercase()
        return when {
            status == 0 -> null
            status == 8 || "login" in lower || "auth" in lower -> TikTokApiErrorCategory.LoginExpired
            status == 10221 || "too many" in lower || "rate" in lower -> TikTokApiErrorCategory.RateLimited
            "captcha" in lower || "verify" in lower || "challenge" in lower -> TikTokApiErrorCategory.ChallengeRequired
            else -> TikTokApiErrorCategory.Unknown
        }
    }

    private fun Response.safeBodyPreview(): String = runCatching { peekBody(600).string() }.getOrDefault("")

    private fun Response.toTikTokHttpResponse(endpointName: String, preview: String): TikTokHttpResponse = TikTokHttpResponse(
        endpointName = endpointName,
        statusCode = code,
        contentType = header("Content-Type"),
        bodyPreview = preview
    )

    private fun SafeHeaders.toOkHttpHeaders(): okhttp3.Headers = okhttp3.Headers.Builder().apply {
        values.forEach { (key, value) -> add(key, value) }
    }.build()

    companion object {
        private const val DEFAULT_WEBVIEW_UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/120 Mobile Safari/537.36"
    }
}
