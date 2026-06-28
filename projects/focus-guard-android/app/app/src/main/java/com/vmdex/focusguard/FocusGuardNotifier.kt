package com.vmdex.focusguard

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

class FocusGuardNotifier(private val context: Context) {
    fun createLimitChannel() {
        val channel = NotificationChannel(
            LimitNotificationChannelId,
            "Focus Guard heads-up alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Heads-up alerts when a tracked app session exceeds its limit."
        }

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    fun showLimitExceeded(
        packageName: String,
        elapsedMillis: Long,
        settings: InterventionSettings
    ): Boolean {
        // Return a boolean so MainActivity can show accurate dev state without knowing notification details.
        if (!hasNotificationPermission()) {
            return false
        }

        val notification = NotificationCompat.Builder(context, LimitNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(settings.notificationTitle)
            .setContentText(
                settings.notificationMessage
                    .replace("{app}", packageName)
                    .replace("{time}", formatElapsed(elapsedMillis))
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(LimitNotificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun hasNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            return true
        }

        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
    }
}
