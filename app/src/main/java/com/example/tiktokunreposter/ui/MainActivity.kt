package com.example.tiktokunreposter.ui

import android.Manifest
import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tiktokunreposter.config.AppModeStore
import com.example.tiktokunreposter.config.ClientMode
import com.example.tiktokunreposter.data.RepostQueueRepository
import com.example.tiktokunreposter.report.SafeReportManager
import com.example.tiktokunreposter.service.RepostRemoveForegroundService
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.TikTokEndpoints

class MainActivity : AppCompatActivity() {
    private lateinit var sessionManager: TikTokSessionManager
    private lateinit var queue: RepostQueueRepository
    private lateinit var reports: SafeReportManager
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var log: TextView
    private val logLines = ArrayDeque<String>()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> appendLog("Notification permission: ${if (granted) "granted" else "denied"}") }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RepostRemoveForegroundService.ACTION_PROGRESS) return
            val mode = intent.getStringExtra(RepostRemoveForegroundService.EXTRA_MODE).orEmpty()
            val state = intent.getStringExtra(RepostRemoveForegroundService.EXTRA_STATE).orEmpty()
            val text = intent.getStringExtra(RepostRemoveForegroundService.EXTRA_TEXT).orEmpty()
            val removed = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_REMOVED, 0)
            val failed = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_FAILED, 0)
            val skipped = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_SKIPPED, 0)
            val remaining = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_REMAINING, 0)
            val total = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_TOTAL, 0)
            progress.text = "mode=$mode • state=$state • removed=$removed • failed=$failed • skipped=$skipped • remaining=$remaining • total=$total"
            appendLog("$mode/$state — $text")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = TikTokSessionManager(this)
        queue = RepostQueueRepository(this)
        reports = SafeReportManager(this)
        buildUi()
        refreshStatus()
        requestNotificationPermissionIfNeeded()
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            receiver,
            IntentFilter(RepostRemoveForegroundService.ACTION_PROGRESS),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        refreshStatus()
    }

    override fun onStop() {
        super.onStop()
        runCatching { unregisterReceiver(receiver) }
    }

    private fun buildUi() {
        status = TextView(this).apply { textSize = 16f }
        progress = TextView(this).apply { textSize = 15f }
        log = TextView(this).apply { textSize = 13f }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        root.addView(status)
        root.addView(progress)
        button("Login TikTok via WebView") {
            startActivity(Intent(this, LoginWebViewActivity::class.java))
        }
        button("Start Mock Test") { startMode(ClientMode.MOCK) }
        button("Start Real Safe Check") { startMode(ClientMode.REAL_SAFE) }
        button("Start Real Dry Run") { startDryRun() }
        button("Start Experimental Remove") { startExperimentalWithWarning() }
        button("Pause") { serviceAction(RepostRemoveForegroundService.ACTION_PAUSE) }
        button("Resume") { serviceAction(RepostRemoveForegroundService.ACTION_RESUME) }
        button("Stop Immediately") { serviceAction(RepostRemoveForegroundService.ACTION_STOP) }
        button("API Diagnostics") { startActivity(Intent(this, ApiDiagnosticsActivity::class.java)) }
        button("Session Diagnostics") { startActivity(Intent(this, SessionDiagnosticsActivity::class.java)) }
        button("Background Readiness") { startActivity(Intent(this, BackgroundReadinessActivity::class.java)) }
        button("Clear Session") {
            confirm("Clear session?", "Ini menghapus encrypted session lokal dan WebView cookies milik app ini. Cookie/token tidak akan ditampilkan.") {
                sessionManager.clearSession(clearAppWebViewCookies = true)
                queue.clear()
                appendLog("Session + local queue cleared")
                refreshStatus()
            }
        }
        button("Export Safe Report") {
            appendLog("Safe report path: ${reports.reportPathForUi()}")
            appendLog(reports.lastReportText().take(1200))
        }
        button("Clear Reports") {
            reports.clearReports()
            appendLog("Reports cleared")
        }

        val hint = TextView(this).apply {
            text = "Unofficial endpoints enabled: ${TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS}. Default tetap false. Real mass remove belum diklaim working."
            textSize = 12f
        }
        root.addView(hint)
        root.addView(log, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun refreshStatus() {
        val mode = AppModeStore.getMode(this)
        val session = sessionManager.redactForUi()
        status.text = "Mode: ${mode.name}\nSession: $session"
        val stats = queue.stats()
        progress.text = "removed=${stats.success} • failed=${stats.failed} • skipped=${stats.skipped} • remaining=${stats.remaining} • total=${stats.total}"
    }

    private fun startMode(mode: ClientMode) {
        requestNotificationPermissionIfNeeded()
        if (mode.isReal() && !sessionManager.hasSession()) {
            appendLog("Login dulu via WebView untuk mode real.")
            return
        }
        AppModeStore.setMode(this, mode)
        serviceAction(RepostRemoveForegroundService.ACTION_START, mode)
        appendLog("Foreground Service started in ${mode.name}. Boleh minimize app dan buka WhatsApp/game.")
        refreshStatus()
    }

    private fun startDryRun() {
        if (!TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS) {
            appendLog("REAL_DRY_RUN butuh ENABLE_UNOFFICIAL_WEB_ENDPOINTS=true, tapi tetap tidak akan remove apa pun.")
            return
        }
        startMode(ClientMode.REAL_DRY_RUN)
    }

    private fun startExperimentalWithWarning() {
        if (!TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS) {
            appendLog("Experimental remove disabled. Ubah ENABLE_UNOFFICIAL_WEB_ENDPOINTS=true lalu rebuild kalau paham risiko.")
            return
        }
        confirm(
            title = "Experimental unofficial endpoint",
            message = "Mode ini memakai endpoint TikTok Web tidak resmi dari sumber publik. Bisa gagal, berubah, memicu rate-limit, atau meminta login ulang/challenge. App tidak akan bypass proteksi keamanan. Lanjutkan hanya untuk akun sendiri.",
            positive = "I understand"
        ) { startMode(ClientMode.REAL_UNOFFICIAL_EXPERIMENTAL) }
    }

    private fun serviceAction(action: String, mode: ClientMode? = null) {
        val intent = Intent(this, RepostRemoveForegroundService::class.java).setAction(action)
        mode?.let { intent.putExtra(RepostRemoveForegroundService.EXTRA_MODE, it.name) }
        ContextCompat.startForegroundService(this, intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun appendLog(msg: String) {
        logLines.addFirst("• ${msg.take(1400)}")
        while (logLines.size > 20) logLines.removeLast()
        log.text = logLines.joinToString("\n")
    }

    private fun confirm(title: String, message: String, positive: String = "OK", onOk: () -> Unit) {
        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setNegativeButton("Cancel", null)
            .setPositiveButton(positive) { _, _ -> onOk() }
            .show()
    }
}
