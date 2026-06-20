package com.example.tiktokunreposter.tiktok

import com.example.tiktokunreposter.session.TikTokSessionManager
import okhttp3.OkHttpClient

/** Backward-compatible name from the first skeleton. Prefer TikTokWebApiClient. */
@Deprecated("Use TikTokWebApiClient instead")
class TikTokClientOfficialOrWeb(
    sessionManager: TikTokSessionManager,
    http: OkHttpClient = OkHttpClient(),
    enableUnofficialWeb: Boolean = TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS
) : TikTokWebApiClient(
    sessionManager = sessionManager,
    http = http,
    unofficialEndpointsEnabled = enableUnofficialWeb
)
