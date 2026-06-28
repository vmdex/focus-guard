package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

data class OverlayWindowPosition(
    val x: Int,
    val y: Int
)

enum class OverlayWindowKind {
    FloatingDebug,
    SessionTimer
}

enum class OverlayOrientation {
    Portrait,
    Landscape
}

class OverlayWindowPositionStore(context: Context) {
    private val preferences = context.getSharedPreferences(OverlayWindowPositionStoreName, Context.MODE_PRIVATE)

    fun load(
        kind: OverlayWindowKind,
        orientation: OverlayOrientation
    ): OverlayWindowPosition? {
        val prefix = keyPrefix(kind, orientation)
        val hasPosition = preferences.getBoolean("${prefix}_has_position", false)
        if (!hasPosition) {
            return null
        }

        return OverlayWindowPosition(
            x = preferences.getInt("${prefix}_x", 0),
            y = preferences.getInt("${prefix}_y", 0)
        )
    }

    fun save(
        kind: OverlayWindowKind,
        orientation: OverlayOrientation,
        position: OverlayWindowPosition
    ) {
        val prefix = keyPrefix(kind, orientation)
        preferences.edit {
            putBoolean("${prefix}_has_position", true)
            putInt("${prefix}_x", position.x)
            putInt("${prefix}_y", position.y)
        }
    }

    private fun keyPrefix(
        kind: OverlayWindowKind,
        orientation: OverlayOrientation
    ): String {
        return "${kind.name}_${orientation.name}"
    }
}

private const val OverlayWindowPositionStoreName = "focus_guard_overlay_window_positions"
