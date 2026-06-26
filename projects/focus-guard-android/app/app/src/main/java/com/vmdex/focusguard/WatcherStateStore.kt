package com.vmdex.focusguard

import android.content.Context

class WatcherStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(WatcherStateStoreName, Context.MODE_PRIVATE)

    fun load(): WatcherState {
        val lastTick = preferences.getLong(LastTickTimeMillisKey, 0L)

        return WatcherState(
            isRunning = preferences.getBoolean(IsRunningKey, false),
            lastTickTimeMillis = lastTick.takeIf { it > 0L }
        )
    }

    fun save(state: WatcherState) {
        preferences.edit()
            .putBoolean(IsRunningKey, state.isRunning)
            .putLong(LastTickTimeMillisKey, state.lastTickTimeMillis ?: 0L)
            .apply()
    }
}

private const val WatcherStateStoreName = "focus_guard_watcher_state"
private const val IsRunningKey = "is_running"
private const val LastTickTimeMillisKey = "last_tick_time_millis"
