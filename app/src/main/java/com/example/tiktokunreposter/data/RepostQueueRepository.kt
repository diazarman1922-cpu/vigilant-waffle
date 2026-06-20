package com.example.tiktokunreposter.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class RepostQueueRepository(context: Context) {
    private val file = File(context.applicationContext.filesDir, "repost_queue.json")

    @Synchronized
    fun clear() {
        if (file.exists()) file.delete()
    }

    @Synchronized
    fun upsertPending(videoIds: List<String>) {
        val existing = loadMutable().associateBy { it.videoId }.toMutableMap()
        for (id in videoIds.distinct().filter { it.isNotBlank() }) {
            existing.putIfAbsent(id, QueueItem(videoId = id, status = QueueStatus.PENDING))
        }
        save(existing.values.toList())
    }

    @Synchronized
    fun nextPending(): QueueItem? = loadMutable().firstOrNull { it.status == QueueStatus.PENDING }

    @Synchronized
    fun mark(videoId: String, status: QueueStatus, message: String? = null) {
        val items = loadMutable().map {
            if (it.videoId == videoId) it.copy(status = status, message = message, updatedAt = System.currentTimeMillis()) else it
        }
        save(items)
    }

    @Synchronized
    fun stats(): QueueStats {
        val items = loadMutable()
        return QueueStats(
            pending = items.count { it.status == QueueStatus.PENDING },
            success = items.count { it.status == QueueStatus.SUCCESS },
            failed = items.count { it.status == QueueStatus.FAILED },
            skipped = items.count { it.status == QueueStatus.SKIPPED },
            total = items.size
        )
    }

    @Synchronized
    fun exportJsonText(): String = if (file.exists()) file.readText() else "[]"

    @Synchronized
    private fun loadMutable(): MutableList<QueueItem> {
        if (!file.exists()) return mutableListOf()
        return try {
            val arr = JSONArray(file.readText())
            MutableList(arr.length()) { i ->
                val obj = arr.getJSONObject(i)
                QueueItem(
                    videoId = obj.getString("videoId"),
                    status = QueueStatus.valueOf(obj.optString("status", QueueStatus.PENDING.name)),
                    message = obj.optString("message").takeIf { it.isNotBlank() },
                    updatedAt = obj.optLong("updatedAt", System.currentTimeMillis())
                )
            }
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    private fun save(items: List<QueueItem>) {
        val arr = JSONArray()
        for (item in items) {
            arr.put(JSONObject().apply {
                put("videoId", item.videoId)
                put("status", item.status.name)
                put("message", item.message ?: "")
                put("updatedAt", item.updatedAt)
            })
        }
        file.writeText(arr.toString(2))
    }
}

data class QueueItem(
    val videoId: String,
    val status: QueueStatus,
    val message: String? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

enum class QueueStatus { PENDING, SUCCESS, FAILED, SKIPPED }

data class QueueStats(
    val pending: Int,
    val success: Int,
    val failed: Int,
    val skipped: Int,
    val total: Int
) {
    val remaining: Int get() = pending
}
