package com.vmdex.focusguard

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

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
    var lastForegroundPackageName: String? = null

    while (events.hasNextEvent()) {
        events.getNextEvent(event)

        if (event.eventType != UsageEvents.Event.MOVE_TO_FOREGROUND) {
            continue
        }

        val packageName = event.packageName ?: continue
        lastForegroundPackageName = packageName

        if (sinceTimeMillis == null || event.timeStamp > sinceTimeMillis) {
            transitions += ForegroundTransition(
                packageName = packageName,
                className = event.className,
                eventType = event.eventType,
                timestampMillis = event.timeStamp
            )
        }
    }

    return ForegroundUsageSnapshot(
        lastForegroundPackageName = lastForegroundPackageName,
        transitions = transitions
    )
}

data class ForegroundUsageSnapshot(
    val lastForegroundPackageName: String?,
    val transitions: List<ForegroundTransition>
)

data class ForegroundTransition(
    val packageName: String,
    val className: String?,
    val eventType: Int,
    val timestampMillis: Long
)
