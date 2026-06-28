package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class SessionStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(SessionStateStoreName, Context.MODE_PRIVATE)

    fun load(): PersistedSessionState? {
        val packageName = preferences.getString(SessionPackageNameKey, null) ?: return null
        val sessionStartedAt = preferences.getLong(SessionStartedAtMillisKey, 0L)
        if (sessionStartedAt <= 0L) {
            return null
        }

        val currentActiveStartedAt = preferences
            .getLong(CurrentActiveStartedAtMillisKey, 0L)
            .takeIf { it > 0L }
        val interruptionStartedAt = preferences
            .getLong(InterruptionStartedAtMillisKey, 0L)
            .takeIf { it > 0L }

        return PersistedSessionState(
            packageName = packageName,
            sessionStartedAtMillis = sessionStartedAt,
            sessionElapsedMillis = preferences.getLong(SessionElapsedMillisKey, 0L),
            appElapsedMillis = preferences
                .getString(AppElapsedMillisKey, null)
                .orEmpty()
                .toAppElapsedMillis(),
            currentActiveStartedAtMillis = currentActiveStartedAt,
            interruptionStartedAtMillis = interruptionStartedAt,
            status = preferences.getString(SessionStatusKey, SessionStatus.Ended.name)
                ?.let(SessionStatus::valueOf)
                ?: SessionStatus.Ended,
            effectiveSettings = readEffectiveSettings(),
            alertedSessionKey = preferences
                .getString(AlertedSessionKeyKey, null)
                .migrateSessionKey(packageName, sessionStartedAt),
            lastUpdatedTimeMillis = preferences.getLong(LastUpdatedTimeMillisKey, sessionStartedAt),
            className = preferences.getString(SessionClassNameKey, null),
            eventType = preferences.getInt(SessionEventTypeKey, 0),
            lastForegroundPackageName = preferences.getString(LastForegroundPackageNameKey, null) ?: packageName
        )
    }

    fun save(state: PersistedSessionState) {
        preferences.edit {
            putString(SessionPackageNameKey, state.packageName)
            putLong(SessionStartedAtMillisKey, state.sessionStartedAtMillis)
            putLong(SessionElapsedMillisKey, state.sessionElapsedMillis)
            putString(AppElapsedMillisKey, state.appElapsedMillis.toStoreString())
            putLong(CurrentActiveStartedAtMillisKey, state.currentActiveStartedAtMillis ?: 0L)
            putLong(InterruptionStartedAtMillisKey, state.interruptionStartedAtMillis ?: 0L)
            putString(SessionStatusKey, state.status.name)
            putInt(EffectiveGracePeriodSecondsKey, state.effectiveSettings.gracePeriodSeconds)
            putInt(EffectiveSessionLimitSecondsKey, state.effectiveSettings.sessionLimitSeconds)
            putInt(
                EffectiveAlertDelayAfterResumeSecondsKey,
                state.effectiveSettings.alertDelayAfterResumeSeconds
            )
            putString(AlertedSessionKeyKey, state.alertedSessionKey)
            putLong(LastUpdatedTimeMillisKey, state.lastUpdatedTimeMillis)
            putString(SessionClassNameKey, state.className)
            putInt(SessionEventTypeKey, state.eventType)
            putString(LastForegroundPackageNameKey, state.lastForegroundPackageName)
        }
    }

    fun clear() {
        preferences.edit { clear() }
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

private fun String?.migrateSessionKey(packageName: String, sessionStartedAtMillis: Long): String? {
    val sessionKey = this ?: return null
    val oldPackageSessionKey = "$packageName:$sessionStartedAtMillis"
    val globalSessionKey = "tracked:$sessionStartedAtMillis"

    return if (sessionKey == oldPackageSessionKey) {
        globalSessionKey
    } else {
        sessionKey
    }
}

private fun Map<String, Long>.toStoreString(): String {
    return entries
        .sortedBy { it.key }
        .joinToString(separator = "\n") { (packageName, elapsedMillis) ->
            "$packageName=$elapsedMillis"
        }
}

private fun String.toAppElapsedMillis(): Map<String, Long> {
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

private const val SessionStateStoreName = "focus_guard_session_state"
private const val SessionPackageNameKey = "session_package_name"
private const val SessionStartedAtMillisKey = "session_started_at_millis"
private const val SessionElapsedMillisKey = "session_elapsed_millis"
private const val AppElapsedMillisKey = "app_elapsed_millis"
private const val CurrentActiveStartedAtMillisKey = "current_active_started_at_millis"
private const val InterruptionStartedAtMillisKey = "interruption_started_at_millis"
private const val SessionStatusKey = "session_status"
private const val EffectiveGracePeriodSecondsKey = "effective_grace_period_seconds"
private const val EffectiveSessionLimitSecondsKey = "effective_session_limit_seconds"
private const val EffectiveAlertDelayAfterResumeSecondsKey = "effective_alert_delay_after_resume_seconds"
private const val AlertedSessionKeyKey = "alerted_session_key"
private const val LastUpdatedTimeMillisKey = "last_updated_time_millis"
private const val SessionClassNameKey = "session_class_name"
private const val SessionEventTypeKey = "session_event_type"
private const val LastForegroundPackageNameKey = "last_foreground_package_name"
