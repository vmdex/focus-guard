package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class InterventionSettingsStore(context: Context) {
    private val preferences = context.getSharedPreferences(InterventionSettingsStoreName, Context.MODE_PRIVATE)

    fun load(): InterventionSettings {
        return InterventionSettings(
            isNotificationEnabled = preferences.getBoolean(NotificationEnabledKey, true),
            isPopupEnabled = preferences.getBoolean(PopupEnabledKey, false),
            notificationTitle = preferences
                .getString(NotificationTitleKey, DefaultInterventionNotificationTitle)
                .orDefault(DefaultInterventionNotificationTitle),
            notificationMessage = preferences
                .getString(NotificationMessageKey, DefaultInterventionNotificationMessage)
                .orDefault(DefaultInterventionNotificationMessage),
            popupMessage = preferences
                .getString(PopupMessageKey, DefaultInterventionPopupMessage)
                .orDefault(DefaultInterventionPopupMessage)
        )
    }

    fun save(settings: InterventionSettings) {
        preferences.edit {
            putBoolean(NotificationEnabledKey, settings.isNotificationEnabled)
            putBoolean(PopupEnabledKey, settings.isPopupEnabled)
            putString(NotificationTitleKey, settings.notificationTitle.ifBlank { DefaultInterventionNotificationTitle })
            putString(
                NotificationMessageKey,
                settings.notificationMessage.ifBlank { DefaultInterventionNotificationMessage }
            )
            putString(PopupMessageKey, settings.popupMessage.ifBlank { DefaultInterventionPopupMessage })
        }
    }
}

private fun String?.orDefault(defaultValue: String): String {
    return this?.takeIf { it.isNotBlank() } ?: defaultValue
}

private const val InterventionSettingsStoreName = "focus_guard_intervention_settings"
private const val NotificationEnabledKey = "notification_enabled"
private const val PopupEnabledKey = "popup_enabled"
private const val NotificationTitleKey = "notification_title"
private const val NotificationMessageKey = "notification_message"
private const val PopupMessageKey = "popup_message"
