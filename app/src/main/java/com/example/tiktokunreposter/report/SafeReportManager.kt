package com.example.tiktokunreposter.report

import android.content.Context
import com.example.tiktokunreposter.config.ClientMode
import com.example.tiktokunreposter.data.QueueStats
import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class SafeReportManager(context: Context) {
    private val reportsDir = File(context.applicationContext.filesDir, "reports")
    private val lastReport = File(reportsDir, "last_run_report.json")

    fun writeLastRunReport(
        mode: ClientMode,
        startedAt: Long,
        finishedAt: Long,
        stats: QueueStats,
        lastErrorCategory: TikTokApiErrorCategory? = null,
        state: String = "finished"
    ): File {
        reportsDir.mkdirs()
        val json = JSONObject().apply {
            put("mode", mode.name)
            put("startedAt", iso(startedAt))
            put("finishedAt", iso(finishedAt))
            put("removed", stats.success)
            put("failed", stats.failed)
            put("skipped", stats.skipped)
            put("remaining", stats.remaining)
            put("lastErrorCategory", lastErrorCategory?.name ?: JSONObject.NULL)
            put("state", state)
            put("containsSensitiveData", false)
        }
        lastReport.writeText(json.toString(2))
        return lastReport
    }

    fun lastReportText(): String = if (lastReport.exists()) lastReport.readText() else "No report yet."

    fun clearReports() {
        if (reportsDir.exists()) reportsDir.deleteRecursively()
    }

    fun reportPathForUi(): String = lastReport.absolutePath

    private fun iso(ts: Long): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        formatter.timeZone = TimeZone.getTimeZone("UTC")
        return formatter.format(Date(ts))
    }
}
