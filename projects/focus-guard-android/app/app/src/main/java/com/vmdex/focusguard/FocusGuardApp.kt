package com.vmdex.focusguard

import android.content.Intent
import android.provider.Settings
import android.app.usage.UsageEvents
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vmdex.focusguard.ui.theme.FocusGuardAndroidTheme

@Composable
fun FocusGuardApp(
    hasUsageAccess: Boolean,
    hasOverlayAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    settings: FocusGuardSettings,
    effectiveSettings: FocusGuardSettings,
    hasPendingSettings: Boolean,
    watcherState: WatcherState,
    packageName: String,
    onRefreshUsageData: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onResetSession: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onSettingsChanged: (FocusGuardSettings) -> Unit
) {
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        UsageAccessScreen(
            hasUsageAccess = hasUsageAccess,
            hasOverlayAccess = hasOverlayAccess,
            foregroundAppState = foregroundAppState,
            currentTimeMillis = currentTimeMillis,
            alertState = alertState,
            settings = settings,
            effectiveSettings = effectiveSettings,
            hasPendingSettings = hasPendingSettings,
            watcherState = watcherState,
            packageName = packageName,
            onRefreshUsageData = onRefreshUsageData,
            onOpenOverlaySettings = onOpenOverlaySettings,
            onResetSession = onResetSession,
            onStartMonitoring = onStartMonitoring,
            onStopMonitoring = onStopMonitoring,
            onSettingsChanged = onSettingsChanged,
            modifier = Modifier.padding(innerPadding)
        )
    }
}

@Composable
private fun UsageAccessScreen(
    hasUsageAccess: Boolean,
    hasOverlayAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    settings: FocusGuardSettings,
    effectiveSettings: FocusGuardSettings,
    hasPendingSettings: Boolean,
    watcherState: WatcherState,
    packageName: String,
    onRefreshUsageData: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onResetSession: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onSettingsChanged: (FocusGuardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Strict off: no automatic usage refresh runs while monitoring is disabled.
    LaunchedEffect(watcherState.isRunning) {
        if (watcherState.isRunning) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                onRefreshUsageData()
            }
        }
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Focus Guard",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "App usage watcher prototype",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            PermissionStatusCard(
                hasUsageAccess = hasUsageAccess,
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )

            OverlayStatusCard(
                hasOverlayAccess = hasOverlayAccess,
                onOpenSettings = onOpenOverlaySettings
            )

            MonitoringCard(
                watcherState = watcherState,
                onStartMonitoring = onStartMonitoring,
                onStopMonitoring = onStopMonitoring
            )

            DevSettingsCard(
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )

            DevInfoCard(
                packageName = packageName,
                hasUsageAccess = hasUsageAccess,
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis,
                alertState = alertState,
                settings = settings,
                effectiveSettings = effectiveSettings,
                hasPendingSettings = hasPendingSettings,
                watcherState = watcherState,
                onRefreshUsageData = onRefreshUsageData,
                onResetSession = onResetSession
            )
        }
    }
}

@Composable
private fun PermissionStatusCard(
    hasUsageAccess: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Usage Access",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Permission", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (hasUsageAccess) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasUsageAccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Focus Guard needs this permission to detect which app is currently being used and later measure uninterrupted app sessions.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Open usage access settings")
            }
        }
    }
}

@Composable
private fun OverlayStatusCard(
    hasOverlayAccess: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Floating debug window",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Overlay permission", style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (hasOverlayAccess) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasOverlayAccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Focus Guard uses this debug window as a visible sign that monitoring is currently active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Open overlay settings")
            }
        }
    }
}

