package com.example.tiktokunreposter.tiktok

import android.content.Context
import com.example.tiktokunreposter.config.AppModeStore
import com.example.tiktokunreposter.config.ClientMode
import com.example.tiktokunreposter.session.TikTokSessionManager

object TikTokClientFactory {
    fun create(context: Context, mode: ClientMode = AppModeStore.getMode(context)): TikTokClient {
        val session = TikTokSessionManager(context.applicationContext)
        return when (mode) {
            ClientMode.MOCK -> MockTikTokClient()
            ClientMode.REAL_SAFE -> TikTokWebApiClient(
                sessionManager = session,
                allowUnofficialEndpoints = false,
                allowRemove = false
            )
            ClientMode.REAL_DRY_RUN -> TikTokWebApiClient(
                sessionManager = session,
                allowUnofficialEndpoints = true,
                allowRemove = false
            )
            ClientMode.REAL_UNOFFICIAL_EXPERIMENTAL -> TikTokWebApiClient(
                sessionManager = session,
                allowUnofficialEndpoints = true,
                allowRemove = true
            )
        }
    }
}
