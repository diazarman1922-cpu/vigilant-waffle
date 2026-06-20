package com.example.tiktokunreposter.tiktok

/** Login state derived from local WebView session, not from stored password. */
data class LoginStatus(
    val loggedIn: Boolean,
    val reason: String? = null,
    val category: TikTokApiErrorCategory? = null
)

data class RepostVideo(
    val id: String,
    val authorName: String? = null,
    val description: String? = null,
    val url: String? = null
)

data class RepostPage(
    val videos: List<RepostVideo>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val endpointName: String,
    val rawCount: Int = videos.size
)

sealed class RemoveResult {
    data class Success(
        val videoId: String,
        val endpointName: String,
        val statusCode: Int
    ) : RemoveResult()

    data class Failed(
        val videoId: String,
        val safeMessage: String,
        val category: TikTokApiErrorCategory = TikTokApiErrorCategory.Unknown,
        val statusCode: Int? = null,
        val endpointName: String? = null
    ) : RemoveResult()

    data class EndpointDisabled(
        val videoId: String,
        val safeMessage: String = TikTokEndpoints.UNOFFICIAL_DISABLED_MESSAGE
    ) : RemoveResult()
}

data class TikTokHttpResponse(
    val endpointName: String,
    val statusCode: Int,
    val contentType: String?,
    val bodyPreview: String
)

data class SafeHeaders(
    val endpointName: String,
    val values: Map<String, String>
) {
    fun redactedForDebug(): Map<String, String> = values.mapValues { (key, value) ->
        when (key.lowercase()) {
            "cookie", "x-tt-token", "x-csrftoken", "csrf-token", "authorization" -> "<redacted>"
            else -> value.take(160)
        }
    }
}

data class TikTokWebTokens(
    val csrfToken: String? = null,
    val msToken: String? = null,
    val verifyFp: String? = null,
    val webId: String? = null,
    val secUid: String? = null,
    val signatureSupported: Boolean = false,
    val notes: List<String> = emptyList()
) {
    val hasAnyUsableToken: Boolean
        get() = !csrfToken.isNullOrBlank() || !msToken.isNullOrBlank() || !verifyFp.isNullOrBlank() || !webId.isNullOrBlank()
}

sealed class TokenExtractionResult {
    data class Supported(val tokens: TikTokWebTokens) : TokenExtractionResult()
    data class Unsupported(val reason: String, val tokens: TikTokWebTokens = TikTokWebTokens()) : TokenExtractionResult()
}

data class ApiDebugEvent(
    val endpointName: String,
    val statusCode: Int? = null,
    val category: TikTokApiErrorCategory? = null,
    val message: String? = null
) {
    fun safeString(): String = buildString {
        append("endpoint=").append(endpointName)
        statusCode?.let { append(" status=").append(it) }
        category?.let { append(" category=").append(it.name) }
        message?.let { append(" message=").append(it.take(180)) }
    }
}
