package com.vmdex.focusguard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

fun readLatestForegroundApp(
    context: Context,
    gracePeriodMillis: Long
): ForegroundAppState {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val start = now - UsageLookupWindowMillis
    val events = usageStatsManager.queryEvents(start, now)
    val event = UsageEvents.Event()
    var latestTrackedApp: TrackedForegroundEvent? = null
    var lastForegroundPackageName: String? = null
    var interruptionStartedAtMillis: Long? = null

    // Walk the window chronologically and keep the latest tracked app as the session candidate.
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

        // The first non-tracked foreground transition after a tracked app starts grace period.
        if (latestTrackedApp != null && interruptionStartedAtMillis == null) {
            interruptionStartedAtMillis = event.timeStamp
        }
    }

    val trackedApp = latestTrackedApp ?: return ForegroundAppState.Unknown
    // Grace period keeps short launcher/recents/app-switch interruptions inside one session.
    val sessionStatus = when {
        lastForegroundPackageName == trackedApp.packageName -> SessionStatus.Active
        interruptionStartedAtMillis == null -> SessionStatus.Active
        now - interruptionStartedAtMillis <= gracePeriodMillis -> SessionStatus.GracePeriod
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
