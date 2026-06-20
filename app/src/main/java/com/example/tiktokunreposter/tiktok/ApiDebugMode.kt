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

data class ApiDebugEvent(
    val endpointName: String,
    val statusCode: Int? = null,
    val category: TikTokApiErrorCategory? = null,
    val elapsedMs: Long? = null,
    val message: String? = null
) {
    fun safeString(): String = buildString {
        append("endpoint=").append(endpointName.take(80))
        append(" status=").append(statusCode ?: "n/a")
        append(" category=").append(category ?: "none")
        append(" elapsedMs=").append(elapsedMs ?: "n/a")
        message?.let { append(" message=").append(it.take(160)) }
    }
}

fun TikTokApiException.safeUserMessage(): String =
    "category=$category status=${statusCode ?: "n/a"}"
