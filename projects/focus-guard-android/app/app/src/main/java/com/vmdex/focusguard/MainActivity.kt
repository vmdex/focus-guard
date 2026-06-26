package com.vmdex.focusguard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.vmdex.focusguard.ui.theme.FocusGuardAndroidTheme

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private var foregroundAppState by mutableStateOf<ForegroundAppState>(ForegroundAppState.Unknown)
    private var currentTimeMillis by mutableStateOf(System.currentTimeMillis())
    private var alertState by mutableStateOf(AlertState())
    private var settings by mutableStateOf(FocusGuardSettings())
    private var alertedSessionKey: String? = null
    private lateinit var notifier: FocusGuardNotifier
    private lateinit var settingsStore: FocusGuardSettingsStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        settingsStore = FocusGuardSettingsStore(this)
        settings = settingsStore.load()
        notifier = FocusGuardNotifier(this)
        notifier.createLimitChannel()
        requestNotificationPermissionIfNeeded()
        refreshUsageData()

        setContent {
            FocusGuardAndroidTheme {
                FocusGuardApp(
                    hasUsageAccess = hasUsageAccess,
                    foregroundAppState = foregroundAppState,
                    currentTimeMillis = currentTimeMillis,
                    alertState = alertState,
                    settings = settings,
                    packageName = packageName,
                    onRefreshUsageData = ::refreshUsageData,
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
        // The UI still drives monitoring in this prototype. A foreground service will own this loop later.
        currentTimeMillis = System.currentTimeMillis()
        hasUsageAccess = hasUsageAccessPermission(this)
        foregroundAppState = if (hasUsageAccess) {
            readLatestForegroundApp(this, settings.gracePeriodMillis)
        } else {
            ForegroundAppState.PermissionMissing
        }

        maybeShowLimitExceededNotification()
    }

    private fun maybeShowLimitExceededNotification() {
        // Notify once per session, otherwise the one-second refresh loop would spam the user.
        val detected = foregroundAppState as? ForegroundAppState.Detected ?: return
        if (detected.sessionStatus == SessionStatus.Ended) {
            return
        }

        val elapsedMillis = calculateSessionElapsedMillis(detected, currentTimeMillis)
        if (elapsedMillis < settings.sessionLimitMillis) {
            return
        }

        val sessionKey = detected.sessionKey
        if (alertedSessionKey == sessionKey) {
            return
        }

        val wasSent = notifier.showLimitExceeded(detected.packageName, elapsedMillis)
        alertedSessionKey = sessionKey
        alertState = AlertState(
            wasSent = wasSent,
            lastAlertTimeMillis = if (wasSent) currentTimeMillis else null,
            lastAlertPackageName = if (wasSent) detected.packageName else null
        )
    }

    private fun applySettings(newSettings: FocusGuardSettings) {
        settings = newSettings
        settingsStore.save(newSettings)
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
}
