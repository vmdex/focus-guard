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
            )
        )
    }

    fun save(settings: DebugSettings) {
        preferences.edit {
            putBoolean(FloatingDebugWindowEnabledKey, settings.isFloatingDebugWindowEnabled)
        }
    }
}

private const val DebugSettingsStoreName = "focus_guard_debug_settings"
private const val FloatingDebugWindowEnabledKey = "floating_debug_window_enabled"
