package com.vmdex.focusguard

data class WatcherState(
    val isRunning: Boolean = false,
    val lastTickTimeMillis: Long? = null,
    val foregroundAppState: ForegroundAppState = ForegroundAppState.Unknown,
    val usageDebugState: UsageDebugState = UsageDebugState(),
    val deviceInteractionState: DeviceInteractionState = DeviceInteractionState(),
    val alertState: AlertState = AlertState(),
    val interventionState: InterventionState = InterventionState(),
    val effectiveSettings: FocusGuardSettings = FocusGuardSettings(),
    val sessionResetTimeMillis: Long? = null
)

data class DeviceInteractionState(
    val isInteractive: Boolean = true,
    val isKeyguardLocked: Boolean = false,
    val lastScreenEvent: String? = null,
    val lastScreenEventTimeMillis: Long? = null
) {
    val isScreenLocked: Boolean
        get() = !isInteractive || isKeyguardLocked
}

data class UsageDebugState(
    val queryStartTimeMillis: Long? = null,
    val queryEndTimeMillis: Long? = null,
    val sinceTimeMillis: Long? = null,
    val resolvedForegroundPackageName: String? = null,
    val lastForegroundStartPackageName: String? = null,
    val lastForegroundStartEventType: Int = 0,
    val lastForegroundStartTimeMillis: Long? = null,
    val transitionCount: Int = 0,
    val recentRawEvents: List<UsageRawEventDebugEntry> = emptyList()
)

data class UsageRawEventDebugEntry(
    val packageName: String,
    val className: String?,
    val eventType: Int,
    val timestampMillis: Long
)
