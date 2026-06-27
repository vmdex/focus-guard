package com.vmdex.focusguard

data class PersistedSessionState(
    val packageName: String,
    val sessionStartedAtMillis: Long,
    val sessionElapsedMillis: Long = 0L,
    val currentActiveStartedAtMillis: Long? = null,
    val interruptionStartedAtMillis: Long? = null,
    val status: SessionStatus = SessionStatus.Active,
    val effectiveSettings: FocusGuardSettings = FocusGuardSettings(),
    val alertedSessionKey: String? = null,
    val lastUpdatedTimeMillis: Long = sessionStartedAtMillis,
    val className: String? = null,
    val eventType: Int = 0,
    val lastForegroundPackageName: String = packageName
) {
    val sessionKey: String = "$packageName:$sessionStartedAtMillis"
    val currentActiveElapsedMillis: Long
        get() = if (status == SessionStatus.Active && currentActiveStartedAtMillis != null) {
            (lastUpdatedTimeMillis - currentActiveStartedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
}
