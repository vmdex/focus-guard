package com.vmdex.focusguard

import android.content.Context
import android.provider.Settings

fun hasOverlayPermission(context: Context): Boolean {
    return Settings.canDrawOverlays(context)
}
