package com.vmdex.focusguard

data class WatcherState(
    val isRunning: Boolean = false,
    val lastTickTimeMillis: Long? = null,
    val foregroundAppState: ForegroundAppState = ForegroundAppState.Unknown,
    val alertState: AlertState = AlertState(),
    val effectiveSettings: FocusGuardSettings = FocusGuardSettings()
)
