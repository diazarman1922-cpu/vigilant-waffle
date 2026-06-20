package com.example.tiktokunreposter.tiktok

/**
 * Safe error categories. These are intentionally high-level so logs/UI never need
 * raw cookies, tokens, full URLs with sensitive query values, or response bodies.
 */
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
    override val message: String,
    val endpointName: String? = null,
    val statusCode: Int? = null,
    cause: Throwable? = null
) : Exception(message, cause) {
    fun safeUserMessage(): String = when (category) {
        TikTokApiErrorCategory.NotLoggedIn -> "Login TikTok dulu lewat WebView app ini."
        TikTokApiErrorCategory.LoginExpired -> "Session TikTok expired. Clear session lalu login ulang."
        TikTokApiErrorCategory.ChallengeRequired -> "TikTok minta verifikasi/challenge. Buka WebView dan selesaikan manual."
        TikTokApiErrorCategory.RateLimited -> "TikTok membatasi request. Stop dulu dan coba lagi nanti."
        TikTokApiErrorCategory.NetworkError -> "Network error. Cek koneksi lalu coba lagi."
        TikTokApiErrorCategory.ParseError -> "Response TikTok tidak bisa dibaca. Endpoint mungkin berubah atau kena challenge."
        TikTokApiErrorCategory.EndpointDisabled -> message
        TikTokApiErrorCategory.Unknown -> message.take(160)
    }
}
