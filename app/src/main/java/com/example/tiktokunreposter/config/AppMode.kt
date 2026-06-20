package com.example.tiktokunreposter.config

import android.content.Context

enum class ClientMode {
    MOCK,
    REAL_SAFE,
    REAL_DRY_RUN,
    REAL_UNOFFICIAL_EXPERIMENTAL;

    fun isReal(): Boolean = this != MOCK
}

object AppModeStore {
    private const val PREFS = "app_mode_prefs"
    private const val KEY_MODE = "client_mode"

    val DEFAULT_MODE: ClientMode = ClientMode.MOCK

    fun getMode(context: Context): ClientMode {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_MODE, DEFAULT_MODE.name)
        return runCatching { ClientMode.valueOf(raw ?: DEFAULT_MODE.name) }.getOrDefault(DEFAULT_MODE)
    }

    fun setMode(context: Context, mode: ClientMode) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }
}
