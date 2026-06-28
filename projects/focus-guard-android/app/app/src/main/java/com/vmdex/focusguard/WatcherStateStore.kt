package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class WatcherStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(WatcherStateStoreName, Context.MODE_PRIVATE)

    fun load(): WatcherState {
        val lastTick = preferences.getLong(LastTickTimeMillisKey, 0L)

        return WatcherState(
            isRunning = preferences.getBoolean(IsRunningKey, false),
            lastTickTimeMillis = lastTick.takeIf { it > 0L },
            foregroundAppState = readForegroundAppState(),
            usageDebugState = readUsageDebugState(),
            deviceInteractionState = readDeviceInteractionState(),
            serviceRestoreState = readServiceRestoreState(),
            alertState = readAlertState(),
            interventionState = readInterventionState(),
            effectiveSettings = readEffectiveSettings(),
            sessionResetTimeMillis = preferences
                .getLong(SessionResetTimeMillisKey, 0L)
                .takeIf { it > 0L }
        )
    }

    fun save(state: WatcherState) {
        preferences.edit {
            putBoolean(IsRunningKey, state.isRunning)
            putLong(LastTickTimeMillisKey, state.lastTickTimeMillis ?: 0L)
            putLong(SessionResetTimeMillisKey, state.sessionResetTimeMillis ?: 0L)
            putLong(UsageQueryStartTimeMillisKey, state.usageDebugState.queryStartTimeMillis ?: 0L)
            putLong(UsageQueryEndTimeMillisKey, state.usageDebugState.queryEndTimeMillis ?: 0L)
            putLong(UsageSinceTimeMillisKey, state.usageDebugState.sinceTimeMillis ?: 0L)
            putString(UsageResolvedForegroundPackageNameKey, state.usageDebugState.resolvedForegroundPackageName)
            putString(UsageLastForegroundStartPackageNameKey, state.usageDebugState.lastForegroundStartPackageName)
            putInt(UsageLastForegroundStartEventTypeKey, state.usageDebugState.lastForegroundStartEventType)
            putLong(
                UsageLastForegroundStartTimeMillisKey,
                state.usageDebugState.lastForegroundStartTimeMillis ?: 0L
            )
            putInt(UsageTransitionCountKey, state.usageDebugState.transitionCount)
            putString(UsageRecentRawEventsKey, state.usageDebugState.recentRawEvents.toStoreString())
            putBoolean(DeviceIsInteractiveKey, state.deviceInteractionState.isInteractive)
            putBoolean(DeviceIsKeyguardLockedKey, state.deviceInteractionState.isKeyguardLocked)
            putString(DeviceLastScreenEventKey, state.deviceInteractionState.lastScreenEvent)
            putLong(
                DeviceLastScreenEventTimeMillisKey,
                state.deviceInteractionState.lastScreenEventTimeMillis ?: 0L
            )
            putString(ServiceStartReasonKey, state.serviceRestoreState.serviceStartReason)
            putLong(ServiceStartTimeMillisKey, state.serviceRestoreState.serviceStartTimeMillis ?: 0L)
            putString(ServiceRestoredSessionKeyKey, state.serviceRestoreState.restoredSessionKey)
            putString(ServiceRestoredSessionStatusKey, state.serviceRestoreState.restoredSessionStatus?.name)
            putLong(
                ServiceRestoredSessionElapsedMillisKey,
                state.serviceRestoreState.restoredSessionElapsedMillis ?: 0L
            )
            putInt(EffectiveGracePeriodSecondsKey, state.effectiveSettings.gracePeriodSeconds)
            putInt(EffectiveSessionLimitSecondsKey, state.effectiveSettings.sessionLimitSeconds)
            putInt(EffectiveAlertDelayAfterResumeSecondsKey, state.effectiveSettings.alertDelayAfterResumeSeconds)
            putBoolean(AlertWasSentKey, state.alertState.wasSent)
            putLong(AlertLastTimeMillisKey, state.alertState.lastAlertTimeMillis ?: 0L)
            putString(AlertLastPackageNameKey, state.alertState.lastAlertPackageName)
            putString(AlertedSessionKeyKey, state.alertState.alertedSessionKey)
            putString(InterventionNotificationStatusKey, state.interventionState.notificationStatus.name)
            putLong(InterventionNotificationLeftMillisKey, state.interventionState.notificationLeftMillis ?: 0L)
            putString(InterventionSessionKeyKey, state.interventionState.sessionKey)

            when (val foregroundState = state.foregroundAppState) {
                ForegroundAppState.PermissionMissing -> {
                    putString(ForegroundStateKindKey, ForegroundStateKindPermissionMissing)
                }

                ForegroundAppState.Unknown -> {
                    putString(ForegroundStateKindKey, ForegroundStateKindUnknown)
                }

                is ForegroundAppState.Untracked -> {
                    putString(ForegroundStateKindKey, ForegroundStateKindUntracked)
                    putString(ForegroundPackageNameKey, foregroundState.packageName)
                }

                is ForegroundAppState.Detected -> {
                    putString(ForegroundStateKindKey, ForegroundStateKindDetected)
                    putString(ForegroundPackageNameKey, foregroundState.packageName)
                    putString(ForegroundClassNameKey, foregroundState.className)
                    putInt(ForegroundEventTypeKey, foregroundState.eventType)
                    putLong(ForegroundTimestampMillisKey, foregroundState.timestampMillis)
                    putBoolean(ForegroundIsTrackedKey, foregroundState.isTracked)
                    putString(ForegroundSessionStatusKey, foregroundState.sessionStatus.name)
                    putString(ForegroundLastPackageNameKey, foregroundState.lastForegroundPackageName)
                    putLong(ForegroundInterruptionStartedAtMillisKey, foregroundState.interruptionStartedAtMillis ?: 0L)
                    putLong(ForegroundSessionElapsedMillisKey, foregroundState.sessionElapsedMillis)
                    putString(ForegroundAppElapsedMillisKey, foregroundState.appElapsedMillis.toElapsedStoreString())
                    putLong(ForegroundCurrentActiveElapsedMillisKey, foregroundState.currentActiveElapsedMillis)
                    putBoolean(ForegroundIsAlertSentForSessionKey, foregroundState.isAlertSentForSession)
                }
            }
        }
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
            appElapsedMillis = preferences
                .getString(ForegroundAppElapsedMillisKey, null)
                .orEmpty()
                .toElapsedMap(),
            currentActiveElapsedMillis = preferences.getLong(ForegroundCurrentActiveElapsedMillisKey, 0L),
            isAlertSentForSession = preferences.getBoolean(ForegroundIsAlertSentForSessionKey, false)
        )
    }

    private fun readAlertState(): AlertState {
        val lastAlertTime = preferences.getLong(AlertLastTimeMillisKey, 0L)

        return AlertState(
            wasSent = preferences.getBoolean(AlertWasSentKey, false),
            lastAlertTimeMillis = lastAlertTime.takeIf { it > 0L },
            lastAlertPackageName = preferences.getString(AlertLastPackageNameKey, null),
            alertedSessionKey = preferences.getString(AlertedSessionKeyKey, null)
        )
    }

    private fun readUsageDebugState(): UsageDebugState {
        return UsageDebugState(
            queryStartTimeMillis = preferences
                .getLong(UsageQueryStartTimeMillisKey, 0L)
                .takeIf { it > 0L },
            queryEndTimeMillis = preferences
                .getLong(UsageQueryEndTimeMillisKey, 0L)
                .takeIf { it > 0L },
            sinceTimeMillis = preferences
                .getLong(UsageSinceTimeMillisKey, 0L)
                .takeIf { it > 0L },
            resolvedForegroundPackageName = preferences.getString(
                UsageResolvedForegroundPackageNameKey,
                null
            ),
            lastForegroundStartPackageName = preferences.getString(
                UsageLastForegroundStartPackageNameKey,
                null
            ),
            lastForegroundStartEventType = preferences.getInt(UsageLastForegroundStartEventTypeKey, 0),
            lastForegroundStartTimeMillis = preferences
                .getLong(UsageLastForegroundStartTimeMillisKey, 0L)
                .takeIf { it > 0L },
            transitionCount = preferences.getInt(UsageTransitionCountKey, 0),
            recentRawEvents = preferences
                .getString(UsageRecentRawEventsKey, null)
                .orEmpty()
                .toUsageRawEventDebugEntries()
        )
    }

    private fun readInterventionState(): InterventionState {
        val notificationLeftMillis = preferences
            .getLong(InterventionNotificationLeftMillisKey, 0L)
            .takeIf { it > 0L }

        return InterventionState(
            notificationStatus = preferences
                .getString(
                    InterventionNotificationStatusKey,
                    InterventionNotificationStatus.NotNeeded.name
                )
                ?.let(InterventionNotificationStatus::valueOf)
                ?: InterventionNotificationStatus.NotNeeded,
            notificationLeftMillis = notificationLeftMillis,
            sessionKey = preferences.getString(InterventionSessionKeyKey, null)
        )
    }

    private fun readDeviceInteractionState(): DeviceInteractionState {
        val lastScreenEventTime = preferences.getLong(DeviceLastScreenEventTimeMillisKey, 0L)

        return DeviceInteractionState(
            isInteractive = preferences.getBoolean(DeviceIsInteractiveKey, true),
            isKeyguardLocked = preferences.getBoolean(DeviceIsKeyguardLockedKey, false),
            lastScreenEvent = preferences.getString(DeviceLastScreenEventKey, null),
            lastScreenEventTimeMillis = lastScreenEventTime.takeIf { it > 0L }
        )
    }

    private fun readServiceRestoreState(): ServiceRestoreState {
        val serviceStartTime = preferences.getLong(ServiceStartTimeMillisKey, 0L)
        val restoredElapsed = preferences.getLong(ServiceRestoredSessionElapsedMillisKey, 0L)

        return ServiceRestoreState(
            serviceStartReason = preferences.getString(ServiceStartReasonKey, null),
            serviceStartTimeMillis = serviceStartTime.takeIf { it > 0L },
            restoredSessionKey = preferences.getString(ServiceRestoredSessionKeyKey, null),
            restoredSessionStatus = preferences
                .getString(ServiceRestoredSessionStatusKey, null)
                ?.let(SessionStatus::valueOf),
            restoredSessionElapsedMillis = restoredElapsed.takeIf { it > 0L }
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

private fun Map<String, Long>.toElapsedStoreString(): String {
    return entries
        .sortedBy { it.key }
        .joinToString(separator = "\n") { (packageName, elapsedMillis) ->
            "$packageName=$elapsedMillis"
        }
}

private fun String.toElapsedMap(): Map<String, Long> {
    if (isBlank()) {
        return emptyMap()
    }

    return lineSequence()
        .mapNotNull { line ->
            val separatorIndex = line.lastIndexOf('=')
            if (separatorIndex <= 0 || separatorIndex == line.lastIndex) {
                return@mapNotNull null
            }

            val packageName = line.substring(0, separatorIndex)
            val elapsedMillis = line.substring(separatorIndex + 1).toLongOrNull() ?: return@mapNotNull null
            packageName to elapsedMillis
        }
        .toMap()
}

private fun List<UsageRawEventDebugEntry>.toStoreString(): String {
    return joinToString(separator = "\n") { event ->
        listOf(
            event.timestampMillis.toString(),
            event.eventType.toString(),
            event.packageName,
            event.className.orEmpty()
        ).joinToString(separator = UsageRawEventFieldSeparator)
    }
}

private fun String.toUsageRawEventDebugEntries(): List<UsageRawEventDebugEntry> {
    if (isBlank()) {
        return emptyList()
    }

    return lineSequence()
        .mapNotNull { line ->
            val parts = line.split(UsageRawEventFieldSeparator, limit = 4)
            if (parts.size < 3) {
                return@mapNotNull null
            }

            val timestampMillis = parts[0].toLongOrNull() ?: return@mapNotNull null
            val eventType = parts[1].toIntOrNull() ?: return@mapNotNull null
            val packageName = parts[2]
            val className = parts.getOrNull(3)?.takeIf { it.isNotBlank() }

            UsageRawEventDebugEntry(
                packageName = packageName,
                className = className,
                eventType = eventType,
                timestampMillis = timestampMillis
            )
        }
        .toList()
}

private const val WatcherStateStoreName = "focus_guard_watcher_state"
private const val IsRunningKey = "is_running"
private const val LastTickTimeMillisKey = "last_tick_time_millis"
private const val SessionResetTimeMillisKey = "session_reset_time_millis"

private const val UsageQueryStartTimeMillisKey = "usage_query_start_time_millis"
private const val UsageQueryEndTimeMillisKey = "usage_query_end_time_millis"
private const val UsageSinceTimeMillisKey = "usage_since_time_millis"
private const val UsageResolvedForegroundPackageNameKey = "usage_resolved_foreground_package_name"
private const val UsageLastForegroundStartPackageNameKey = "usage_last_foreground_start_package_name"
private const val UsageLastForegroundStartEventTypeKey = "usage_last_foreground_start_event_type"
private const val UsageLastForegroundStartTimeMillisKey = "usage_last_foreground_start_time_millis"
private const val UsageTransitionCountKey = "usage_transition_count"
private const val UsageRecentRawEventsKey = "usage_recent_raw_events"
private const val UsageRawEventFieldSeparator = "|"

private const val DeviceIsInteractiveKey = "device_is_interactive"
private const val DeviceIsKeyguardLockedKey = "device_is_keyguard_locked"
private const val DeviceLastScreenEventKey = "device_last_screen_event"
private const val DeviceLastScreenEventTimeMillisKey = "device_last_screen_event_time_millis"

private const val ServiceStartReasonKey = "service_start_reason"
private const val ServiceStartTimeMillisKey = "service_start_time_millis"
private const val ServiceRestoredSessionKeyKey = "service_restored_session_key"
private const val ServiceRestoredSessionStatusKey = "service_restored_session_status"
private const val ServiceRestoredSessionElapsedMillisKey = "service_restored_session_elapsed_millis"

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
private const val ForegroundAppElapsedMillisKey = "foreground_app_elapsed_millis"
private const val ForegroundCurrentActiveElapsedMillisKey = "foreground_current_active_elapsed_millis"
private const val ForegroundIsAlertSentForSessionKey = "foreground_is_alert_sent_for_session"

private const val AlertWasSentKey = "alert_was_sent"
private const val AlertLastTimeMillisKey = "alert_last_time_millis"
private const val AlertLastPackageNameKey = "alert_last_package_name"
private const val AlertedSessionKeyKey = "alerted_session_key"

private const val InterventionNotificationStatusKey = "intervention_notification_status"
private const val InterventionNotificationLeftMillisKey = "intervention_notification_left_millis"
private const val InterventionSessionKeyKey = "intervention_session_key"

private const val EffectiveGracePeriodSecondsKey = "effective_grace_period_seconds"
private const val EffectiveSessionLimitSecondsKey = "effective_session_limit_seconds"
private const val EffectiveAlertDelayAfterResumeSecondsKey = "effective_alert_delay_after_resume_seconds"
