package com.vmdex.focusguard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build

fun readForegroundUsageSnapshot(
    context: Context,
    sinceTimeMillis: Long?
): ForegroundUsageSnapshot {
    val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    val now = System.currentTimeMillis()
    val start = now - UsageLookupWindowMillis
    val events = usageStatsManager.queryEvents(start, now)
    val event = UsageEvents.Event()
    val transitions = mutableListOf<ForegroundTransition>()
    val rawEvents = ArrayDeque<UsageRawEventDebugEntry>()
    val activityStates = mutableMapOf<ActivityKey, ActivityUsageState>()
    var lastForegroundStartEventType: Int = 0
    var lastForegroundStartTimeMillis: Long? = null
    var lastForegroundStartPackageName: String? = null

    while (events.hasNextEvent()) {
        events.getNextEvent(event)

        val rawPackageName = event.packageName
        if (rawPackageName != null) {
            val rawEvent = UsageRawEventDebugEntry(
                packageName = rawPackageName,
                className = event.className,
                eventType = event.eventType,
                timestampMillis = event.timeStamp
            )
            updateActivityState(activityStates, rawEvent)

            rawEvents += UsageRawEventDebugEntry(
                packageName = rawPackageName,
                className = event.className,
                eventType = event.eventType,
                timestampMillis = event.timeStamp
            )

            if (rawEvents.size > RecentRawUsageEventsLimit) {
                rawEvents.removeFirst()
            }
        }

        if (!event.eventType.isForegroundStartEventType()) {
            continue
        }

        val packageName = event.packageName ?: continue
        lastForegroundStartPackageName = packageName
        lastForegroundStartEventType = event.eventType
        lastForegroundStartTimeMillis = event.timeStamp

        if (sinceTimeMillis == null || event.timeStamp > sinceTimeMillis) {
            transitions += ForegroundTransition(
                packageName = packageName,
                className = event.className,
                eventType = event.eventType,
                timestampMillis = event.timeStamp
            )
        }
    }
    val currentForegroundPackageName = currentForegroundPackageName(activityStates)

    return ForegroundUsageSnapshot(
        lastForegroundPackageName = currentForegroundPackageName,
        transitions = transitions,
        debugState = UsageDebugState(
            queryStartTimeMillis = start,
            queryEndTimeMillis = now,
            sinceTimeMillis = sinceTimeMillis,
            resolvedForegroundPackageName = currentForegroundPackageName,
            lastForegroundStartPackageName = lastForegroundStartPackageName,
            lastForegroundStartEventType = lastForegroundStartEventType,
            lastForegroundStartTimeMillis = lastForegroundStartTimeMillis,
            transitionCount = transitions.size,
            recentRawEvents = rawEvents.toList()
        )
    )
}

data class ForegroundUsageSnapshot(
    val lastForegroundPackageName: String?,
    val transitions: List<ForegroundTransition>,
    val debugState: UsageDebugState = UsageDebugState()
)

data class ForegroundTransition(
    val packageName: String,
    val className: String?,
    val eventType: Int,
    val timestampMillis: Long
)

private fun updateActivityState(
    activityStates: MutableMap<ActivityKey, ActivityUsageState>,
    event: UsageRawEventDebugEntry
) {
    if (!event.eventType.isActivityLifecycleEventType()) {
        return
    }

    activityStates[ActivityKey(event.packageName, event.className)] = ActivityUsageState(
        packageName = event.packageName,
        eventType = event.eventType,
        timestampMillis = event.timestampMillis
    )
}

private fun currentForegroundPackageName(activityStates: Map<ActivityKey, ActivityUsageState>): String? {
    return activityStates.values
        .filter { state -> state.eventType.isForegroundStartEventType() }
        .maxByOrNull { state -> state.timestampMillis }
        ?.packageName
}

fun Int.isForegroundStartEventType(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this == UsageEvents.Event.ACTIVITY_RESUMED
    } else {
        isLegacyForegroundStartEventType()
    }
}

private fun Int.isActivityLifecycleEventType(): Boolean {
    return isForegroundStartEventType() || isForegroundEndEventType()
}

private fun Int.isForegroundEndEventType(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        this == UsageEvents.Event.ACTIVITY_PAUSED ||
            this == UsageEvents.Event.ACTIVITY_STOPPED ||
            this == ActivityDestroyedEventType
    } else {
        isLegacyForegroundEndEventType()
    }
}

fun usageEventTypeLabel(eventType: Int): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return when (eventType) {
            UsageEvents.Event.ACTIVITY_RESUMED -> "ACTIVITY_RESUMED"
            UsageEvents.Event.ACTIVITY_PAUSED -> "ACTIVITY_PAUSED"
            UsageEvents.Event.ACTIVITY_STOPPED -> "ACTIVITY_STOPPED"
            ActivityDestroyedEventType -> "ACTIVITY_DESTROYED"
            UsageEvents.Event.USER_INTERACTION -> "USER_INTERACTION"
            UsageEvents.Event.STANDBY_BUCKET_CHANGED -> "STANDBY_BUCKET_CHANGED"
            NotificationInterruptionEventType -> "NOTIFICATION_INTERRUPTION"
            UsageEvents.Event.FOREGROUND_SERVICE_START -> "FOREGROUND_SERVICE_START"
            UsageEvents.Event.FOREGROUND_SERVICE_STOP -> "FOREGROUND_SERVICE_STOP"
            else -> eventType.toString()
        }
    }

    return when (eventType) {
        legacyForegroundStartEventType() -> "MOVE_TO_FOREGROUND"
        legacyForegroundEndEventType() -> "MOVE_TO_BACKGROUND"
        else -> eventType.toString()
    }
}

@Suppress("DEPRECATION")
private fun Int.isLegacyForegroundStartEventType(): Boolean {
    return this == UsageEvents.Event.MOVE_TO_FOREGROUND
}

@Suppress("DEPRECATION")
private fun legacyForegroundStartEventType(): Int {
    return UsageEvents.Event.MOVE_TO_FOREGROUND
}

@Suppress("DEPRECATION")
private fun Int.isLegacyForegroundEndEventType(): Boolean {
    return this == UsageEvents.Event.MOVE_TO_BACKGROUND
}

@Suppress("DEPRECATION")
private fun legacyForegroundEndEventType(): Int {
    return UsageEvents.Event.MOVE_TO_BACKGROUND
}

private data class ActivityKey(
    val packageName: String,
    val className: String?
)

private data class ActivityUsageState(
    val packageName: String,
    val eventType: Int,
    val timestampMillis: Long
)

private const val RecentRawUsageEventsLimit = 10
private const val NotificationInterruptionEventType = 12
private const val ActivityDestroyedEventType = 24
