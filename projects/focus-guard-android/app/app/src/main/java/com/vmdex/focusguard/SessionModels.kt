package com.vmdex.focusguard

data class AlertState(
    val wasSent: Boolean = false,
    val lastAlertTimeMillis: Long? = null,
    val lastAlertPackageName: String? = null
)

sealed interface ForegroundAppState {
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
    ) : ForegroundAppState {
        val sessionKey: String = "$packageName:$timestampMillis"
    }
}

enum class SessionStatus {
    Active,
    GracePeriod,
    Ended
}

fun calculateSessionElapsedMillis(
    foregroundAppState: ForegroundAppState.Detected,
    currentTimeMillis: Long
): Long {
    // Once grace period expires, the session should stop growing.
    return when {
        foregroundAppState.sessionStatus == SessionStatus.Ended &&
            foregroundAppState.interruptionStartedAtMillis != null ->
            foregroundAppState.interruptionStartedAtMillis - foregroundAppState.timestampMillis

        else -> currentTimeMillis - foregroundAppState.timestampMillis
    }
}