@Composable
private fun MonitoringCard(
    watcherState: WatcherState,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Monitoring",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            DevInfoRow(label = "Monitoring", value = if (watcherState.isRunning) "On" else "Off")
            DevInfoRow(
                label = "Last service tick",
                value = watcherState.lastTickTimeMillis?.let(::formatTimestamp) ?: "-"
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = onStartMonitoring,
                    enabled = !watcherState.isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Start monitoring")
                }

                Button(
                    onClick = onStopMonitoring,
                    enabled = watcherState.isRunning,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Stop")
                }
            }

            Text(
                text = "When monitoring is off, Focus Guard does not refresh usage data, count sessions, or show limit alerts.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DevSettingsCard(
    settings: FocusGuardSettings,
    onSettingsChanged: (FocusGuardSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Dev settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            SecondsField(
                label = "Grace period seconds",
                value = settings.gracePeriodSeconds,
                onValueChanged = { seconds ->
                    onSettingsChanged(settings.copy(gracePeriodMillis = seconds * 1000L))
                }
            )

            SecondsField(
                label = "Session limit seconds",
                value = settings.sessionLimitSeconds,
                onValueChanged = { seconds ->
                    onSettingsChanged(settings.copy(sessionLimitMillis = seconds * 1000L))
                }
            )

            SecondsField(
                label = "Alert delay after resume seconds",
                value = settings.alertDelayAfterResumeSeconds,
                onValueChanged = { seconds ->
                    onSettingsChanged(settings.copy(alertDelayAfterResumeMillis = seconds * 1000L))
                }
            )

            Text(
                text = "Saved locally. Active sessions keep their original settings; changes apply to the next session.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SecondsField(
    label: String,
    value: Int,
    onValueChanged: (Int) -> Unit
) {
    OutlinedTextField(
        value = value.toString(),
        onValueChange = { text ->
            text.toIntOrNull()
                ?.coerceAtLeast(1)
                ?.let(onValueChanged)
        },
        label = { Text(text = label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number)
    )
}

@Composable
private fun DevInfoCard(
    packageName: String,
    hasUsageAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    settings: FocusGuardSettings,
    effectiveSettings: FocusGuardSettings,
    hasPendingSettings: Boolean,
    watcherState: WatcherState,
    onRefreshUsageData: () -> Unit,
    onResetSession: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Dev info",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            DevInfoRow(label = "Package", value = packageName)
            DevInfoRow(label = "Usage access", value = if (hasUsageAccess) "true" else "false")
            DevInfoRow(label = "Own package ignored", value = "true")
            DevInfoRow(label = "Tracked apps", value = TrackedAppPackages.size.toString())
            DevInfoRow(label = "Session strategy", value = "Grace period")
            DevInfoRow(label = "Pending settings", value = hasPendingSettings.toString())
            DevInfoRow(
                label = "Reset session time",
                value = watcherState.sessionResetTimeMillis?.let(::formatTimestamp) ?: "-"
            )
            DevInfoRow(label = "Grace period", value = formatElapsed(effectiveSettings.gracePeriodMillis))
            DevInfoRow(label = "Session limit", value = formatElapsed(effectiveSettings.sessionLimitMillis))
            DevInfoRow(
                label = "Alert delay after resume",
                value = formatElapsed(effectiveSettings.alertDelayAfterResumeMillis)
            )
            Text(
                text = TrackedAppPackages.joinToString(separator = "\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ForegroundAppRows(foregroundAppState)
            CurrentSessionRows(
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis,
                settings = effectiveSettings,
                alertState = alertState
            )
            AlertRows(alertState)

            Button(
                onClick = onRefreshUsageData,
                enabled = watcherState.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Refresh usage data")
            }

            Button(
                onClick = onResetSession,
                enabled = watcherState.isRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Reset session")
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Strict off is enabled: usage data is read only while monitoring is on.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AlertRows(alertState: AlertState) {
    DevInfoRow(label = "Alert sent", value = alertState.wasSent.toString())
    DevInfoRow(label = "Last alert app", value = alertState.lastAlertPackageName ?: "-")
    DevInfoRow(label = "Alerted session key", value = alertState.alertedSessionKey ?: "-")
    DevInfoRow(
        label = "Last alert time",
        value = alertState.lastAlertTimeMillis?.let(::formatTimestamp) ?: "-"
    )
}

@Composable
private fun ForegroundAppRows(foregroundAppState: ForegroundAppState) {
    when (foregroundAppState) {
        ForegroundAppState.PermissionMissing -> {
            DevInfoRow(label = "Detected app", value = "permission missing")
        }

        ForegroundAppState.Unknown -> {
            DevInfoRow(label = "Detected app", value = "unknown")
        }

        is ForegroundAppState.Untracked -> {
            DevInfoRow(label = "Detected app", value = foregroundAppState.packageName)
            DevInfoRow(label = "Tracked", value = "false")
        }

        is ForegroundAppState.Detected -> {
            DevInfoRow(label = "Detected app", value = foregroundAppState.packageName)
            DevInfoRow(label = "Tracked", value = foregroundAppState.isTracked.toString())
            DevInfoRow(label = "Session status", value = sessionStatusLabel(foregroundAppState.sessionStatus))
            DevInfoRow(label = "Last foreground", value = foregroundAppState.lastForegroundPackageName)
            DevInfoRow(label = "Class", value = foregroundAppState.className ?: "-")
            DevInfoRow(label = "Event", value = eventTypeLabel(foregroundAppState.eventType))
            DevInfoRow(label = "Event time", value = formatTimestamp(foregroundAppState.timestampMillis))
        }
    }
}

@Composable
private fun CurrentSessionRows(
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    settings: FocusGuardSettings,
    alertState: AlertState
) {
    when (foregroundAppState) {
        is ForegroundAppState.Detected -> {
            val elapsedMillis = calculateSessionElapsedMillis(
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis
            )
            val isLimitExceeded = elapsedMillis >= settings.sessionLimitMillis

            DevInfoRow(label = "Session app", value = foregroundAppState.packageName)
            DevInfoRow(label = "Session key", value = foregroundAppState.sessionKey)
            DevInfoRow(
                label = "Alert sent for session",
                value = (alertState.alertedSessionKey == foregroundAppState.sessionKey).toString()
            )
            DevInfoRow(label = "Session started", value = formatTimestamp(foregroundAppState.timestampMillis))
            DevInfoRow(label = "Session elapsed", value = formatElapsed(elapsedMillis))
            DevInfoRow(
                label = "Current active elapsed",
                value = formatElapsed(foregroundAppState.currentActiveElapsedMillis)
            )
            DevInfoRow(label = "Limit status", value = if (isLimitExceeded) "Exceeded" else "Within limit")

            if (foregroundAppState.sessionStatus == SessionStatus.GracePeriod &&
                foregroundAppState.interruptionStartedAtMillis != null
            ) {
                val graceElapsed = currentTimeMillis - foregroundAppState.interruptionStartedAtMillis
                val graceRemaining = settings.gracePeriodMillis - graceElapsed

                DevInfoRow(label = "Grace remaining", value = formatElapsed(graceRemaining))
            }
        }

        else -> {
            DevInfoRow(label = "Session app", value = "-")
            DevInfoRow(label = "Session elapsed", value = "00:00")
            DevInfoRow(label = "Current active elapsed", value = "00:00")
            DevInfoRow(label = "Limit status", value = "-")
        }
    }
}

@Composable
private fun DevInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun eventTypeLabel(eventType: Int): String {
    return when (eventType) {
        UsageEvents.Event.MOVE_TO_FOREGROUND -> "MOVE_TO_FOREGROUND"
        else -> eventType.toString()
    }
}

private fun sessionStatusLabel(sessionStatus: SessionStatus): String {
    return when (sessionStatus) {
        SessionStatus.Active -> "Active"
        SessionStatus.GracePeriod -> "Grace period"
        SessionStatus.Ended -> "Ended"
    }
}

@Preview(showBackground = true)
@Composable
private fun UsageAccessScreenPreview() {
    FocusGuardAndroidTheme {
        UsageAccessScreen(
            hasUsageAccess = false,
            hasOverlayAccess = false,
            foregroundAppState = ForegroundAppState.PermissionMissing,
            currentTimeMillis = System.currentTimeMillis(),
            alertState = AlertState(),
            settings = FocusGuardSettings(),
            effectiveSettings = FocusGuardSettings(),
            hasPendingSettings = false,
            watcherState = WatcherState(),
            packageName = "com.vmdex.focusguard",
            onRefreshUsageData = {},
            onOpenOverlaySettings = {},
            onResetSession = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
            onSettingsChanged = {}
        )
    }
}
