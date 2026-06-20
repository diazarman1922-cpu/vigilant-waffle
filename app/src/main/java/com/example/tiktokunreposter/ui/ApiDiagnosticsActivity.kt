package com.example.tiktokunreposter.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.tiktokunreposter.config.AppModeStore
import com.example.tiktokunreposter.config.ClientMode
import com.example.tiktokunreposter.tiktok.RemoveResult
import com.example.tiktokunreposter.tiktok.TikTokApiErrorCategory
import com.example.tiktokunreposter.tiktok.TikTokApiException
import com.example.tiktokunreposter.tiktok.TikTokClientFactory
import com.example.tiktokunreposter.tiktok.TikTokEndpoints
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ApiDiagnosticsActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var output: TextView
    private lateinit var modeText: TextView
    private val lines = ArrayDeque<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshMode()
    }

    private fun buildUi() {
        output = TextView(this).apply { textSize = 13f }
        modeText = TextView(this).apply { textSize = 16f }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(modeText)
        button("Set Mode: MOCK") { setMode(ClientMode.MOCK) }
        button("Set Mode: REAL_SAFE") { setMode(ClientMode.REAL_SAFE) }
        button("Set Mode: REAL_DRY_RUN") { setMode(ClientMode.REAL_DRY_RUN) }
        button("Check Login") { checkLogin() }
        button("Test Fetch Reposts") { testFetch() }
        button("Test Remove One Mock Item") { testRemoveMock() }
        val removeOne = button("Remove 1 Repost Test") { confirmRemoveOneFirst() }
        removeOne.visibility = if (TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS) View.VISIBLE else View.GONE
        button("Show Last Safe Error") { append("Last safe error is shown in entries below; no cookie/token/session is logged.") }
        button("Clear Diagnostics") { lines.clear(); output.text = "" }
        root.addView(output, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun setMode(mode: ClientMode) {
        AppModeStore.setMode(this, mode)
        refreshMode()
        append("Mode changed to ${mode.name}")
    }

    private fun refreshMode() {
        modeText.text = "Client mode: ${AppModeStore.getMode(this).name}\nUnofficial enabled: ${TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS}"
    }

    private fun checkLogin() = runDiagnostic("checkLogin") {
        val client = TikTokClientFactory.create(this)
        val started = System.currentTimeMillis()
        val result = client.checkLogin()
        val elapsed = System.currentTimeMillis() - started
        "endpoint=checkLogin status=${result.statusCode ?: "n/a"} category=${result.category ?: "none"} loggedIn=${result.loggedIn} elapsedMs=$elapsed"
    }

    private fun testFetch() = runDiagnostic("fetchRepostedVideos") {
        val client = TikTokClientFactory.create(this)
        val started = System.currentTimeMillis()
        val page = client.fetchRepostedVideos(null)
        val elapsed = System.currentTimeMillis() - started
        "endpoint=${page.endpointName} status=${page.statusCode ?: "n/a"} items=${page.items.size} cursor=${if (page.cursor != null) "present" else "none"} hasMore=${page.hasMore} elapsedMs=$elapsed"
    }

    private fun testRemoveMock() = runDiagnostic("removeRepostMock") {
        val client = TikTokClientFactory.create(this, ClientMode.MOCK)
        val started = System.currentTimeMillis()
        val result = client.removeRepost("mock_video_diag_1")
        val elapsed = System.currentTimeMillis() - started
        "endpoint=${result.endpointName} status=${result.statusCode ?: "n/a"} success=${result.success} category=${result.category ?: "none"} elapsedMs=$elapsed"
    }

    private fun confirmRemoveOneFirst() {
        AlertDialog.Builder(this)
            .setTitle("Remove 1 repost test")
            .setMessage("Mode ini akan mencoba menghapus 1 repost dari akun TikTok yang login di WebView. Endpoint ini tidak resmi, bisa gagal, berubah, atau memicu rate-limit/challenge. App tidak akan bypass proteksi TikTok. Lanjutkan hanya untuk akun sendiri.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand") { _, _ -> confirmRemoveOneSecond() }
            .show()
    }

    private fun confirmRemoveOneSecond() {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi terakhir")
            .setMessage("Test ini hanya fetch satu page, mencoba remove 1 item, lalu stop. Tidak ada loop massal.")
            .setNegativeButton("Cancel", null)
            .setPositiveButton("I understand, remove 1 item") { _, _ -> removeOneReal() }
            .show()
    }

    private fun removeOneReal() = runDiagnostic("removeOneReal") {
        if (!TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS) throw TikTokApiException(TikTokApiErrorCategory.EndpointDisabled, endpointName = "removeOneReal")
        val client = TikTokClientFactory.create(this, ClientMode.REAL_UNOFFICIAL_EXPERIMENTAL)
        val started = System.currentTimeMillis()
        val page = client.fetchRepostedVideos(null)
        val first = page.items.firstOrNull() ?: throw TikTokApiException(TikTokApiErrorCategory.Unknown, page.statusCode, "removeOneReal")
        val result = client.removeRepost(first.videoId)
        val elapsed = System.currentTimeMillis() - started
        "endpoint=${result.endpointName} status=${result.statusCode ?: "n/a"} success=${result.success} category=${result.category ?: "none"} elapsedMs=$elapsed"
    }

    private fun runDiagnostic(name: String, block: suspend () -> String) {
        append("Running $name...")
        scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { block() }.getOrElse { error ->
                    when (error) {
                        is TikTokApiException -> "endpoint=$name status=${error.statusCode ?: "n/a"} category=${error.category} elapsedMs=n/a"
                        else -> "endpoint=$name status=n/a category=Unknown elapsedMs=n/a"
                    }
                }
            }
            append(text)
        }
    }

    private fun append(text: String) {
        lines.addFirst("• ${text.take(900)}")
        while (lines.size > 40) lines.removeLast()
        output.text = lines.joinToString("\n")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
