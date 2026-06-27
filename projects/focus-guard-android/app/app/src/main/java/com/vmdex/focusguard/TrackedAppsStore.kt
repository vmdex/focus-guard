package com.vmdex.focusguard

import android.content.Context
import androidx.core.content.edit

class TrackedAppsStore(context: Context) {
    private val preferences = context.getSharedPreferences(TrackedAppsStoreName, Context.MODE_PRIVATE)

    fun load(): Set<String> {
        return preferences.getStringSet(SelectedPackagesKey, emptySet()).orEmpty().toSet()
    }

    fun save(packageNames: Set<String>) {
        preferences.edit {
            putStringSet(SelectedPackagesKey, packageNames.toSet())
        }
    }
}

private const val TrackedAppsStoreName = "focus_guard_tracked_apps"
private const val SelectedPackagesKey = "selected_packages"
