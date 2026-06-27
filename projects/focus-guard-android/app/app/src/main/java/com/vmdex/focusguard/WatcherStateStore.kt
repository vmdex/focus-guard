package com.vmdex.focusguard

import android.content.Context

class WatcherStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(WatcherStateStoreName, Context.MODE_PRIVATE)

    fun load(): WatcherState {
        val lastTick = preferences.getLong(LastTickTimeMillisKey, 0L)

        return WatcherState(
            isRunning = preferences.getBoolean(IsRunningKey, false),
            lastTickTimeMillis = lastTick.takeIf { it > 0L },
            foregroundAppState = readForegroundAppState(),
            alertState = readAlertState(),
            effectiveSettings = readEffectiveSettings(),
            sessionResetTimeMillis = preferences
                .getLong(SessionResetTimeMillisKey, 0L)
                .takeIf { it > 0L }
        )
    }

    fun save(state: WatcherState) {
        val editor = preferences.edit()
            .putBoolean(IsRunningKey, state.isRunning)
            .putLong(LastTickTimeMillisKey, state.lastTickTimeMillis ?: 0L)
            .putLong(SessionResetTimeMillisKey, state.sessionResetTimeMillis ?: 0L)
            .putInt(EffectiveGracePeriodSecondsKey, state.effectiveSettings.gracePeriodSeconds)
            .putInt(EffectiveSessionLimitSecondsKey, state.effectiveSettings.sessionLimitSeconds)
            .putInt(EffectiveAlertDelayAfterResumeSecondsKey, state.effectiveSettings.alertDelayAfterResumeSeconds)
            .putBoolean(AlertWasSentKey, state.alertState.wasSent)
            .putLong(AlertLastTimeMillisKey, state.alertState.lastAlertTimeMillis ?: 0L)
            .putString(AlertLastPackageNameKey, state.alertState.lastAlertPackageName)

        when (val foregroundState = state.foregroundAppState) {
            ForegroundAppState.PermissionMissing -> {
                editor.putString(ForegroundStateKindKey, ForegroundStateKindPermissionMissing)
            }

            ForegroundAppState.Unknown -> {
                editor.putString(ForegroundStateKindKey, ForegroundStateKindUnknown)
            }

            is ForegroundAppState.Untracked -> {
                editor
                    .putString(ForegroundStateKindKey, ForegroundStateKindUntracked)
                    .putString(ForegroundPackageNameKey, foregroundState.packageName)
            }

            is ForegroundAppState.Detected -> {
                editor
                    .putString(ForegroundStateKindKey, ForegroundStateKindDetected)
                    .putString(ForegroundPackageNameKey, foregroundState.packageName)
                    .putString(ForegroundClassNameKey, foregroundState.className)
                    .putInt(ForegroundEventTypeKey, foregroundState.eventType)
                    .putLong(ForegroundTimestampMillisKey, foregroundState.timestampMillis)
                    .putBoolean(ForegroundIsTrackedKey, foregroundState.isTracked)
                    .putString(ForegroundSessionStatusKey, foregroundState.sessionStatus.name)
                    .putString(ForegroundLastPackageNameKey, foregroundState.lastForegroundPackageName)
                    .putLong(ForegroundInterruptionStartedAtMillisKey, foregroundState.interruptionStartedAtMillis ?: 0L)
                    .putLong(ForegroundSessionElapsedMillisKey, foregroundState.sessionElapsedMillis)
                    .putLong(ForegroundCurrentActiveElapsedMillisKey, foregroundState.currentActiveElapsedMillis)
            }
        }

        editor.apply()
    }

    private fun readForegroundAppState(): ForegroundAppState {
        return when (preferences.getString(ForegroundStateKindKey, ForegroundStateKindUnknown)) {
            ForegroundStateKindPermissionMissing -> ForegroundAppState.PermissionMissing
            ForegroundStateKindUntracked -> ForegroundAppState.Untracked(
                packageName = preferences.getString(ForegroundPackageNameKey, null).orEmpty()
            )
            ForegroundStateKindDetected -> readDetectedForegroundAppState()
            else -> ForegroundAppState.Unknown
        }
    }

    private fun readDetectedForegroundAppState(): ForegroundAppState.Detected {
        val sessionStatus = preferences.getString(ForegroundSessionStatusKey, SessionStatus.Ended.name)
            ?.let(SessionStatus::valueOf)
            ?: SessionStatus.Ended
        val interruptionStartedAt = preferences
            .getLong(ForegroundInterruptionStartedAtMillisKey, 0L)
            .takeIf { it > 0L }

        return ForegroundAppState.Detected(
            packageName = preferences.getString(ForegroundPackageNameKey, null).orEmpty(),
            className = preferences.getString(ForegroundClassNameKey, null),
            eventType = preferences.getInt(ForegroundEventTypeKey, 0),
            timestampMillis = preferences.getLong(ForegroundTimestampMillisKey, 0L),
            isTracked = preferences.getBoolean(ForegroundIsTrackedKey, false),
            sessionStatus = sessionStatus,
            lastForegroundPackageName = preferences.getString(ForegroundLastPackageNameKey, null) ?: "-",
            interruptionStartedAtMillis = interruptionStartedAt,
            sessionElapsedMillis = preferences.getLong(ForegroundSessionElapsedMillisKey, 0L),
            currentActiveElapsedMillis = preferences.getLong(ForegroundCurrentActiveElapsedMillisKey, 0L)
        )
    }

    private fun readAlertState(): AlertState {
        val lastAlertTime = preferences.getLong(AlertLastTimeMillisKey, 0L)

        return AlertState(
            wasSent = preferences.getBoolean(AlertWasSentKey, false),
            lastAlertTimeMillis = lastAlertTime.takeIf { it > 0L },
            lastAlertPackageName = preferences.getString(AlertLastPackageNameKey, null)
        )
    }

    private fun readEffectiveSettings(): FocusGuardSettings {
        return FocusGuardSettings(
            gracePeriodMillis = preferences
                .getInt(EffectiveGracePeriodSecondsKey, (DefaultGracePeriodMillis / 1000).toInt())
                .coerceAtLeast(1) * 1000L,
            sessionLimitMillis = preferences
                .getInt(EffectiveSessionLimitSecondsKey, (DefaultSessionLimitMillis / 1000).toInt())
                .coerceAtLeast(1) * 1000L,
            alertDelayAfterResumeMillis = preferences
                .getInt(
                    EffectiveAlertDelayAfterResumeSecondsKey,
                    (DefaultAlertDelayAfterResumeMillis / 1000).toInt()
                )
                .coerceAtLeast(1) * 1000L
        )
    }
}

