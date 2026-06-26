package com.vmdex.focusguard

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.vmdex.focusguard.ui.theme.FocusGuardAndroidTheme
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class MainActivity : ComponentActivity() {
    private var hasUsageAccess by mutableStateOf(false)
    private var foregroundAppState by mutableStateOf<ForegroundAppState>(ForegroundAppState.Unknown)
    private var currentTimeMillis by mutableStateOf(System.currentTimeMillis())
    private var alertState by mutableStateOf(AlertState())
    private var alertedSessionKey: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        createNotificationChannel()
        requestNotificationPermissionIfNeeded()
        refreshUsageData()

        setContent {
            FocusGuardAndroidTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    UsageAccessScreen(
                        hasUsageAccess = hasUsageAccess,
                        foregroundAppState = foregroundAppState,
                        currentTimeMillis = currentTimeMillis,
                        alertState = alertState,
                        packageName = packageName,
                        onRefreshUsageData = ::refreshUsageData,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshUsageData()
    }

    private fun refreshUsageData() {
        currentTimeMillis = System.currentTimeMillis()
        hasUsageAccess = hasUsageAccessPermission(this)
        foregroundAppState = if (hasUsageAccess) {
            readLatestForegroundApp(this)
        } else {
            ForegroundAppState.PermissionMissing
        }
        maybeShowLimitExceededNotification()
    }

    private fun maybeShowLimitExceededNotification() {
        val detected = foregroundAppState as? ForegroundAppState.Detected ?: return
        if (detected.sessionStatus == SessionStatus.Ended) {
            return
        }

        val elapsedMillis = calculateSessionElapsedMillis(detected, currentTimeMillis)
        if (elapsedMillis < DefaultSessionLimitMillis) {
            return
        }

        val sessionKey = "${detected.packageName}:${detected.timestampMillis}"
        if (alertedSessionKey == sessionKey) {
            return
        }

        showLimitExceededNotification(detected.packageName, elapsedMillis)
        alertedSessionKey = sessionKey
        alertState = AlertState(
            wasSent = true,
            lastAlertTimeMillis = currentTimeMillis,
            lastAlertPackageName = detected.packageName
        )
    }

    private fun showLimitExceededNotification(packageName: String, elapsedMillis: Long) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            alertState = alertState.copy(wasSent = false)
            return
        }

        val notification = NotificationCompat.Builder(this, LimitNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Focus Guard")
            .setContentText("$packageName has been open for ${formatElapsed(elapsedMillis)}")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        try {
            NotificationManagerCompat.from(this).notify(LimitNotificationId, notification)
        } catch (_: SecurityException) {
            alertState = alertState.copy(wasSent = false)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            LimitNotificationChannelId,
            "Focus Guard alerts",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a tracked app session exceeds its limit."
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return
        }

        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), NotificationPermissionRequestCode)
    }
}

private data class AlertState(
    val wasSent: Boolean = false,
    val lastAlertTimeMillis: Long? = null,
    val lastAlertPackageName: String? = null
)

private sealed interface ForegroundAppState {
    data object Unknown : ForegroundAppState
    data object PermissionMissing : ForegroundAppState
    data class Detected(
        val packageName: String,
        val className: String?,
        val eventType: Int,
        val timestampMillis: Long,
        val isTracked: Boolean,
        val sessionStatus: SessionStatus,
        val lastForegroundPackageName: String,
        val interruptionStartedAtMillis: Long?
    ) : ForegroundAppState
}

private enum class SessionStatus {
    Active,
    GracePeriod,
    Ended
}

private fun hasUsageAccessPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )

    return mode == AppOpsManager.MODE_ALLOWED
}

private fun readLatestForegroundApp(context: Context): ForegroundAppState {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val start = now - UsageLookupWindowMillis
    val events = usageStatsManager.queryEvents(start, now)
    val event = UsageEvents.Event()
    var latestTrackedApp: TrackedForegroundEvent? = null
    var lastForegroundPackageName: String? = null
    var interruptionStartedAtMillis: Long? = null

    while (events.hasNextEvent()) {
        events.getNextEvent(event)

        if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) {
            continue
        }

        val foregroundPackageName = event.packageName ?: continue
        lastForegroundPackageName = foregroundPackageName

        if (foregroundPackageName != context.packageName &&
            foregroundPackageName in TrackedAppPackages
        ) {
            latestTrackedApp = TrackedForegroundEvent(
                packageName = foregroundPackageName,
                className = event.className,
                eventType = event.eventType,
                timestampMillis = event.timeStamp
            )
            interruptionStartedAtMillis = null
            continue
        }

        if (latestTrackedApp != null && interruptionStartedAtMillis == null) {
            interruptionStartedAtMillis = event.timeStamp
        }
    }

    val trackedApp = latestTrackedApp ?: return ForegroundAppState.Unknown
    val sessionStatus = when {
        lastForegroundPackageName == trackedApp.packageName -> SessionStatus.Active
        interruptionStartedAtMillis == null -> SessionStatus.Active
        now - interruptionStartedAtMillis <= DefaultGracePeriodMillis -> SessionStatus.GracePeriod
        else -> SessionStatus.Ended
    }

    return ForegroundAppState.Detected(
        packageName = trackedApp.packageName,
        className = trackedApp.className,
        eventType = trackedApp.eventType,
        timestampMillis = trackedApp.timestampMillis,
        isTracked = true,
        sessionStatus = sessionStatus,
        lastForegroundPackageName = lastForegroundPackageName ?: "-",
        interruptionStartedAtMillis = interruptionStartedAtMillis
    )
}

