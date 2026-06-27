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
    var lastForegroundPackageName: String? = null
    var session: TrackedSessionAccumulator? = null

    // Walk chronologically and accumulate only time spent with the tracked app in foreground.
    while (events.hasNextEvent()) {
        events.getNextEvent(event)

        if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) {
            continue
        }

        val foregroundPackageName = event.packageName ?: continue
        lastForegroundPackageName = foregroundPackageName
        val isTrackedApp = foregroundPackageName != context.packageName &&
            foregroundPackageName in TrackedAppPackages

        if (isTrackedApp) {
            val currentSession = session
            val shouldStartNewSession = currentSession == null ||
                currentSession.packageName != foregroundPackageName ||
                currentSession.isGraceExpired(event.timeStamp, gracePeriodMillis)

            session = if (shouldStartNewSession) {
                TrackedSessionAccumulator(
                    packageName = foregroundPackageName,
                    className = event.className,
                    eventType = event.eventType,
                    sessionStartedAtMillis = event.timeStamp,
                    currentActiveStartedAtMillis = event.timeStamp
                )
            } else {
                currentSession.copy(
                    className = event.className,
                    eventType = event.eventType,
                    currentActiveStartedAtMillis = currentSession.currentActiveStartedAtMillis ?: event.timeStamp,
                    interruptionStartedAtMillis = null
                )
            }
            continue
        }

        val currentSession = session
        if (currentSession != null && currentSession.currentActiveStartedAtMillis != null) {
            session = currentSession.copy(
                accumulatedSessionElapsedMillis = currentSession.accumulatedSessionElapsedMillis +
                    (event.timeStamp - currentSession.currentActiveStartedAtMillis).coerceAtLeast(0L),
                currentActiveStartedAtMillis = null,
                interruptionStartedAtMillis = event.timeStamp
            )
        }
    }

    val trackedSession = session ?: return ForegroundAppState.Unknown
    val activeStartedAtMillis = trackedSession.currentActiveStartedAtMillis
    // Grace period keeps short launcher/recents/app-switch interruptions inside one session.
    val sessionStatus = when {
        lastForegroundPackageName == trackedSession.packageName && activeStartedAtMillis != null -> SessionStatus.Active
        trackedSession.interruptionStartedAtMillis == null -> SessionStatus.Active
        now - trackedSession.interruptionStartedAtMillis <= gracePeriodMillis -> SessionStatus.GracePeriod
        else -> SessionStatus.Ended
    }
    val currentActiveElapsedMillis = if (sessionStatus == SessionStatus.Active && activeStartedAtMillis != null) {
        (now - activeStartedAtMillis).coerceAtLeast(0L)
    } else {
        0L
    }
    val sessionElapsedMillis = trackedSession.accumulatedSessionElapsedMillis + currentActiveElapsedMillis

    return ForegroundAppState.Detected(
        packageName = trackedSession.packageName,
        className = trackedSession.className,
        eventType = trackedSession.eventType,
        timestampMillis = trackedSession.sessionStartedAtMillis,
        isTracked = true,
        sessionStatus = sessionStatus,
        lastForegroundPackageName = lastForegroundPackageName ?: "-",
        interruptionStartedAtMillis = trackedSession.interruptionStartedAtMillis,
        sessionElapsedMillis = sessionElapsedMillis,
        currentActiveElapsedMillis = currentActiveElapsedMillis
    )
}

private data class TrackedSessionAccumulator(
    val packageName: String,
    val className: String?,
    val eventType: Int,
    val sessionStartedAtMillis: Long,
    val accumulatedSessionElapsedMillis: Long = 0L,
    val currentActiveStartedAtMillis: Long? = null,
    val interruptionStartedAtMillis: Long? = null
) {
    fun isGraceExpired(currentTimeMillis: Long, gracePeriodMillis: Long): Boolean {
        val interruptionStartedAt = interruptionStartedAtMillis ?: return false

        return currentTimeMillis - interruptionStartedAt > gracePeriodMillis
    }
}
