package com.example.tiktokunreposter.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.tiktokunreposter.data.QueueStatus
import com.example.tiktokunreposter.data.RepostQueueRepository
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.RemoveResult
import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory
import com.example.tiktokunreposter.tiktok.TikTokApiException
import com.example.tiktokunreposter.tiktok.TikTokClient
import com.example.tiktokunreposter.tiktok.TikTokEndpoints
import com.example.tiktokunreposter.tiktok.TikTokWebApiClient
import com.example.tiktokunreposter.util.SafetyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RepostRemoveForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    @Volatile private var paused = false
    @Volatile private var stopRequested = false

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var queue: RepostQueueRepository
    private lateinit var client: TikTokClient

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.ensureChannel()
        queue = RepostQueueRepository(this)
        client = TikTokWebApiClient(TikTokSessionManager(this))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startWork()
            ACTION_PAUSE -> {
                paused = true
                updateNotification("Paused", "User paused", broadcast = true)
            }
            ACTION_RESUME -> {
                paused = false
                updateNotification("Removing reposts…", "Resumed", broadcast = true)
            }
            ACTION_STOP -> stopImmediately("Stopped by user")
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startWork() {
        if (worker?.isActive == true) return
        stopRequested = false
        paused = false
        val initial = notificationHelper.buildProgress(
            title = "Preparing…",
            text = "Starting",
            removed = 0,
            failed = 0,
            remaining = 0,
            paused = false
        )
        startForegroundCompat(initial)
        updateNotification("Preparing…", "Starting", broadcast = true)
        worker = scope.launch { runRemovalLoop() }
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NotificationHelper.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID, notification)
        }
    }

    private suspend fun runRemovalLoop() {
        val safety = SafetyController()
        try {
            val login = client.checkLogin()
            if (!login.loggedIn) {
                throw TikTokApiException(
                    category = login.category ?: TikTokApiErrorCategory.LoginExpired,
                    message = login.reason ?: "Login required"
                )
            }

            if (!TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS) {
                updateNotification(
                    "Endpoint disabled",
                    TikTokEndpoints.UNOFFICIAL_DISABLED_MESSAGE,
                    broadcast = true
                )
                safety.stopNow(TikTokEndpoints.UNOFFICIAL_DISABLED_MESSAGE)
                return
            }

            updateNotification("Listing reposts…", "Fetching queue", broadcast = true)
            fetchQueuePages(safety)

            while (!stopRequested && safety.canContinue()) {
                while (paused && !stopRequested) {
                    updateNotification("Paused", "Tap Resume to continue", broadcast = true)
                    delay(1_000)
                }
                val next = queue.nextPending() ?: break
                var attempt = 0
                var processed = false
                while (!processed && attempt < 3 && !stopRequested && safety.canContinue()) {
                    attempt++
                    try {
                        when (val result = client.removeRepost(next.videoId)) {
                            is RemoveResult.Success -> {
                                queue.mark(next.videoId, QueueStatus.SUCCESS)
                                safety.onSuccess()
                                processed = true
                            }
                            is RemoveResult.EndpointDisabled -> {
                                queue.mark(next.videoId, QueueStatus.SKIPPED, result.safeMessage)
                                safety.onFailure(TikTokApiErrorCategory.EndpointDisabled)
                                updateNotification("Endpoint disabled", result.safeMessage, broadcast = true)
                                stopRequested = true
                                processed = true
                            }
                            is RemoveResult.Failed -> {
                                queue.mark(next.videoId, QueueStatus.FAILED, result.safeMessage)
                                safety.onFailure(result.category)
                                updateNotification("Remove failed", result.safeMessage, broadcast = true)
                                if (safety.shouldStopFor(result.category)) stopRequested = true
                                processed = true
                            }
                        }
                    } catch (e: TikTokApiException) {
                        if (safety.shouldStopFor(e.category)) {
                            queue.mark(next.videoId, QueueStatus.FAILED, e.safeUserMessage())
                            safety.onFailure(e.category)
                            updateNotification(e.titleForUi(), e.safeUserMessage(), broadcast = true)
                            stopRequested = true
                            processed = true
                        } else if (attempt >= 3) {
                            queue.mark(next.videoId, QueueStatus.FAILED, e.safeUserMessage())
                            safety.onFailure(e.category)
                            processed = true
                        } else {
                            delay(safety.backoffMs(attempt))
                        }
                    } catch (e: Exception) {
                        if (attempt >= 3) {
                            queue.mark(next.videoId, QueueStatus.FAILED, e.safeMessage())
                            safety.onFailure(TikTokApiErrorCategory.Unknown)
                            processed = true
                        } else {
                            delay(safety.backoffMs(attempt))
                        }
                    }
                }
                updateNotification("Removing reposts…", "Working", broadcast = true)
                if (!stopRequested && safety.canContinue()) delay(safety.nextDelayMs())
            }
        } catch (e: TikTokApiException) {
            updateNotification(e.titleForUi(), e.safeUserMessage(), broadcast = true)
        } catch (e: Exception) {
            updateNotification("Stopped", e.safeMessage(), broadcast = true)
        } finally {
            val reason = safety.stopReasonOrNull()
            if (reason != null) updateNotification("Stopped safely", reason, broadcast = true)
            else if (!stopRequested) updateNotification("Finished", "Queue done", broadcast = true)
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf()
        }
    }

    private suspend fun fetchQueuePages(safety: SafetyController) {
        var cursor: String? = null
        var pages = 0
        do {
            val page = try {
                client.fetchRepostedVideos(cursor)
            } catch (e: TikTokApiException) {
                safety.onFailure(e.category)
                throw e
            }
            queue.upsertPending(page.videos.map { it.id })
            cursor = page.nextCursor
            pages++
            updateNotification(
                "Listing reposts…",
                "Fetched page $pages • found=${page.rawCount}",
                broadcast = true
            )
            delay(safety.nextDelayMs())
        } while (page.hasMore && !cursor.isNullOrBlank() && !stopRequested && pages < MAX_PAGES_PER_RUN)
    }

    private fun stopImmediately(reason: String) {
        stopRequested = true
        worker?.cancel()
        updateNotification("Stopped", reason, broadcast = true)
        stopForeground(STOP_FOREGROUND_DETACH)
        stopSelf()
    }

    private fun updateNotification(title: String, text: String, broadcast: Boolean) {
        val stats = queue.stats()
        notificationHelper.notify(notificationHelper.buildProgress(
            title = title,
            text = text,
            removed = stats.success,
            failed = stats.failed,
            remaining = stats.remaining,
            paused = paused
        ))
        if (broadcast) {
            sendBroadcast(Intent(ACTION_PROGRESS).setPackage(packageName).apply {
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_REMOVED, stats.success)
                putExtra(EXTRA_FAILED, stats.failed)
                putExtra(EXTRA_REMAINING, stats.remaining)
                putExtra(EXTRA_TOTAL, stats.total)
            })
        }
    }

    private fun Throwable.safeMessage(): String = when (this) {
        is TikTokApiException -> safeUserMessage()
        else -> message?.replace(Regex("[\\r\\n]"), " ")?.take(120) ?: "Unknown error"
    }

    private fun TikTokApiException.titleForUi(): String = when (category) {
        TikTokApiErrorCategory.NotLoggedIn -> "Not logged in"
        TikTokApiErrorCategory.LoginExpired -> "Login expired"
        TikTokApiErrorCategory.ChallengeRequired -> "Challenge required"
        TikTokApiErrorCategory.RateLimited -> "Rate limited"
        TikTokApiErrorCategory.NetworkError -> "Network error"
        TikTokApiErrorCategory.ParseError -> "Parse error"
        TikTokApiErrorCategory.EndpointDisabled -> "Endpoint disabled"
        TikTokApiErrorCategory.Unknown -> "Stopped"
    }

    companion object {
        const val ACTION_START = "com.example.tiktokunreposter.action.START"
        const val ACTION_PAUSE = "com.example.tiktokunreposter.action.PAUSE"
        const val ACTION_RESUME = "com.example.tiktokunreposter.action.RESUME"
        const val ACTION_STOP = "com.example.tiktokunreposter.action.STOP"
        const val ACTION_PROGRESS = "com.example.tiktokunreposter.action.PROGRESS"

        const val EXTRA_TITLE = "title"
        const val EXTRA_TEXT = "text"
        const val EXTRA_REMOVED = "removed"
        const val EXTRA_FAILED = "failed"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_TOTAL = "total"

        private const val MAX_PAGES_PER_RUN = 100
    }
}
