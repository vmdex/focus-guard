package com.vmdex.focusguard

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import com.vmdex.focusguard.ui.theme.FocusGuardAndroidTheme

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private var hasOverlayAccess by mutableStateOf(false)
    private var foregroundAppState by mutableStateOf<ForegroundAppState>(ForegroundAppState.Unknown)
    private var currentTimeMillis by mutableStateOf(System.currentTimeMillis())
    private var alertState by mutableStateOf(AlertState())
    private var settings by mutableStateOf(FocusGuardSettings())
    private var interventionSettings by mutableStateOf(InterventionSettings())
    private var debugSettings by mutableStateOf(DebugSettings())
    private var effectiveSettings by mutableStateOf(FocusGuardSettings())
    private var watcherState by mutableStateOf(WatcherState())
    private var launchableApps by mutableStateOf(emptyList<LaunchableApp>())
    private var selectedTrackedPackages by mutableStateOf(emptySet<String>())
    private lateinit var settingsStore: FocusGuardSettingsStore
    private lateinit var interventionSettingsStore: InterventionSettingsStore
    private lateinit var debugSettingsStore: DebugSettingsStore
    private lateinit var watcherStateStore: WatcherStateStore
    private lateinit var sessionStateStore: SessionStateStore
    private lateinit var trackedAppsStore: TrackedAppsStore
    private lateinit var installedAppProvider: InstalledAppProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsStore = FocusGuardSettingsStore(this)
        interventionSettingsStore = InterventionSettingsStore(this)
        debugSettingsStore = DebugSettingsStore(this)
        watcherStateStore = WatcherStateStore(this)
        sessionStateStore = SessionStateStore(this)
        trackedAppsStore = TrackedAppsStore(this)
        installedAppProvider = InstalledAppProvider(this)
        settings = settingsStore.load()
        interventionSettings = interventionSettingsStore.load()
        debugSettings = debugSettingsStore.load()
        selectedTrackedPackages = trackedAppsStore.load()
        launchableApps = installedAppProvider.loadLaunchableApps()
        watcherState = watcherStateStore.load()
        effectiveSettings = settings
        requestNotificationPermissionIfNeeded()
        resumeMonitoringServiceIfNeeded()
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
                    interventionSettings = interventionSettings,
                    debugSettings = debugSettings,
                    effectiveSettings = effectiveSettings,
                    hasPendingSettings = settings != effectiveSettings,
                    watcherState = watcherState,
                    launchableApps = launchableApps,
                    selectedTrackedPackages = selectedTrackedPackages,
                    packageName = packageName,
                    onRefreshUsageData = ::refreshUsageData,
                    onOpenOverlaySettings = ::openOverlaySettings,
                    onResetSession = ::resetSession,
                    onStartMonitoring = ::startMonitoring,
                    onStopMonitoring = ::stopMonitoring,
                    onTrackedAppsChanged = ::applyTrackedApps,
                    onInterventionSettingsChanged = ::applyInterventionSettings,
                    onDebugSettingsChanged = ::applyDebugSettings,
                    onSettingsChanged = ::applySettings
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        launchableApps = installedAppProvider.loadLaunchableApps()
        resumeMonitoringServiceIfNeeded()
        refreshUsageData()
    }

    private fun refreshUsageData() {
        currentTimeMillis = System.currentTimeMillis()
        watcherState = watcherStateStore.load()
        selectedTrackedPackages = trackedAppsStore.load()
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

    private fun resumeMonitoringServiceIfNeeded() {
        if (watcherStateStore.load().isRunning) {
            UsageWatcherService.start(this)
        }
    }

    private fun applySettings(newSettings: FocusGuardSettings) {
        settings = newSettings
        settingsStore.save(newSettings)
        if (!watcherState.isRunning) {
            effectiveSettings = newSettings
        }
        refreshUsageData()
    }

    private fun applyInterventionSettings(newSettings: InterventionSettings) {
        interventionSettings = newSettings
        interventionSettingsStore.save(newSettings)
        refreshUsageData()
    }

    private fun applyDebugSettings(newSettings: DebugSettings) {
        debugSettings = newSettings
        debugSettingsStore.save(newSettings)
        refreshUsageData()
    }

    private fun applyTrackedApps(packageNames: Set<String>) {
        selectedTrackedPackages = packageNames
        trackedAppsStore.save(packageNames)
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

    private fun resetSession() {
        sessionStateStore.clear()
        val resetState = watcherStateStore.load().copy(
            foregroundAppState = ForegroundAppState.Unknown,
            alertState = AlertState(),
            interventionState = InterventionState(),
            sessionResetTimeMillis = System.currentTimeMillis()
        )
        watcherStateStore.save(resetState)
        refreshUsageData()
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
            "package:$packageName".toUri()
        )
        startActivity(intent)
    }
}
