package com.vmdex.focusguard

data class DebugSettings(
    val isFloatingDebugWindowEnabled: Boolean = true,
    val isSessionTimerEnabled: Boolean = false,
    val activeTickMillis: Long = WatcherTickMillis,
    val graceTickMillis: Long = WatcherGraceTickMillis,
    val idleTickMillis: Long = WatcherIdleTickMillis,
    val screenLockedTickMillis: Long = WatcherScreenLockedTickMillis
) {
    val activeTickSeconds: Int = (activeTickMillis / 1000).toInt()
    val graceTickSeconds: Int = (graceTickMillis / 1000).toInt()
    val idleTickSeconds: Int = (idleTickMillis / 1000).toInt()
    val screenLockedTickSeconds: Int = (screenLockedTickMillis / 1000).toInt()
}
