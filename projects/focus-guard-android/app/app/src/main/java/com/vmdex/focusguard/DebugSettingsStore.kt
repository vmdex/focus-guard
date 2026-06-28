package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class DebugSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(DebugSettingsStoreName, Context.MODE_PRIVATE)

    fun load(): DebugSettings {
        return DebugSettings(
            isFloatingDebugWindowEnabled = preferences.getBoolean(
                FloatingDebugWindowEnabledKey,
                true
            ),
            activeTickMillis = readSeconds(ActiveTickSecondsKey, WatcherTickMillis),
            graceTickMillis = readSeconds(GraceTickSecondsKey, WatcherGraceTickMillis),
            idleTickMillis = readSeconds(IdleTickSecondsKey, WatcherIdleTickMillis),
            screenLockedTickMillis = readSeconds(
                ScreenLockedTickSecondsKey,
                WatcherScreenLockedTickMillis
            )
        )
    }

    fun save(settings: DebugSettings) {
        preferences.edit {
            putBoolean(FloatingDebugWindowEnabledKey, settings.isFloatingDebugWindowEnabled)
            putInt(ActiveTickSecondsKey, settings.activeTickSeconds)
            putInt(GraceTickSecondsKey, settings.graceTickSeconds)
            putInt(IdleTickSecondsKey, settings.idleTickSeconds)
            putInt(ScreenLockedTickSecondsKey, settings.screenLockedTickSeconds)
        }
    }

    private fun readSeconds(key: String, defaultMillis: Long): Long {
        return preferences.getInt(key, (defaultMillis / 1000).toInt()).coerceAtLeast(1) * 1000L
    }
}

private const val DebugSettingsStoreName = "focus_guard_debug_settings"
private const val FloatingDebugWindowEnabledKey = "floating_debug_window_enabled"
private const val ActiveTickSecondsKey = "active_tick_seconds"
private const val GraceTickSecondsKey = "grace_tick_seconds"
private const val IdleTickSecondsKey = "idle_tick_seconds"
private const val ScreenLockedTickSecondsKey = "screen_locked_tick_seconds"
