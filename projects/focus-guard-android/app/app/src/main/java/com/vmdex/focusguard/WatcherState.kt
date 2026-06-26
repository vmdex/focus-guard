package com.vmdex.focusguard

data class WatcherState(
    val isRunning: Boolean = false,
    val lastTickTimeMillis: Long? = null
)
