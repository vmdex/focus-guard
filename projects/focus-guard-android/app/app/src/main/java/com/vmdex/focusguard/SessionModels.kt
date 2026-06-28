package com.vmdex.focusguard

data class AlertState(
    val wasSent: Boolean = false,
    val lastAlertTimeMillis: Long? = null,
    val lastAlertPackageName: String? = null,
    val alertedSessionKey: String? = null
)

data class InterventionState(
    val notificationStatus: InterventionNotificationStatus = InterventionNotificationStatus.NotNeeded,
    val notificationLeftMillis: Long? = null,
    val sessionKey: String? = null
)

enum class InterventionNotificationStatus {
    NotNeeded,
    WaitingLimit,
    WaitingResumeDelay,
    ReadyToNotify,
    Sent
}

sealed interface ForegroundAppState {
    data object Unknown : ForegroundAppState
    data object PermissionMissing : ForegroundAppState
    data class Untracked(val packageName: String) : ForegroundAppState

    data class Detected(
        val packageName: String,
        val className: String?,
        val eventType: Int,
        val timestampMillis: Long,
        val isTracked: Boolean,
        val sessionStatus: SessionStatus,
        val lastForegroundPackageName: String,
        val interruptionStartedAtMillis: Long?,
        val sessionElapsedMillis: Long,
        val appElapsedMillis: Map<String, Long> = emptyMap(),
        val currentActiveElapsedMillis: Long,
        val isAlertSentForSession: Boolean = false
    ) : ForegroundAppState {
        val sessionKey: String = "tracked:$timestampMillis"
    }
}

enum class SessionStatus {
    Active,
    GracePeriod,
    Ended
}

fun isSessionInProgress(foregroundAppState: ForegroundAppState): Boolean {
    val detected = foregroundAppState as? ForegroundAppState.Detected ?: return false

    return detected.sessionStatus == SessionStatus.Active ||
        detected.sessionStatus == SessionStatus.GracePeriod
}

fun calculateSessionElapsedMillis(
    foregroundAppState: ForegroundAppState.Detected,
    currentTimeMillis: Long
): Long {
    return foregroundAppState.sessionElapsedMillis
}

fun interventionStateForSession(session: PersistedSessionState?): InterventionState {
    if (session == null || session.status != SessionStatus.Active) {
        return InterventionState()
    }

    if (session.alertedSessionKey == session.sessionKey) {
        return InterventionState(
            notificationStatus = InterventionNotificationStatus.Sent,
            sessionKey = session.sessionKey
        )
    }

    val limitLeftMillis = (session.effectiveSettings.sessionLimitMillis - session.sessionElapsedMillis)
        .coerceAtLeast(0L)
    val resumeDelayLeftMillis =
        (session.effectiveSettings.alertDelayAfterResumeMillis - session.currentActiveElapsedMillis)
            .coerceAtLeast(0L)
    val notificationLeftMillis = maxOf(limitLeftMillis, resumeDelayLeftMillis)
    val status = when {
        resumeDelayLeftMillis > limitLeftMillis -> InterventionNotificationStatus.WaitingResumeDelay
        limitLeftMillis > 0L -> InterventionNotificationStatus.WaitingLimit
        else -> InterventionNotificationStatus.ReadyToNotify
    }

    return InterventionState(
        notificationStatus = status,
        notificationLeftMillis = notificationLeftMillis,
        sessionKey = session.sessionKey
    )
}
