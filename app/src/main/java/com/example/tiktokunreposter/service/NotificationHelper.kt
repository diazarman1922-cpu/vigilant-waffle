package com.example.tiktokunreposter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "TikTok unreposter progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Local foreground-service progress. No sensitive session data is shown."
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }
    }

    fun buildProgress(
        mode: String,
        state: String,
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
        val isTerminal = state.equals("finished", true) || state.equals("stopped", true) || state.equals("error", true)

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle("$mode • $state")
            .setContentText("$text • removed=$removed failed=$failed remaining=$remaining")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$text\nremoved=$removed • failed=$failed • remaining=$remaining"))
            .setOngoing(!isTerminal)
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
