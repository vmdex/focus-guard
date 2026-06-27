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
        val interruptionStartedAtMillis: Long?,
        val sessionElapsedMillis: Long,
        val currentActiveElapsedMillis: Long
    ) : ForegroundAppState {
        val sessionKey: String = "$packageName:$timestampMillis"
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
