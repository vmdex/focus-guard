package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class FocusGuardSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(SettingsStoreName, Context.MODE_PRIVATE)

    fun load(): FocusGuardSettings {
        return FocusGuardSettings(
            gracePeriodMillis = readSeconds(GracePeriodSecondsKey, DefaultGracePeriodMillis),
            sessionLimitMillis = readSeconds(SessionLimitSecondsKey, DefaultSessionLimitMillis),
            alertDelayAfterResumeMillis = readSeconds(
                AlertDelayAfterResumeSecondsKey,
                DefaultAlertDelayAfterResumeMillis
            ),
            isSessionTimerEnabled = preferences.getBoolean(
                SessionTimerEnabledKey,
                false
            )
        )
    }

    fun save(settings: FocusGuardSettings) {
        preferences.edit {
            putInt(GracePeriodSecondsKey, settings.gracePeriodSeconds)
            putInt(SessionLimitSecondsKey, settings.sessionLimitSeconds)
            putInt(AlertDelayAfterResumeSecondsKey, settings.alertDelayAfterResumeSeconds)
            putBoolean(SessionTimerEnabledKey, settings.isSessionTimerEnabled)
        }
    }

    private fun readSeconds(key: String, fallbackMillis: Long): Long {
        val fallbackSeconds = (fallbackMillis / 1000).toInt()
        val seconds = preferences.getInt(key, fallbackSeconds).coerceAtLeast(1)

        return seconds * 1000L
    }
}

private const val SettingsStoreName = "focus_guard_settings"
private const val GracePeriodSecondsKey = "grace_period_seconds"
private const val SessionLimitSecondsKey = "session_limit_seconds"
private const val AlertDelayAfterResumeSecondsKey = "alert_delay_after_resume_seconds"
private const val SessionTimerEnabledKey = "session_timer_enabled"
