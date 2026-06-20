package com.example.tiktokunreposter.tiktok

import android.util.Log
import com.example.tiktokunreposter.BuildConfig

object ApiDebugMode {
    @Volatile
    var enabled: Boolean = BuildConfig.DEBUG

    fun log(event: ApiDebugEvent) {
        if (!enabled) return
        Log.d(TAG, event.safeString())
    }

    fun logFailure(endpointName: String, error: TikTokApiException) {
        log(
            ApiDebugEvent(
                endpointName = endpointName,
                statusCode = error.statusCode,
                category = error.category,
                message = error.safeUserMessage()
            )
        )
    }

    private const val TAG = "TikTokApiSafeDebug"
}
