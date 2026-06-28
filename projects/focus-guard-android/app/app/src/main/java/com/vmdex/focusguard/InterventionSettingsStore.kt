package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class InterventionSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(InterventionSettingsStoreName, Context.MODE_PRIVATE)

    fun load(): InterventionSettings {
        return InterventionSettings(
            isNotificationEnabled = preferences.getBoolean(NotificationEnabledKey, true),
            isPopupEnabled = preferences.getBoolean(PopupEnabledKey, false),
            isVisualInterventionEnabled = preferences.getBoolean(VisualInterventionEnabledKey, false),
            notificationTitle = preferences
                .getString(NotificationTitleKey, DefaultInterventionNotificationTitle)
                .orDefault(DefaultInterventionNotificationTitle),
            notificationMessage = preferences
                .getString(NotificationMessageKey, DefaultInterventionNotificationMessage)
                .orDefault(DefaultInterventionNotificationMessage),
            popupMessage = preferences
                .getString(PopupMessageKey, DefaultInterventionPopupMessage)
                .orDefault(DefaultInterventionPopupMessage),
            isVisualInterventionSoundEnabled = preferences.getBoolean(
                VisualInterventionSoundEnabledKey,
                false
            ),
            isVisualInterventionFrameEnabled = preferences.getBoolean(
                VisualInterventionFrameEnabledKey,
                false
            ),
            visualInterventionFrameThicknessDp = preferences
                .getInt(
                    VisualInterventionFrameThicknessDpKey,
                    DefaultVisualInterventionFrameThicknessDp
                )
                .coerceAtLeast(0),
            visualInterventionRotationDegrees = preferences.getInt(
                VisualInterventionRotationDegreesKey,
                DefaultVisualInterventionRotationDegrees
            ),
            isVisualInterventionDebugInfoEnabled = preferences.getBoolean(
                VisualInterventionDebugInfoEnabledKey,
                false
            ),
            visualInterventionZoomPercent = preferences
                .getInt(VisualInterventionZoomPercentKey, DefaultVisualInterventionZoomPercent)
                .coerceAtLeast(1)
        )
    }

    fun save(settings: InterventionSettings) {
        preferences.edit {
            putBoolean(NotificationEnabledKey, settings.isNotificationEnabled)
            putBoolean(PopupEnabledKey, settings.isPopupEnabled)
            putBoolean(VisualInterventionEnabledKey, settings.isVisualInterventionEnabled)
            putString(NotificationTitleKey, settings.notificationTitle.ifBlank { DefaultInterventionNotificationTitle })
            putString(
                NotificationMessageKey,
                settings.notificationMessage.ifBlank { DefaultInterventionNotificationMessage }
            )
            putString(PopupMessageKey, settings.popupMessage.ifBlank { DefaultInterventionPopupMessage })
            putBoolean(
                VisualInterventionSoundEnabledKey,
                settings.isVisualInterventionSoundEnabled
            )
            putBoolean(
                VisualInterventionFrameEnabledKey,
                settings.isVisualInterventionFrameEnabled
            )
            putInt(
                VisualInterventionFrameThicknessDpKey,
                settings.visualInterventionFrameThicknessDp.coerceAtLeast(0)
            )
            putInt(
                VisualInterventionRotationDegreesKey,
                settings.visualInterventionRotationDegrees
            )
            putBoolean(
                VisualInterventionDebugInfoEnabledKey,
                settings.isVisualInterventionDebugInfoEnabled
            )
            putInt(
                VisualInterventionZoomPercentKey,
                settings.visualInterventionZoomPercent.coerceAtLeast(1)
            )
        }
    }
}

private fun String?.orDefault(defaultValue: String): String {
    return this?.takeIf { it.isNotBlank() } ?: defaultValue
}

private const val InterventionSettingsStoreName = "focus_guard_intervention_settings"
private const val NotificationEnabledKey = "notification_enabled"
private const val PopupEnabledKey = "popup_enabled"
private const val VisualInterventionEnabledKey = "visual_intervention_enabled"
private const val NotificationTitleKey = "notification_title"
private const val NotificationMessageKey = "notification_message"
private const val PopupMessageKey = "popup_message"
private const val VisualInterventionSoundEnabledKey = "visual_intervention_sound_enabled"
private const val VisualInterventionFrameEnabledKey = "visual_intervention_frame_enabled"
private const val VisualInterventionFrameThicknessDpKey = "visual_intervention_frame_thickness_dp"
private const val VisualInterventionRotationDegreesKey = "visual_intervention_rotation_degrees"
private const val VisualInterventionDebugInfoEnabledKey = "visual_intervention_debug_info_enabled"
private const val VisualInterventionZoomPercentKey = "visual_intervention_zoom_percent"
