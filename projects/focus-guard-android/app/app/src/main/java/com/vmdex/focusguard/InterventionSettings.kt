package com.vmdex.focusguard

const val DefaultInterventionNotificationTitle = "Focus Guard"
const val DefaultInterventionNotificationMessage = "{app} has been open for {time}"
const val DefaultInterventionPopupMessage = "Time to pause."
const val DefaultVisualInterventionFrameThicknessDp = 6
const val DefaultVisualInterventionRotationDegrees = 0
const val DefaultVisualInterventionZoomPercent = 100

data class InterventionSettings(
    val isNotificationEnabled: Boolean = true,
    val isPopupEnabled: Boolean = false,
    val isVisualInterventionEnabled: Boolean = false,
    val notificationTitle: String = DefaultInterventionNotificationTitle,
    val notificationMessage: String = DefaultInterventionNotificationMessage,
    val popupMessage: String = DefaultInterventionPopupMessage,
    val isVisualInterventionSoundEnabled: Boolean = false,
    val isVisualInterventionFrameEnabled: Boolean = false,
    val visualInterventionFrameThicknessDp: Int = DefaultVisualInterventionFrameThicknessDp,
    val visualInterventionRotationDegrees: Int = DefaultVisualInterventionRotationDegrees,
    val isVisualInterventionDebugInfoEnabled: Boolean = false,
    val visualInterventionZoomPercent: Int = DefaultVisualInterventionZoomPercent
)
