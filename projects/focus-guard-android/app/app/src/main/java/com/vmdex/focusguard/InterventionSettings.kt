package com.vmdex.focusguard

const val DefaultInterventionNotificationTitle = "Focus Guard"
const val DefaultInterventionNotificationMessage = "{app} has been open for {time}"
const val DefaultInterventionPopupMessage = "Time to pause."

data class InterventionSettings(
    val isNotificationEnabled: Boolean = true,
    val isPopupEnabled: Boolean = false,
    val notificationTitle: String = DefaultInterventionNotificationTitle,
    val notificationMessage: String = DefaultInterventionNotificationMessage,
    val popupMessage: String = DefaultInterventionPopupMessage
)
