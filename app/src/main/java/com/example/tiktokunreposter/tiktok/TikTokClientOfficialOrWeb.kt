package com.example.tiktokunreposter.tiktok

import android.content.Context
import com.example.tiktokunreposter.config.ClientMode

/**
 * Compatibility wrapper for older project versions.
 * New code should use TikTokClientFactory directly.
 */
object TikTokClientOfficialOrWeb {
    fun create(context: Context, mode: ClientMode = ClientMode.REAL_SAFE): TikTokClient =
        TikTokClientFactory.create(context, mode)
}
