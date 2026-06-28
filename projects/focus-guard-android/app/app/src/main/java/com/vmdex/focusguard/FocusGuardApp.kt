package com.vmdex.focusguard

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vmdex.focusguard.ui.theme.FocusGuardAndroidTheme
import kotlin.time.Duration.Companion.seconds

@Composable
fun FocusGuardApp(
    hasUsageAccess: Boolean,
    hasOverlayAccess: Boolean,
    hasNotificationAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    settings: FocusGuardSettings,
    interventionSettings: InterventionSettings,
    debugSettings: DebugSettings,
    effectiveSettings: FocusGuardSettings,
    hasPendingSettings: Boolean,
    watcherState: WatcherState,
    launchableApps: List<LaunchableApp>,
    selectedTrackedPackages: Set<String>,
    packageName: String,
    onRefreshUsageData: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onResetSession: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onTrackedAppsChanged: (Set<String>) -> Unit,
    onInterventionSettingsChanged: (InterventionSettings) -> Unit,
    onDebugSettingsChanged: (DebugSettings) -> Unit,
    onSettingsChanged: (FocusGuardSettings) -> Unit
) {
    var screen by rememberSaveable { mutableStateOf(FocusGuardScreen.Main) }

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        when (screen) {
            FocusGuardScreen.Main -> UsageAccessScreen(
                hasUsageAccess = hasUsageAccess,
                hasOverlayAccess = hasOverlayAccess,
                hasNotificationAccess = hasNotificationAccess,
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis,
                alertState = alertState,
                settings = settings,
                interventionSettings = interventionSettings,
                debugSettings = debugSettings,
                effectiveSettings = effectiveSettings,
                hasPendingSettings = hasPendingSettings,
                watcherState = watcherState,
                selectedTrackedPackages = selectedTrackedPackages,
                packageName = packageName,
                onRefreshUsageData = onRefreshUsageData,
                onOpenNotificationSettings = onOpenNotificationSettings,
                onOpenOverlaySettings = onOpenOverlaySettings,
                onResetSession = onResetSession,
                onStartMonitoring = onStartMonitoring,
                onStopMonitoring = onStopMonitoring,
                onChooseApps = { screen = FocusGuardScreen.ChooseApps },
                onConfigureInterventions = { screen = FocusGuardScreen.ConfigureInterventions },
                onDebugSettingsChanged = onDebugSettingsChanged,
                onSettingsChanged = onSettingsChanged,
                modifier = Modifier.padding(innerPadding)
            )

            FocusGuardScreen.ChooseApps -> ChooseAppsScreen(
                launchableApps = launchableApps,
                selectedTrackedPackages = selectedTrackedPackages,
                onApply = { packageNames ->
                    onTrackedAppsChanged(packageNames)
                    screen = FocusGuardScreen.Main
                },
                onBack = { screen = FocusGuardScreen.Main },
                modifier = Modifier.padding(innerPadding)
            )

            FocusGuardScreen.ConfigureInterventions -> ConfigureInterventionsScreen(
                interventionSettings = interventionSettings,
                onApply = { settings ->
                    onInterventionSettingsChanged(settings)
                    screen = FocusGuardScreen.Main
                },
                onBack = { screen = FocusGuardScreen.Main },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

private enum class FocusGuardScreen {
    Main,
    ChooseApps,
    ConfigureInterventions
}

@Composable
private fun UsageAccessScreen(
    hasUsageAccess: Boolean,
    hasOverlayAccess: Boolean,
    hasNotificationAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    settings: FocusGuardSettings,
    interventionSettings: InterventionSettings,
    debugSettings: DebugSettings,
    effectiveSettings: FocusGuardSettings,
    hasPendingSettings: Boolean,
    watcherState: WatcherState,
    selectedTrackedPackages: Set<String>,
    packageName: String,
    onRefreshUsageData: () -> Unit,
    onOpenNotificationSettings: () -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onResetSession: () -> Unit,
    onStartMonitoring: () -> Unit,
    onStopMonitoring: () -> Unit,
    onChooseApps: () -> Unit,
    onConfigureInterventions: () -> Unit,
    onDebugSettingsChanged: (DebugSettings) -> Unit,
    onSettingsChanged: (FocusGuardSettings) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    // Strict off: no automatic usage refresh runs while monitoring is disabled.
    LaunchedEffect(watcherState.isRunning) {
        if (watcherState.isRunning) {
            while (true) {
                kotlinx.coroutines.delay(1.seconds)
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

            FocusSettingsCard(
                settings = settings,
                onSettingsChanged = onSettingsChanged
            )

            InterventionSettingsCard(
                interventionSettings = interventionSettings,
                onConfigureInterventions = onConfigureInterventions
            )

            NotificationStatusCard(
                hasNotificationAccess = hasNotificationAccess,
                onOpenSettings = onOpenNotificationSettings
            )

            TrackedAppsCard(
                selectedTrackedPackages = selectedTrackedPackages,
                onChooseApps = onChooseApps
            )

            MonitoringCard(
                watcherState = watcherState,
                onStartMonitoring = onStartMonitoring,
                onStopMonitoring = onStopMonitoring
            )

            DevSettingsCard(
                debugSettings = debugSettings,
                onDebugSettingsChanged = onDebugSettingsChanged
            )

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

            DevInfoCard(
                packageName = packageName,
                hasUsageAccess = hasUsageAccess,
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis,
                alertState = alertState,
                effectiveSettings = effectiveSettings,
                hasPendingSettings = hasPendingSettings,
                watcherState = watcherState,
                selectedTrackedPackages = selectedTrackedPackages,
                onRefreshUsageData = onRefreshUsageData,
                onResetSession = onResetSession
            )
        }
    }
}

@Composable
private fun ChooseAppsScreen(
    launchableApps: List<LaunchableApp>,
    selectedTrackedPackages: Set<String>,
    onApply: (Set<String>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var searchText by rememberSaveable { mutableStateOf("") }
    var draftSelectedPackages by remember(selectedTrackedPackages) {
        mutableStateOf(selectedTrackedPackages)
    }
    val pinnedPackages = remember(selectedTrackedPackages) { selectedTrackedPackages }
    val hasChanges = draftSelectedPackages != selectedTrackedPackages
    val visibleApps = visibleAppsForSelection(
        apps = launchableApps,
        pinnedPackages = pinnedPackages,
        searchText = searchText
    )

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Choose apps",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Button(onClick = { onApply(draftSelectedPackages) }) {
                    Text(text = if (hasChanges) "✓ Save changes" else "✓")
                }
            }

            OutlinedTextField(
                value = searchText,
                onValueChange = { searchText = it },
                label = { Text(text = "Search apps") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Text(
                text = "${draftSelectedPackages.size} tracking apps",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(
                    items = visibleApps,
                    key = { app -> app.packageName }
                ) { app ->
                    ChooseAppRow(
                        app = app,
                        isSelected = app.packageName in draftSelectedPackages,
                        onSelectionChanged = { isSelected ->
                            draftSelectedPackages = if (isSelected) {
                                draftSelectedPackages + app.packageName
                            } else {
                                draftSelectedPackages - app.packageName
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChooseAppRow(
    app: LaunchableApp,
    isSelected: Boolean,
    onSelectionChanged: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onSelectionChanged(!isSelected) }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Checkbox(
            checked = isSelected,
            onCheckedChange = onSelectionChanged
        )
        Text(
            text = app.appName,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ConfigureInterventionsScreen(
    interventionSettings: InterventionSettings,
    onApply: (InterventionSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var draftSettings by remember(interventionSettings) {
        mutableStateOf(interventionSettings)
    }
    val hasChanges = draftSettings != interventionSettings
    val scrollState = rememberScrollState()

    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Intervention settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold
                )

                Button(onClick = { onApply(draftSettings) }) {
                    Text(text = if (hasChanges) "✓ Apply" else "✓")
                }
            }

            InterventionSwitchRow(
                label = "Android heads-up notification",
                checked = draftSettings.isNotificationEnabled,
                onCheckedChange = { isEnabled ->
                    draftSettings = draftSettings.copy(isNotificationEnabled = isEnabled)
                }
            )

            OutlinedTextField(
                value = draftSettings.notificationTitle,
                onValueChange = { text ->
                    draftSettings = draftSettings.copy(notificationTitle = text)
                },
                label = { Text(text = "Notification title") },
                enabled = draftSettings.isNotificationEnabled,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = draftSettings.notificationMessage,
                onValueChange = { text ->
                    draftSettings = draftSettings.copy(notificationMessage = text)
                },
                label = { Text(text = "Notification message") },
                enabled = draftSettings.isNotificationEnabled,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            InterventionSwitchRow(
                label = "Custom overlay popup",
                checked = draftSettings.isPopupEnabled,
                onCheckedChange = { isEnabled ->
                    draftSettings = draftSettings.copy(isPopupEnabled = isEnabled)
                }
            )

            OutlinedTextField(
                value = draftSettings.popupMessage,
                onValueChange = { text ->
                    draftSettings = draftSettings.copy(popupMessage = text)
                },
                label = { Text(text = "Popup message") },
                enabled = draftSettings.isPopupEnabled,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Button(
                onClick = { draftSettings = InterventionSettings() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Restore defaults")
            }

            if (!draftSettings.isNotificationEnabled && !draftSettings.isPopupEnabled) {
                Text(
                    text = "No intervention channel enabled.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun InterventionSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
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
                text = "Float window",
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
private fun NotificationStatusCard(
    hasNotificationAccess: Boolean,
    onOpenSettings: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Notifications",
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
                    text = if (hasNotificationAccess) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasNotificationAccess) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Heads-up notifications use Android notification permission and channel settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Button(
                onClick = onOpenSettings,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Open notification settings")
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
    debugSettings: DebugSettings,
    onDebugSettingsChanged: (DebugSettings) -> Unit
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Float debug window", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = debugSettings.isFloatingDebugWindowEnabled,
                    onCheckedChange = { isEnabled ->
                        onDebugSettingsChanged(
                            debugSettings.copy(isFloatingDebugWindowEnabled = isEnabled)
                        )
                    }
                )
            }

            SecondsField(
                label = "Active tick seconds",
                value = debugSettings.activeTickSeconds,
                onValueChanged = { seconds ->
                    onDebugSettingsChanged(debugSettings.copy(activeTickMillis = seconds * 1000L))
                }
            )

            SecondsField(
                label = "Grace tick seconds",
                value = debugSettings.graceTickSeconds,
                onValueChanged = { seconds ->
                    onDebugSettingsChanged(debugSettings.copy(graceTickMillis = seconds * 1000L))
                }
            )

            SecondsField(
                label = "Idle tick seconds",
                value = debugSettings.idleTickSeconds,
                onValueChanged = { seconds ->
                    onDebugSettingsChanged(debugSettings.copy(idleTickMillis = seconds * 1000L))
                }
            )

            SecondsField(
                label = "Locked tick seconds",
                value = debugSettings.screenLockedTickSeconds,
                onValueChanged = { seconds ->
                    onDebugSettingsChanged(debugSettings.copy(screenLockedTickMillis = seconds * 1000L))
                }
            )
        }
    }
}

@Composable
private fun TrackedAppsCard(
    selectedTrackedPackages: Set<String>,
    onChooseApps: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Tracked apps",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Text(
                text = "${selectedTrackedPackages.size} tracking apps",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold
            )

            Button(
                onClick = onChooseApps,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Choose apps")
            }
        }
    }
}

@Composable
private fun InterventionSettingsCard(
    interventionSettings: InterventionSettings,
    onConfigureInterventions: () -> Unit
) {
    val enabledChannels = buildList {
        if (interventionSettings.isNotificationEnabled) {
            add("Android heads-up notification")
        }
        if (interventionSettings.isPopupEnabled) {
            add("Custom overlay popup")
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Intervention settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            if (enabledChannels.isEmpty()) {
                Text(
                    text = "No intervention channel enabled.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                Text(
                    text = enabledChannels.joinToString(separator = "\n"),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onConfigureInterventions,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Configure")
            }
        }
    }
}

@Composable
private fun FocusSettingsCard(
    settings: FocusGuardSettings,
    onSettingsChanged: (FocusGuardSettings) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Focus settings",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Show session timer", style = MaterialTheme.typography.bodyLarge)
                Switch(
                    checked = settings.isSessionTimerEnabled,
                    onCheckedChange = { isEnabled ->
                        onSettingsChanged(settings.copy(isSessionTimerEnabled = isEnabled))
                    }
                )
            }

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
    var text by rememberSaveable(label) { mutableStateOf(value.toString()) }

    LaunchedEffect(value) {
        if (text.isNotBlank() && text.toIntOrNull() != value) {
            text = value.toString()
        }
    }

    OutlinedTextField(
        value = text,
        onValueChange = { nextText ->
            if (nextText.any { !it.isDigit() }) {
                return@OutlinedTextField
            }

            text = nextText
            nextText.toIntOrNull()
                ?.coerceAtLeast(1)
                ?.let(onValueChanged)
        },
        label = { Text(text = label) },
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focusState ->
                if (!focusState.isFocused && text.isBlank()) {
                    text = value.toString()
                }
            },
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
    effectiveSettings: FocusGuardSettings,
    hasPendingSettings: Boolean,
    watcherState: WatcherState,
    selectedTrackedPackages: Set<String>,
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

            DevInfoSectionTitle(text = "App")
            DevInfoRow(label = "Package", value = packageName)
            DevInfoRow(label = "Usage access", value = if (hasUsageAccess) "true" else "false")
            DevInfoRow(label = "Own package ignored", value = "true")
            DevInfoRow(label = "Tracked apps", value = selectedTrackedPackages.size.toString())
            Text(
                text = selectedTrackedPackages.sorted().joinToString(separator = "\n").ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            DevInfoSectionTitle(text = "Settings")
            DevInfoRow(label = "Session strategy", value = "Grace period")
            DevInfoRow(label = "Pending settings", value = hasPendingSettings.toString())
            DevInfoRow(label = "Grace period", value = formatElapsed(effectiveSettings.gracePeriodMillis))
            DevInfoRow(label = "Session limit", value = formatElapsed(effectiveSettings.sessionLimitMillis))
            DevInfoRow(
                label = "Alert delay after resume",
                value = formatElapsed(effectiveSettings.alertDelayAfterResumeMillis)
            )

            DevInfoSectionTitle(text = "Foreground")
            ForegroundAppRows(foregroundAppState)

            DevInfoSectionTitle(text = "Device")
            DeviceInteractionRows(watcherState.deviceInteractionState)

            DevInfoSectionTitle(text = "Service restore")
            ServiceRestoreRows(watcherState.serviceRestoreState)

            DevInfoSectionTitle(text = "Usage events")
            UsageDebugRows(watcherState.usageDebugState)

            DevInfoSectionTitle(text = "Session")
            DevInfoRow(
                label = "Reset session time",
                value = watcherState.sessionResetTimeMillis?.let(::formatTimestamp) ?: "-"
            )
            CurrentSessionRows(
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis,
                settings = effectiveSettings,
                alertState = alertState
            )

            DevInfoSectionTitle(text = "Alerts")
            AlertRows(alertState)

            DevInfoSectionTitle(text = "Intervention")
            InterventionRows(watcherState.interventionState)

            DevInfoSectionTitle(text = "Actions")
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
private fun DevInfoSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp)
    )
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
private fun InterventionRows(interventionState: InterventionState) {
    DevInfoRow(
        label = "Notification status",
        value = interventionNotificationStatusLabel(interventionState.notificationStatus)
    )
    DevInfoRow(
        label = "Notification left",
        value = interventionState.notificationLeftMillis?.let(::formatElapsed) ?: "-"
    )
    DevInfoRow(label = "Intervention session", value = interventionState.sessionKey ?: "-")
}

@Composable
private fun ServiceRestoreRows(serviceRestoreState: ServiceRestoreState) {
    DevInfoRow(label = "Start reason", value = serviceRestoreState.serviceStartReason ?: "-")
    DevInfoRow(
        label = "Start time",
        value = serviceRestoreState.serviceStartTimeMillis?.let(::formatTimestamp) ?: "-"
    )
    DevInfoRow(label = "Had persisted session", value = serviceRestoreState.hadPersistedSession.toString())
    DevInfoRow(label = "Restored session key", value = serviceRestoreState.restoredSessionKey ?: "-")
    DevInfoRow(
        label = "Restored status",
        value = serviceRestoreState.restoredSessionStatus?.let(::sessionStatusLabel) ?: "-"
    )
    DevInfoRow(
        label = "Restored elapsed",
        value = serviceRestoreState.restoredSessionElapsedMillis?.let(::formatElapsed) ?: "-"
    )
}

@Composable
private fun DeviceInteractionRows(deviceInteractionState: DeviceInteractionState) {
    DevInfoRow(label = "Interactive", value = deviceInteractionState.isInteractive.toString())
    DevInfoRow(label = "Keyguard locked", value = deviceInteractionState.isKeyguardLocked.toString())
    DevInfoRow(label = "Screen locked", value = deviceInteractionState.isScreenLocked.toString())
    DevInfoRow(label = "Last screen event", value = deviceInteractionState.lastScreenEvent ?: "-")
    DevInfoRow(
        label = "Last screen event time",
        value = deviceInteractionState.lastScreenEventTimeMillis?.let(::formatTimestamp) ?: "-"
    )
}

@Composable
private fun UsageDebugRows(usageDebugState: UsageDebugState) {
    DevInfoRow(
        label = "Query start",
        value = usageDebugState.queryStartTimeMillis?.let(::formatTimestamp) ?: "-"
    )
    DevInfoRow(
        label = "Query end",
        value = usageDebugState.queryEndTimeMillis?.let(::formatTimestamp) ?: "-"
    )
    DevInfoRow(
        label = "Since time",
        value = usageDebugState.sinceTimeMillis?.let(::formatTimestamp) ?: "-"
    )
    DevInfoRow(label = "Transitions", value = usageDebugState.transitionCount.toString())
    DevInfoRow(
        label = "Resolved foreground",
        value = usageDebugState.resolvedForegroundPackageName ?: "-"
    )
    DevInfoRow(
        label = "Last fg start app",
        value = usageDebugState.lastForegroundStartPackageName ?: "-"
    )
    DevInfoRow(
        label = "Last fg start event",
        value = if (usageDebugState.lastForegroundStartEventType != 0) {
            eventTypeLabel(usageDebugState.lastForegroundStartEventType)
        } else {
            "-"
        }
    )
    DevInfoRow(
        label = "Last fg start time",
        value = usageDebugState.lastForegroundStartTimeMillis?.let(::formatTimestamp) ?: "-"
    )

    Text(
        text = "Recent raw events",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Text(
        text = usageDebugState.recentRawEvents
            .joinToString(separator = "\n") { event -> event.debugLabel() }
            .ifBlank { "-" },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
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

            DevInfoRow(label = "Current app", value = foregroundAppState.packageName)
            DevInfoRow(label = "Session key", value = foregroundAppState.sessionKey)
            DevInfoRow(
                label = "Alert sent for session",
                value = (alertState.alertedSessionKey == foregroundAppState.sessionKey).toString()
            )
            DevInfoRow(label = "Session started", value = formatTimestamp(foregroundAppState.timestampMillis))
            DevInfoRow(label = "Session elapsed", value = formatElapsed(elapsedMillis))
            Text(
                text = "Session app times",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = foregroundAppState.appElapsedMillis
                    .entries
                    .sortedByDescending { it.value }
                    .joinToString(separator = "\n") { (packageName, elapsed) ->
                        "${packageName.substringAfterLast('.')}: ${formatElapsed(elapsed)}"
                    }
                    .ifBlank { "-" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            DevInfoRow(label = "Current app", value = "-")
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
    return usageEventTypeLabel(eventType)
}

private fun UsageRawEventDebugEntry.debugLabel(): String {
    val classSuffix = className?.substringAfterLast('.')?.let { " / $it" }.orEmpty()
    return "${formatTimestamp(timestampMillis)}  ${shortDebugPackageName(packageName)}  ${eventTypeLabel(eventType)}$classSuffix"
}

private fun shortDebugPackageName(packageName: String): String {
    return packageName.substringAfterLast('.')
}

private fun sessionStatusLabel(sessionStatus: SessionStatus): String {
    return when (sessionStatus) {
        SessionStatus.Active -> "Active"
        SessionStatus.PausedByScreenLock -> "Paused by screen lock"
        SessionStatus.GracePeriod -> "Grace period"
        SessionStatus.Ended -> "Ended"
    }
}

private fun interventionNotificationStatusLabel(status: InterventionNotificationStatus): String {
    return when (status) {
        InterventionNotificationStatus.NotNeeded -> "Not needed"
        InterventionNotificationStatus.WaitingLimit -> "Waiting limit"
        InterventionNotificationStatus.WaitingResumeDelay -> "Waiting resume delay"
        InterventionNotificationStatus.ReadyToNotify -> "Ready to notify"
        InterventionNotificationStatus.Sent -> "Sent"
    }
}

@Preview(showBackground = true)
@Composable
private fun UsageAccessScreenPreview() {
    FocusGuardAndroidTheme {
        UsageAccessScreen(
            hasUsageAccess = false,
            hasOverlayAccess = false,
            hasNotificationAccess = false,
            foregroundAppState = ForegroundAppState.PermissionMissing,
            currentTimeMillis = System.currentTimeMillis(),
            alertState = AlertState(),
            settings = FocusGuardSettings(),
            interventionSettings = InterventionSettings(),
            debugSettings = DebugSettings(),
            effectiveSettings = FocusGuardSettings(),
            hasPendingSettings = false,
            watcherState = WatcherState(),
            selectedTrackedPackages = emptySet(),
            packageName = "com.vmdex.focusguard",
            onRefreshUsageData = {},
            onOpenNotificationSettings = {},
            onOpenOverlaySettings = {},
            onResetSession = {},
            onStartMonitoring = {},
            onStopMonitoring = {},
            onChooseApps = {},
            onConfigureInterventions = {},
            onDebugSettingsChanged = {},
            onSettingsChanged = {}
        )
    }
}
