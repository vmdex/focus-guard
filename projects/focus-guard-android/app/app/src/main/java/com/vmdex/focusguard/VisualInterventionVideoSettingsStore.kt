package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class VisualInterventionVideoSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(StoreName, Context.MODE_PRIVATE)
    private val draftPreferences = context.getSharedPreferences(DraftStoreName, Context.MODE_PRIVATE)

    fun load(videoId: String): VisualInterventionVideoSettings {
        return read(preferences, videoId)
    }

    fun save(videoId: String, settings: VisualInterventionVideoSettings) {
        preferences.edit {
            write(videoId, settings)
        }
    }

    fun beginDraft(videoId: String, settings: VisualInterventionVideoSettings = load(videoId)) {
        draftPreferences.edit {
            write(videoId, settings)
        }
    }

    fun loadDraft(videoId: String): VisualInterventionVideoSettings {
        return read(draftPreferences, videoId)
    }

    fun saveDraft(videoId: String, settings: VisualInterventionVideoSettings) {
        draftPreferences.edit {
            write(videoId, settings)
        }
    }

    fun clearDraft(videoId: String) {
        draftPreferences.edit {
            remove(key(videoId, GreenScreenEnabledSuffix))
            remove(key(videoId, SoundEnabledSuffix))
            remove(key(videoId, ZoomPercentSuffix))
            remove(key(videoId, PositionXSuffix))
            remove(key(videoId, PositionYSuffix))
        }
    }

    private fun read(
        source: android.content.SharedPreferences,
        videoId: String
    ): VisualInterventionVideoSettings {
        val hasPositionX = source.contains(key(videoId, PositionXSuffix))
        val hasPositionY = source.contains(key(videoId, PositionYSuffix))
        return VisualInterventionVideoSettings(
            isGreenScreenEnabled = source.getBoolean(key(videoId, GreenScreenEnabledSuffix), true),
            isSoundEnabled = source.getBoolean(key(videoId, SoundEnabledSuffix), false),
            zoomPercent = source.getInt(key(videoId, ZoomPercentSuffix), 100).coerceAtLeast(1),
            positionX = if (hasPositionX) source.getInt(key(videoId, PositionXSuffix), 0) else null,
            positionY = if (hasPositionY) source.getInt(key(videoId, PositionYSuffix), 0) else null
        )
    }

    private fun android.content.SharedPreferences.Editor.write(
        videoId: String,
        settings: VisualInterventionVideoSettings
    ) {
        putBoolean(key(videoId, GreenScreenEnabledSuffix), settings.isGreenScreenEnabled)
        putBoolean(key(videoId, SoundEnabledSuffix), settings.isSoundEnabled)
        putInt(key(videoId, ZoomPercentSuffix), settings.zoomPercent.coerceAtLeast(1))
        settings.positionX?.let { putInt(key(videoId, PositionXSuffix), it) }
            ?: remove(key(videoId, PositionXSuffix))
        settings.positionY?.let { putInt(key(videoId, PositionYSuffix), it) }
            ?: remove(key(videoId, PositionYSuffix))
    }

    private fun key(videoId: String, suffix: String): String = "$videoId.$suffix"
}

private const val StoreName = "focus_guard_visual_intervention_video_settings"
private const val DraftStoreName = "focus_guard_visual_intervention_video_draft_settings"
private const val GreenScreenEnabledSuffix = "green_screen_enabled"
private const val SoundEnabledSuffix = "sound_enabled"
private const val ZoomPercentSuffix = "zoom_percent"
private const val PositionXSuffix = "position_x"
private const val PositionYSuffix = "position_y"
