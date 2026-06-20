package com.example.tiktokunreposter.ui

import android.Manifest
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
import com.example.tiktokunreposter.data.RepostQueueRepository
import com.example.tiktokunreposter.service.RepostRemoveForegroundService
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.TikTokEndpoints

class MainActivity : AppCompatActivity() {
    private lateinit var sessionManager: TikTokSessionManager
    private lateinit var queue: RepostQueueRepository
    private lateinit var status: TextView
    private lateinit var progress: TextView
    private lateinit var log: TextView

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* user can deny; service still starts, but Android 13 notification visibility may be limited */ }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action != RepostRemoveForegroundService.ACTION_PROGRESS) return
            val title = intent.getStringExtra(RepostRemoveForegroundService.EXTRA_TITLE).orEmpty()
            val text = intent.getStringExtra(RepostRemoveForegroundService.EXTRA_TEXT).orEmpty()
            val removed = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_REMOVED, 0)
            val failed = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_FAILED, 0)
            val remaining = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_REMAINING, 0)
            val total = intent.getIntExtra(RepostRemoveForegroundService.EXTRA_TOTAL, 0)
            progress.text = "removed=$removed • failed=$failed • remaining=$remaining • total=$total"
            appendLog("$title — $text")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = TikTokSessionManager(this)
        queue = RepostQueueRepository(this)
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
        fun button(label: String, onClick: () -> Unit) = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }

        root.addView(status)
        root.addView(progress)
        button("Login TikTok via WebView") {
            startActivity(Intent(this, LoginWebViewActivity::class.java))
        }
        button("Start Remove Reposts") { startRemoval() }
        button("Pause") { serviceAction(RepostRemoveForegroundService.ACTION_PAUSE) }
        button("Resume") { serviceAction(RepostRemoveForegroundService.ACTION_RESUME) }
        button("Stop Immediately") { serviceAction(RepostRemoveForegroundService.ACTION_STOP) }
        button("Clear Session") {
            sessionManager.clearSession()
            queue.clear()
            appendLog("Session + local queue cleared")
            refreshStatus()
        }
        button("Export Report JSON to Log") {
            appendLog(queue.exportJsonText().take(1800))
        }

        val hint = TextView(this).apply {
            text = "Mode unofficial endpoints: ${TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS}. Default false karena API resmi remove/unrepost belum ada."
            textSize = 12f
        }
        root.addView(hint)
        root.addView(log, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)

        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun refreshStatus() {
        status.text = if (sessionManager.hasSession()) {
            "Login status: ${sessionManager.redactForUi()}"
        } else {
            "Login status: Not logged in"
        }
        val stats = queue.stats()
        progress.text = "removed=${stats.success} • failed=${stats.failed} • remaining=${stats.remaining} • total=${stats.total}"
    }

    private fun startRemoval() {
        requestNotificationPermissionIfNeeded()
        if (!sessionManager.hasSession()) {
            appendLog("Login dulu via WebView.")
            return
        }
        serviceAction(RepostRemoveForegroundService.ACTION_START)
        appendLog("Foreground Service started. Boleh minimize app dan buka WhatsApp/game.")
    }

    private fun serviceAction(action: String) {
        val intent = Intent(this, RepostRemoveForegroundService::class.java).setAction(action)
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
        // Do not append cookie/session/token values here.
        log.text = ("\n• $msg" + log.text).take(5000)
        refreshStatus()
    }
}
