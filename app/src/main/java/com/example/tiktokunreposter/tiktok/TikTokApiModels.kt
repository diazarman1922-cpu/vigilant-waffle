package com.example.tiktokunreposter.tiktok

data class LoginStatus(
    val loggedIn: Boolean,
    val category: TikTokApiErrorCategory? = null,
    val statusCode: Int? = null,
    val message: String? = null
)

data class RepostedVideo(
    val videoId: String,
    val createTime: Long? = null,
    val source: String = "unknown"
)

data class RepostPage(
    val items: List<RepostedVideo>,
    val cursor: String? = null,
    val hasMore: Boolean = false,
    val statusCode: Int? = null,
    val endpointName: String = "unknown"
)

data class RemoveResult(
    val videoId: String,
    val success: Boolean,
    val statusCode: Int? = null,
    val category: TikTokApiErrorCategory? = null,
    val endpointName: String = "unknown",
    val elapsedMs: Long = 0L
)

data class SafeHeaders(
    val userAgent: String,
    val referer: String,
    val origin: String,
    val csrfTokenPresent: Boolean,
    val cookiePresent: Boolean
)

data class TikTokHttpResponse(
    val statusCode: Int,
    val endpointName: String,
    val bodyPreview: String? = null
)

data class TikTokWebTokens(
    val msTokenPresent: Boolean = false,
    val csrfTokenPresent: Boolean = false,
    val secUid: String? = null
)

sealed class TokenExtractionResult {
    data class Supported(val tokens: TikTokWebTokens) : TokenExtractionResult()
    data class Unsupported(
        val reason: String,
        val tokens: TikTokWebTokens = TikTokWebTokens()
    ) : TokenExtractionResult()
}

data class ApiDiagnosticResult(
    val endpointName: String,
    val statusCode: Int? = null,
    val category: TikTokApiErrorCategory? = null,
    val elapsedMs: Long = 0L,
    val itemCount: Int? = null,
    val hasCursor: Boolean? = null,
    val note: String? = null
) {
    fun toSafeText(): String = buildString {
        append("endpoint=").append(endpointName)
        append(" status=").append(statusCode ?: "n/a")
        append(" category=").append(category ?: "none")
        append(" elapsedMs=").append(elapsedMs)
        itemCount?.let { append(" items=").append(it) }
        hasCursor?.let { append(" cursor=").append(if (it) "present" else "none") }
        note?.let { append(" note=").append(it) }
    }
}