private const val WatcherStateStoreName = "focus_guard_watcher_state"
private const val IsRunningKey = "is_running"
private const val LastTickTimeMillisKey = "last_tick_time_millis"
private const val SessionResetTimeMillisKey = "session_reset_time_millis"

private const val ForegroundStateKindKey = "foreground_state_kind"
private const val ForegroundStateKindUnknown = "unknown"
private const val ForegroundStateKindPermissionMissing = "permission_missing"
private const val ForegroundStateKindUntracked = "untracked"
private const val ForegroundStateKindDetected = "detected"
private const val ForegroundPackageNameKey = "foreground_package_name"
private const val ForegroundClassNameKey = "foreground_class_name"
private const val ForegroundEventTypeKey = "foreground_event_type"
private const val ForegroundTimestampMillisKey = "foreground_timestamp_millis"
private const val ForegroundIsTrackedKey = "foreground_is_tracked"
private const val ForegroundSessionStatusKey = "foreground_session_status"
private const val ForegroundLastPackageNameKey = "foreground_last_package_name"
private const val ForegroundInterruptionStartedAtMillisKey = "foreground_interruption_started_at_millis"
private const val ForegroundSessionElapsedMillisKey = "foreground_session_elapsed_millis"
private const val ForegroundCurrentActiveElapsedMillisKey = "foreground_current_active_elapsed_millis"

private const val AlertWasSentKey = "alert_was_sent"
private const val AlertLastTimeMillisKey = "alert_last_time_millis"
private const val AlertLastPackageNameKey = "alert_last_package_name"

private const val EffectiveGracePeriodSecondsKey = "effective_grace_period_seconds"
private const val EffectiveSessionLimitSecondsKey = "effective_session_limit_seconds"
private const val EffectiveAlertDelayAfterResumeSecondsKey = "effective_alert_delay_after_resume_seconds"
