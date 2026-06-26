package com.vmdex.focusguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat

class UsageWatcherService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stateStore: WatcherStateStore

    private val tickRunnable = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            stateStore.save(WatcherState(isRunning = true, lastTickTimeMillis = now))
            updateMonitoringNotification(now)
            handler.postDelayed(this, WatcherTickMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = WatcherStateStore(this)
        createMonitoringChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            stopMonitoring()
            stopSelf()
            return START_NOT_STICKY
        }

        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring() {
        val now = System.currentTimeMillis()
        stateStore.save(WatcherState(isRunning = true, lastTickTimeMillis = now))
        startForeground(WatcherNotificationId, buildMonitoringNotification(now))
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, WatcherTickMillis)
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(tickRunnable)
        stateStore.save(WatcherState(isRunning = false, lastTickTimeMillis = null))
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun updateMonitoringNotification(lastTickTimeMillis: Long) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(WatcherNotificationId, buildMonitoringNotification(lastTickTimeMillis))
    }

    private fun buildMonitoringNotification(lastTickTimeMillis: Long): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, WatcherNotificationChannelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Focus Guard monitoring")
            .setContentText("Last watcher tick: ${formatTimestamp(lastTickTimeMillis)}")
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createMonitoringChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }

        val channel = NotificationChannel(
            WatcherNotificationChannelId,
            "Focus Guard monitoring",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Focus Guard usage monitoring active."
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val ActionStart = "com.vmdex.focusguard.action.START_USAGE_WATCHER"
        private const val ActionStop = "com.vmdex.focusguard.action.STOP_USAGE_WATCHER"

        fun start(context: Context) {
            val intent = Intent(context, UsageWatcherService::class.java).setAction(ActionStart)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, UsageWatcherService::class.java).setAction(ActionStop)
            context.startService(intent)
        }
    }
}
