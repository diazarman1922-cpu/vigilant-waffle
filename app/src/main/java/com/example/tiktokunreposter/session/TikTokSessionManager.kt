package com.example.tiktokunreposter.session

import android.content.Context
import android.os.Build
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.tiktokunreposter.tiktok.CookieHeaderBuilder
import com.example.tiktokunreposter.tiktok.SafeSessionInfo

class TikTokSessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        val key = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            PREFS,
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveSession(cookieHeader: String, secUid: String? = null) {
        val sanitized = CookieHeaderBuilder.sanitizeCookieHeader(cookieHeader)
        require(sanitized.isNotBlank()) { "Empty TikTok cookie header" }
        prefs.edit()
            .putString(KEY_COOKIE, sanitized)
            .putString(KEY_SEC_UID, secUid?.takeIf { it.isNotBlank() })
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun hasSession(): Boolean = !getCookieHeader().isNullOrBlank()

    fun getCookieHeader(): String? = prefs.getString(KEY_COOKIE, null)
        ?.let { CookieHeaderBuilder.sanitizeCookieHeader(it) }
        ?.takeIf { it.isNotBlank() }

    fun getSecUid(): String? = prefs.getString(KEY_SEC_UID, null)?.takeIf { it.isNotBlank() }

    fun getSavedAt(): Long? = prefs.getLong(KEY_SAVED_AT, 0L).takeIf { it > 0L }

    fun describeSessionSafely(): SafeSessionInfo = CookieHeaderBuilder.safeDescription(
        raw = getCookieHeader(),
        savedAt = getSavedAt(),
        secUid = getSecUid()
    )

    fun redactForUi(): String {
        val info = describeSessionSafely()
        return if (!info.hasCookies) {
            "No TikTok WebView session saved"
        } else {
            "TikTok cookies present • count=${info.cookieCount} • msToken=${present(info.hasMsToken)} • csrf=${present(info.hasCsrf)} • secUid=${present(info.hasSecUid)}"
        }
    }

    fun clearSession(clearAppWebViewCookies: Boolean = false) {
        prefs.edit().clear().apply()
        if (clearAppWebViewCookies) {
            val manager = CookieManager.getInstance()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                manager.removeAllCookies(null)
                manager.flush()
            } else {
                @Suppress("DEPRECATION")
                manager.removeAllCookie()
            }
        }
    }

    private fun present(value: Boolean): String = if (value) "present" else "not found"

    companion object {
        private const val PREFS = "encrypted_tiktok_session"
        private const val KEY_COOKIE = "cookie_header"
        private const val KEY_SEC_UID = "sec_uid"
        private const val KEY_SAVED_AT = "saved_at"
    }
}
