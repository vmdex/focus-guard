package com.vmdex.focusguard

const val UsageLookupWindowMillis = 10 * 60 * 1000L
const val DefaultGracePeriodMillis = 15 * 1000L
const val DefaultSessionLimitMillis = 30 * 1000L

const val LimitNotificationChannelId = "focus_guard_limit_alerts"
const val LimitNotificationId = 1001
const val NotificationPermissionRequestCode = 2001

val TrackedAppPackages = setOf(
    "com.google.android.youtube",
    "com.android.chrome",
    "com.chrome.beta",
    "tv.twitch.android.app"
)

data class FocusGuardSettings(
    val gracePeriodMillis: Long = DefaultGracePeriodMillis,
    val sessionLimitMillis: Long = DefaultSessionLimitMillis
) {
    val gracePeriodSeconds: Int = (gracePeriodMillis / 1000).toInt()
    val sessionLimitSeconds: Int = (sessionLimitMillis / 1000).toInt()
}
