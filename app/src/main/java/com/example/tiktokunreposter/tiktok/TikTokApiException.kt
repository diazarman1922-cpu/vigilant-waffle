package com.example.tiktokunreposter.tiktok

enum class TikTokApiErrorCategory {
    NotLoggedIn,
    LoginExpired,
    ChallengeRequired,
    RateLimited,
    NetworkError,
    ParseError,
    EndpointDisabled,
    Unknown
}

class TikTokApiException(
    val category: TikTokApiErrorCategory,
    val statusCode: Int? = null,
    endpointName: String? = null,
    cause: Throwable? = null
) : Exception(buildSafeMessage(category, statusCode, endpointName), cause) {
    companion object {
        fun buildSafeMessage(
            category: TikTokApiErrorCategory,
            statusCode: Int? = null,
            endpointName: String? = null
        ): String {
            val status = statusCode?.let { " status=$it" }.orEmpty()
            val endpoint = endpointName?.let { " endpoint=$it" }.orEmpty()
            return "TikTok API error category=$category$status$endpoint"
        }

        fun fromStatusCode(statusCode: Int, endpointName: String? = null): TikTokApiException {
            val category = when (statusCode) {
                401 -> TikTokApiErrorCategory.LoginExpired
                403 -> TikTokApiErrorCategory.ChallengeRequired
                429 -> TikTokApiErrorCategory.RateLimited
                else -> TikTokApiErrorCategory.Unknown
            }
            return TikTokApiException(category, statusCode, endpointName)
        }
    }
}
