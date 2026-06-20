package com.example.tiktokunreposter.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.tiktokunreposter.config.AppModeStore
import com.example.tiktokunreposter.session.TikTokSessionManager
import com.example.tiktokunreposter.tiktok.TikTokEndpoints

class BackgroundReadinessActivity : AppCompatActivity() {
    private lateinit var sessionManager: TikTokSessionManager
    private lateinit var output: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionManager = TikTokSessionManager(this)
        buildUi()
        checkReadiness()
    }

    private fun buildUi() {
        output = TextView(this).apply { textSize = 15f }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }
        fun button(label: String, onClick: () -> Unit): Button = Button(this).apply {
            text = label
            setOnClickListener { onClick() }
            root.addView(this, LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        root.addView(output)
        button("Check Readiness") { checkReadiness() }
        button("Open App Notification Settings") { openNotificationSettings() }
        button("Open Battery Optimization Settings") { openBatterySettings() }
        setContentView(ScrollView(this).apply {
            addView(root, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
    }

    private fun checkReadiness() {
        val notificationGranted = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val batteryIgnored = isIgnoringBatteryOptimizations()
        val network = hasNetwork()
        val session = sessionManager.describeSessionSafely()
        output.text = buildString {
            appendLine("Notification permission granted: ${yesNo(notificationGranted)}")
            appendLine("Battery optimization ignored: ${yesNo(batteryIgnored)}")
            appendLine("Foreground service declared: yes")
            appendLine("Network available: ${yesNo(network)}")
            appendLine("Session exists: ${yesNo(session.hasCookies)}")
            appendLine("App mode: ${AppModeStore.getMode(this@BackgroundReadinessActivity).name}")
            appendLine("Unofficial endpoint enabled: ${yesNo(TikTokEndpoints.ENABLE_UNOFFICIAL_WEB_ENDPOINTS)}")
            appendLine("\nCatatan: app tidak memaksa disable battery optimization. Ini cuma checklist supaya testing foreground service lebih jelas.")
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            pm.isIgnoringBatteryOptimizations(packageName)
        } else true
    }

    private fun hasNetwork(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun openNotificationSettings() {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(Uri.parse("package:$packageName"))
        }
        startActivity(intent)
    }

    private fun openBatterySettings() {
        val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        startActivity(intent)
    }

    private fun yesNo(value: Boolean) = if (value) "yes" else "no"
}
