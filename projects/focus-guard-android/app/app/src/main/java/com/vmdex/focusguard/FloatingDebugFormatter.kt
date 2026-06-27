package com.vmdex.focusguard

fun floatingDebugTextForState(state: WatcherState): String {
    return when (val foregroundState = state.foregroundAppState) {
        ForegroundAppState.PermissionMissing -> "Permission missing"
        ForegroundAppState.Unknown -> "FG monitoring"
        is ForegroundAppState.Untracked -> "Not tracking: ${shortPackageName(foregroundState.packageName)}"
        is ForegroundAppState.Detected -> floatingTrackedDebugText(
            foregroundState = foregroundState,
            settings = state.effectiveSettings,
            interventionState = state.interventionState
        )
    }
}

private fun floatingTrackedDebugText(
    foregroundState: ForegroundAppState.Detected,
    settings: FocusGuardSettings,
    interventionState: InterventionState
): String {
    val currentForegroundPackageName = foregroundState.lastForegroundPackageName
    if (foregroundState.sessionStatus != SessionStatus.Active ||
        currentForegroundPackageName != foregroundState.packageName
    ) {
        return "Not tracking: ${shortPackageName(currentForegroundPackageName)}"
    }

    val limitLeftMillis = (settings.sessionLimitMillis - foregroundState.sessionElapsedMillis).coerceAtLeast(0L)
    val notificationText = floatingNotificationText(interventionState)

    return listOf(
        "Tracking: ${shortPackageName(foregroundState.packageName)}",
        "Session: ${formatElapsed(foregroundState.sessionElapsedMillis)}",
        "Limit left: ${formatElapsed(limitLeftMillis)}",
        notificationText
    ).joinToString(separator = "\n")
}

private fun floatingNotificationText(interventionState: InterventionState): String {
    return when (interventionState.notificationStatus) {
        InterventionNotificationStatus.Sent -> "Notification sent"
        InterventionNotificationStatus.WaitingResumeDelay -> {
            "Notification left: ${formatElapsed(interventionState.notificationLeftMillis ?: 0L)} (resume delay)"
        }
        InterventionNotificationStatus.WaitingLimit,
        InterventionNotificationStatus.ReadyToNotify -> {
            "Notification left: ${formatElapsed(interventionState.notificationLeftMillis ?: 0L)}"
        }
        InterventionNotificationStatus.NotNeeded -> "Notification left: -"
    }
}

private fun shortPackageName(packageName: String): String {
    return packageName.substringAfterLast('.').ifBlank { packageName }
}
