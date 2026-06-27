package com.vmdex.focusguard

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class LaunchableApp(
    val packageName: String,
    val appName: String
)

class InstalledAppProvider(private val context: Context) {
    fun loadLaunchableApps(): List<LaunchableApp> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val packageManager = context.packageManager
        val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.queryIntentActivities(
                intent,
                PackageManager.ResolveInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(intent, 0)
        }

        return activities
            .mapNotNull { resolveInfo ->
                val packageName = resolveInfo.activityInfo?.packageName ?: return@mapNotNull null
                val appName = resolveInfo.loadLabel(packageManager)?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: packageName.substringAfterLast('.')

                LaunchableApp(packageName = packageName, appName = appName)
            }
            .distinctBy { it.packageName }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, LaunchableApp::appName))
    }
}

fun visibleAppsForSelection(
    apps: List<LaunchableApp>,
    selectedPackages: Set<String>,
    searchText: String
): List<LaunchableApp> {
    val query = searchText.trim()

    if (query.isNotEmpty()) {
        return apps.filter { app ->
            app.appName.contains(query, ignoreCase = true)
        }
    }

    return apps.sortedWith(
        compareByDescending<LaunchableApp> { it.packageName in selectedPackages }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.appName }
    )
}