private data class TrackedForegroundEvent(
    val packageName: String,
    val className: String?,
    val eventType: Int,
    val timestampMillis: Long
)

@Composable
private fun UsageAccessScreen(
    hasUsageAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    packageName: String,
    onRefreshUsageData: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000)
            onRefreshUsageData()
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

            DevInfoCard(
                packageName = packageName,
                hasUsageAccess = hasUsageAccess,
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis,
                alertState = alertState,
                onRefreshUsageData = onRefreshUsageData
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
                Text(
                    text = "Permission",
                    style = MaterialTheme.typography.bodyLarge
                )
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
private fun DevInfoCard(
    packageName: String,
    hasUsageAccess: Boolean,
    foregroundAppState: ForegroundAppState,
    currentTimeMillis: Long,
    alertState: AlertState,
    onRefreshUsageData: () -> Unit
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
            DevInfoRow(label = "Grace period", value = formatElapsed(DefaultGracePeriodMillis))
            DevInfoRow(label = "Session limit", value = formatElapsed(DefaultSessionLimitMillis))
            Text(
                text = TrackedAppPackages.joinToString(separator = "\n"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ForegroundAppRows(foregroundAppState)
            CurrentSessionRows(
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis
            )
            AlertRows(alertState)

            Button(
                onClick = onRefreshUsageData,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Refresh usage data")
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = "Only tracked apps are detected here. Launcher, recents, system screens, and other untracked apps are ignored for now.",
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
    currentTimeMillis: Long
) {
    when (foregroundAppState) {
        is ForegroundAppState.Detected -> {
            val elapsedMillis = calculateSessionElapsedMillis(
                foregroundAppState = foregroundAppState,
                currentTimeMillis = currentTimeMillis
            )
            val isLimitExceeded = elapsedMillis >= DefaultSessionLimitMillis

            DevInfoRow(label = "Session app", value = foregroundAppState.packageName)
            DevInfoRow(label = "Session started", value = formatTimestamp(foregroundAppState.timestampMillis))
            DevInfoRow(label = "Session elapsed", value = formatElapsed(elapsedMillis))
            DevInfoRow(label = "Limit status", value = if (isLimitExceeded) "Exceeded" else "Within limit")

            if (foregroundAppState.sessionStatus == SessionStatus.GracePeriod &&
                foregroundAppState.interruptionStartedAtMillis != null
            ) {
                val graceElapsed = currentTimeMillis - foregroundAppState.interruptionStartedAtMillis
                val graceRemaining = DefaultGracePeriodMillis - graceElapsed

                DevInfoRow(label = "Grace remaining", value = formatElapsed(graceRemaining))
            }
        }

        else -> {
            DevInfoRow(label = "Session app", value = "-")
            DevInfoRow(label = "Session elapsed", value = "00:00")
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

private fun calculateSessionElapsedMillis(
    foregroundAppState: ForegroundAppState.Detected,
    currentTimeMillis: Long
): Long {
    return when {
        foregroundAppState.sessionStatus == SessionStatus.Ended &&
            foregroundAppState.interruptionStartedAtMillis != null ->
            foregroundAppState.interruptionStartedAtMillis - foregroundAppState.timestampMillis

        else -> currentTimeMillis - foregroundAppState.timestampMillis
    }
}

private fun formatTimestamp(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(TimeFormatter)
}

private fun formatElapsed(elapsedMillis: Long): String {
    val totalSeconds = (elapsedMillis / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60

    return "%02d:%02d".format(minutes, seconds)
}

@Preview(showBackground = true)
@Composable
private fun UsageAccessScreenPreview() {
    FocusGuardAndroidTheme {
        UsageAccessScreen(
            hasUsageAccess = false,
            foregroundAppState = ForegroundAppState.PermissionMissing,
            currentTimeMillis = System.currentTimeMillis(),
            alertState = AlertState(),
            packageName = "com.vmdex.focusguard",
            onRefreshUsageData = {}
        )
    }
}

private const val UsageLookupWindowMillis = 10 * 60 * 1000L
private const val DefaultGracePeriodMillis = 15 * 1000L
private const val DefaultSessionLimitMillis = 30 * 1000L
private const val LimitNotificationChannelId = "focus_guard_limit_alerts"
private const val LimitNotificationId = 1001
private const val NotificationPermissionRequestCode = 2001

private val TrackedAppPackages = setOf(
    "com.google.android.youtube",
    "com.android.chrome",
    "com.chrome.beta",
    "tv.twitch.android.app"
)

private val TimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
