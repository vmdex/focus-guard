package com.vmdex.focusguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vmdex.focusguard.ui.theme.FocusGuardAndroidTheme

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private var hasOverlayAccess by mutableStateOf(false)
    private var foregroundAppState by mutableStateOf<ForegroundAppState>(ForegroundAppState.Unknown)
    private var currentTimeMillis by mutableStateOf(System.currentTimeMillis())
    private var alertState by mutableStateOf(AlertState())
    private var settings by mutableStateOf(FocusGuardSettings())
    private var effectiveSettings by mutableStateOf(FocusGuardSettings())
    private var watcherState by mutableStateOf(WatcherState())
    private lateinit var settingsStore: FocusGuardSettingsStore
    private lateinit var watcherStateStore: WatcherStateStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsStore = FocusGuardSettingsStore(this)
        watcherStateStore = WatcherStateStore(this)
        settings = settingsStore.load()
        watcherState = watcherStateStore.load()
        effectiveSettings = settings
        requestNotificationPermissionIfNeeded()
        refreshUsageData()

        setContent {
            FocusGuardAndroidTheme {
                FocusGuardApp(
                    hasUsageAccess = hasUsageAccess,
                    hasOverlayAccess = hasOverlayAccess,
                    foregroundAppState = foregroundAppState,
                    currentTimeMillis = currentTimeMillis,
                    alertState = alertState,
                    settings = settings,
                    effectiveSettings = effectiveSettings,
                    hasPendingSettings = settings != effectiveSettings,
                    watcherState = watcherState,
                    packageName = packageName,
                    onRefreshUsageData = ::refreshUsageData,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onStartMonitoring = ::startMonitoring,
                    onStopMonitoring = ::stopMonitoring,
                    onSettingsChanged = ::applySettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsageData()
    }

    private fun refreshUsageData() {
        currentTimeMillis = System.currentTimeMillis()
        watcherState = watcherStateStore.load()
        hasUsageAccess = hasUsageAccessPermission(this)
        hasOverlayAccess = hasOverlayPermission(this)

        if (!watcherState.isRunning) {
            foregroundAppState = ForegroundAppState.Unknown
            alertState = AlertState()
            effectiveSettings = settings
            return
        }

        foregroundAppState = watcherState.foregroundAppState
        alertState = watcherState.alertState
        effectiveSettings = watcherState.effectiveSettings
    }

    private fun applySettings(newSettings: FocusGuardSettings) {
        settings = newSettings
        settingsStore.save(newSettings)
        if (!watcherState.isRunning) {
            effectiveSettings = newSettings
        }
        refreshUsageData()
    }

    private fun startMonitoring() {
        val now = System.currentTimeMillis()
        UsageWatcherService.start(this)
        watcherState = WatcherState(isRunning = true, lastTickTimeMillis = now, effectiveSettings = effectiveSettings)
        watcherStateStore.save(watcherState)
        refreshUsageData()
    }

    private fun stopMonitoring() {
        UsageWatcherService.stop(this)
        watcherState = WatcherState(isRunning = false, lastTickTimeMillis = null)
        watcherStateStore.save(watcherState)
        alertState = AlertState()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        requestPermissions(
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            NotificationPermissionRequestCode
        )
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
