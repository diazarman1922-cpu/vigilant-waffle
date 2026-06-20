package com.example.tiktokunreposter.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.example.tiktokunreposter.config.AppModeStore
import com.example.tiktokunreposter.config.ClientMode
import com.example.tiktokunreposter.data.QueueStatus
import com.example.tiktokunreposter.data.RepostQueueRepository
import com.example.tiktokunreposter.report.SafeReportManager
import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory
import com.example.tiktokunreposter.tiktok.TikTokApiException
import com.example.tiktokunreposter.tiktok.TikTokClient
import com.example.tiktokunreposter.tiktok.TikTokClientFactory
import com.example.tiktokunreposter.util.SafetyController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RepostRemoveForegroundService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    @Volatile private var paused = false
    @Volatile private var stopRequested = false

    private lateinit var notificationHelper: NotificationHelper
    private lateinit var queue: RepostQueueRepository
    private lateinit var reports: SafeReportManager
    private lateinit var client: TikTokClient
    private var mode: ClientMode = ClientMode.MOCK
    private var startedAt: Long = 0L
    private var lastErrorCategory: TikTokApiErrorCategory? = null

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationHelper.ensureChannel()
        queue = RepostQueueRepository(this)
        reports = SafeReportManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startWork(resolveMode(intent))
            ACTION_PAUSE -> {
                paused = true
                updateNotification("paused", "User paused", broadcast = true)
            }
            ACTION_RESUME -> {
                paused = false
                updateNotification("removing", "Resumed", broadcast = true)
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

    private fun resolveMode(intent: Intent?): ClientMode {
        val raw = intent?.getStringExtra(EXTRA_MODE)
        return runCatching { ClientMode.valueOf(raw ?: AppModeStore.getMode(this).name) }.getOrDefault(AppModeStore.getMode(this))
    }

    private fun startWork(requestedMode: ClientMode) {
        if (worker?.isActive == true) return
        mode = requestedMode
        AppModeStore.setMode(this, mode)
        client = TikTokClientFactory.create(this, mode)
        stopRequested = false
        paused = false
        lastErrorCategory = null
        startedAt = System.currentTimeMillis()
        val initial = notificationHelper.buildProgress(
            mode = mode.name,
            state = "idle",
            text = "Starting",
            removed = 0,
            failed = 0,
            remaining = queue.stats().remaining,
            paused = false
        )
        startForegroundCompat(initial)
        updateNotification("checking login", "Starting", broadcast = true)
        worker = scope.launch { runModeLoop() }
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

    private suspend fun runModeLoop() {
        val safety = SafetyController()
        try {
            updateNotification("checking login", "Checking local session/client", true)
            val login = client.checkLogin()
            if (!login.loggedIn) {
                val category = login.category ?: TikTokApiErrorCategory.NotLoggedIn
                throw TikTokApiException(category = category, statusCode = login.statusCode, endpointName = "checkLogin")
            }

            when (mode) {
                ClientMode.REAL_SAFE -> {
                    updateNotification("finished", "REAL_SAFE checked login only. No fetch/remove was attempted.", true)
                    writeReport("finished")
                    stopSelfSafely()
                    return
                }
                ClientMode.REAL_DRY_RUN -> {
                    runDryRunFetchOnly()
                    writeReport("finished")
                    stopSelfSafely()
                    return
                }
                ClientMode.MOCK,
                ClientMode.REAL_UNOFFICIAL_EXPERIMENTAL -> runFetchAndRemoveLoop(safety)
            }

            val reason = safety.stopReasonOrNull()
            if (reason != null) {
                updateNotification("stopped", reason, true)
                writeReport("stopped")
            } else {
                updateNotification("finished", "Finished", true)
                writeReport("finished")
            }
        } catch (e: TikTokApiException) {
            lastErrorCategory = e.category
            updateNotification("error", "Stopped safely: ${e.category}", true)
            writeReport("error")
        } catch (e: Exception) {
            lastErrorCategory = TikTokApiErrorCategory.Unknown
            updateNotification("error", "Stopped safely: Unknown", true)
            writeReport("error")
        } finally {
            stopSelfSafely()
        }
    }

    private suspend fun runDryRunFetchOnly() {
        updateNotification("fetching reposts", "REAL_DRY_RUN fetch only; no remove will run", true)
        val page = client.fetchRepostedVideos(cursor = null)
        queue.clear()
        queue.upsertPending(page.items.map { it.videoId })
        page.items.forEach { queue.mark(it.videoId, QueueStatus.SKIPPED, "dry-run-no-remove") }
        updateNotification("finished", "Dry run fetched ${page.items.size} item(s). Removed 0.", true)
    }

    private suspend fun runFetchAndRemoveLoop(safety: SafetyController) {
        if (queue.stats().remaining == 0) {
            updateNotification("fetching reposts", "Fetching repost list", true)
            val page = client.fetchRepostedVideos(cursor = null)
            queue.upsertPending(page.items.map { it.videoId })
            updateNotification("removing", "Queued ${page.items.size} item(s)", true)
        } else {
            updateNotification("removing", "Resuming saved queue", true)
        }

        while (scope.coroutineContext.isActive && !stopRequested && safety.canContinue()) {
            waitIfPaused()
            if (stopRequested) break
            val next = queue.nextPending() ?: break
            try {
                updateNotification("removing", "Removing one item", true)
                val result = client.removeRepost(next.videoId)
                if (result.success) {
                    queue.mark(next.videoId, QueueStatus.SUCCESS)
                    safety.onSuccess()
                } else {
                    val category = result.category ?: TikTokApiErrorCategory.Unknown
                    queue.mark(next.videoId, QueueStatus.FAILED, category.name)
                    safety.onFailure(category)
                }
                updateNotification("removing", "Progress updated", true)
                delay(safety.nextDelayMs())
            } catch (e: TikTokApiException) {
                lastErrorCategory = e.category
                queue.mark(next.videoId, QueueStatus.FAILED, e.category.name)
                safety.onFailure(e.category)
                updateNotification("error", "Safe stop/error: ${e.category}", true)
                if (safety.shouldStopFor(e.category)) break
                delay(safety.backoffMs(queue.stats().failed))
            }
        }
    }

    private suspend fun waitIfPaused() {
        while (paused && !stopRequested && scope.coroutineContext.isActive) {
            updateNotification("paused", "Paused", true)
            delay(700L)
        }
    }

    private fun stopImmediately(reason: String) {
        stopRequested = true
        paused = false
        updateNotification("stopped", reason, broadcast = true)
        writeReport("stopped")
        stopSelfSafely()
    }

    private fun stopSelfSafely() {
        runCatching { if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_DETACH) else @Suppress("DEPRECATION") stopForeground(false) }
        stopSelf()
    }

    private fun writeReport(state: String) {
        runCatching {
            reports.writeLastRunReport(
                mode = mode,
                startedAt = if (startedAt == 0L) System.currentTimeMillis() else startedAt,
                finishedAt = System.currentTimeMillis(),
                stats = queue.stats(),
                lastErrorCategory = lastErrorCategory,
                state = state
            )
        }
    }

    private fun updateNotification(state: String, text: String, broadcast: Boolean) {
        val stats = queue.stats()
        val notification = notificationHelper.buildProgress(
            mode = mode.name,
            state = state,
            text = text,
            removed = stats.success,
            failed = stats.failed,
            remaining = stats.remaining,
            paused = paused
        )
        notificationHelper.notify(notification)
        if (broadcast) {
            sendBroadcast(Intent(ACTION_PROGRESS).apply {
                setPackage(packageName)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_STATE, state)
                putExtra(EXTRA_TEXT, text)
                putExtra(EXTRA_REMOVED, stats.success)
                putExtra(EXTRA_FAILED, stats.failed)
                putExtra(EXTRA_SKIPPED, stats.skipped)
                putExtra(EXTRA_REMAINING, stats.remaining)
                putExtra(EXTRA_TOTAL, stats.total)
            })
        }
    }

    companion object {
        const val ACTION_START = "com.example.tiktokunreposter.action.START"
        const val ACTION_PAUSE = "com.example.tiktokunreposter.action.PAUSE"
        const val ACTION_RESUME = "com.example.tiktokunreposter.action.RESUME"
        const val ACTION_STOP = "com.example.tiktokunreposter.action.STOP"
        const val ACTION_PROGRESS = "com.example.tiktokunreposter.action.PROGRESS"

        const val EXTRA_MODE = "mode"
        const val EXTRA_STATE = "state"
        const val EXTRA_TEXT = "text"
        const val EXTRA_REMOVED = "removed"
        const val EXTRA_FAILED = "failed"
        const val EXTRA_SKIPPED = "skipped"
        const val EXTRA_REMAINING = "remaining"
        const val EXTRA_TOTAL = "total"
    }
}
