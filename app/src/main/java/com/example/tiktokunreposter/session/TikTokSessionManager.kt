package com.example.tiktokunreposter.session

import android.content.Context
import android.webkit.CookieManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.example.tiktokunreposter.tiktok.CookieHeaderBuilder

class TikTokSessionManager(context: Context) {
    private val appContext = context.applicationContext
    private val cookieHeaderBuilder = CookieHeaderBuilder()

    private val masterKey = MasterKey.Builder(appContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        appContext,
        "tiktok_session_encrypted",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun hasSession(): Boolean = !getCookieHeader().isNullOrBlank()

    /**
     * Raw Cookie header for TikTok only. Never print this, never show in UI,
     * never send to non-TikTok domains.
     */
    fun getCookieHeader(): String? = prefs.getString(KEY_COOKIE_HEADER, null)

    fun getSecUid(): String? = prefs.getString(KEY_SEC_UID, null)

    fun getUserAgent(): String? = prefs.getString(KEY_USER_AGENT, null)

    fun saveSession(cookieHeader: String, secUid: String?, userAgent: String? = null) {
        val sanitized = cookieHeaderBuilder.sanitize(cookieHeader)
            ?: throw IllegalArgumentException("Cookie header cannot be blank")
        prefs.edit()
            .putString(KEY_COOKIE_HEADER, sanitized)
            .putString(KEY_SEC_UID, secUid?.takeIf { it.isNotBlank() && it != "null" })
            .putString(KEY_USER_AGENT, userAgent?.takeIf { it.isNotBlank() })
            .putLong(KEY_SAVED_AT, System.currentTimeMillis())
            .apply()
    }

    fun updateSecUid(secUid: String?) {
        prefs.edit()
            .putString(KEY_SEC_UID, secUid?.takeIf { it.isNotBlank() && it != "null" })
            .apply()
    }

    fun clearSession() {
        prefs.edit().clear().apply()
        val cm = CookieManager.getInstance()
        cm.removeAllCookies(null)
        cm.flush()
    }

    fun getSavedAt(): Long = prefs.getLong(KEY_SAVED_AT, 0L)

    /** Never include actual cookie/token values in logs, crash messages, or UI. */
    fun redactForUi(): String {
        val saved = getSavedAt()
        val hasSecUid = !getSecUid().isNullOrBlank()
        return if (saved == 0L) {
            "No local session"
        } else {
            "Local encrypted session saved • secUid=${if (hasSecUid) "yes" else "not yet"}"
        }
    }

    companion object {
        private const val KEY_COOKIE_HEADER = "cookie_header"
        private const val KEY_SEC_UID = "sec_uid"
        private const val KEY_USER_AGENT = "user_agent"
        private const val KEY_SAVED_AT = "saved_at"
    }
}
