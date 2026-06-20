package com.example.tiktokunreposter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.tiktokunreposter.R

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TikTok repost removal",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows local progress for repost removal"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildProgress(
        title: String,
        text: String,
        removed: Int,
        failed: Int,
        remaining: Int,
        paused: Boolean
    ): Notification {
        val total = removed + failed + remaining
        val done = removed + failed
        val pauseOrResume = if (paused) RepostRemoveForegroundService.ACTION_RESUME else RepostRemoveForegroundService.ACTION_PAUSE
        val pauseLabel = if (paused) "Resume" else "Pause"

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(title)
            .setContentText("$text • removed=$removed failed=$failed remaining=$remaining")
            .setOngoing(!title.contains("Finished", ignoreCase = true) && !title.contains("Stopped", ignoreCase = true))
            .setOnlyAlertOnce(true)
            .setProgress(total.coerceAtLeast(1), done.coerceAtLeast(0), total == 0)
            .addAction(0, pauseLabel, servicePendingIntent(pauseOrResume))
            .addAction(0, "Stop", servicePendingIntent(RepostRemoveForegroundService.ACTION_STOP))
            .build()
    }

    fun notify(notification: Notification) {
        manager.notify(NOTIFICATION_ID, notification)
    }

    private fun servicePendingIntent(action: String): PendingIntent {
        val intent = Intent(context, RepostRemoveForegroundService::class.java).setAction(action)
        return PendingIntent.getService(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        const val CHANNEL_ID = "repost_remove_progress"
        const val NOTIFICATION_ID = 4412
    }
}
