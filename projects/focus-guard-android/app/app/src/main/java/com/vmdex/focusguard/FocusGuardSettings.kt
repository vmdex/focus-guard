package com.vmdex.focusguard

const val UsageLookupWindowMillis = 10 * 60 * 1000L
const val DefaultGracePeriodMillis = 15 * 1000L
const val DefaultSessionLimitMillis = 30 * 1000L
const val DefaultAlertDelayAfterResumeMillis = 3 * 1000L

const val LimitNotificationChannelId = "focus_guard_heads_up_alerts_v2"
const val LimitNotificationId = 1001
const val BootResumeNotificationChannelId = "focus_guard_boot_resume"
const val BootResumeNotificationId = 1002
const val NotificationPermissionRequestCode = 2001
const val WatcherNotificationChannelId = "focus_guard_monitoring"
const val WatcherNotificationId = 2001
const val WatcherTickMillis = 1000L
const val WatcherGraceTickMillis = 3000L
const val WatcherIdleTickMillis = 5000L
const val WatcherScreenLockedTickMillis = 30 * 1000L

data class FocusGuardSettings(
    val gracePeriodMillis: Long = DefaultGracePeriodMillis,
    val sessionLimitMillis: Long = DefaultSessionLimitMillis,
    val alertDelayAfterResumeMillis: Long = DefaultAlertDelayAfterResumeMillis,
    val isSessionTimerEnabled: Boolean = false
) {
    val gracePeriodSeconds: Int = (gracePeriodMillis / 1000).toInt()
    val sessionLimitSeconds: Int = (sessionLimitMillis / 1000).toInt()
    val alertDelayAfterResumeSeconds: Int = (alertDelayAfterResumeMillis / 1000).toInt()
}

fun FocusGuardSettings.hasSessionRuleChanges(other: FocusGuardSettings): Boolean {
    return gracePeriodMillis != other.gracePeriodMillis ||
        sessionLimitMillis != other.sessionLimitMillis ||
        alertDelayAfterResumeMillis != other.alertDelayAfterResumeMillis
}
