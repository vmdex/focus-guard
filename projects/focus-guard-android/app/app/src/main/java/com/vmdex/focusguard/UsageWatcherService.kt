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
    private lateinit var settingsStore: FocusGuardSettingsStore
    private lateinit var notifier: FocusGuardNotifier
    private var effectiveSettings = FocusGuardSettings()
    private var alertedSessionKey: String? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            val state = buildNextWatcherState()
            stateStore.save(state)
            updateMonitoringNotification(state)
            handler.postDelayed(this, WatcherTickMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = WatcherStateStore(this)
        settingsStore = FocusGuardSettingsStore(this)
        notifier = FocusGuardNotifier(this)
        effectiveSettings = stateStore.load().effectiveSettings
        createMonitoringChannel()
        notifier.createLimitChannel()
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
        val state = buildNextWatcherState()
        stateStore.save(state)
        startForeground(WatcherNotificationId, buildMonitoringNotification(state))
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, WatcherTickMillis)
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(tickRunnable)
        stateStore.save(WatcherState(isRunning = false, lastTickTimeMillis = null))
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNextWatcherState(): WatcherState {
        val now = System.currentTimeMillis()
        val savedSettings = settingsStore.load()
        val foregroundAppState = if (hasUsageAccessPermission(this)) {
            readLatestForegroundApp(this, effectiveSettings.gracePeriodMillis)
        } else {
            ForegroundAppState.PermissionMissing
        }
        effectiveSettings = resolveEffectiveSettings(
            savedSettings = savedSettings,
            foregroundAppState = foregroundAppState
        )
        val refreshedForegroundAppState = if (foregroundAppState is ForegroundAppState.Detected) {
            readLatestForegroundApp(this, effectiveSettings.gracePeriodMillis)
        } else {
            foregroundAppState
        }
        val alertState = maybeShowLimitExceededNotification(refreshedForegroundAppState, now)

        return WatcherState(
            isRunning = true,
            lastTickTimeMillis = now,
            foregroundAppState = refreshedForegroundAppState,
            alertState = alertState,
            effectiveSettings = effectiveSettings
        )
    }

    private fun resolveEffectiveSettings(
        savedSettings: FocusGuardSettings,
        foregroundAppState: ForegroundAppState
    ): FocusGuardSettings {
        if (savedSettings == effectiveSettings || isSessionInProgress(foregroundAppState)) {
            return effectiveSettings
        }

        return savedSettings
    }

    private fun maybeShowLimitExceededNotification(
        foregroundAppState: ForegroundAppState,
        currentTimeMillis: Long
    ): AlertState {
        val previousAlertState = stateStore.load().alertState
        val detected = foregroundAppState as? ForegroundAppState.Detected ?: return previousAlertState
        if (detected.sessionStatus == SessionStatus.Ended) {
            return previousAlertState
        }

        val elapsedMillis = calculateSessionElapsedMillis(detected, currentTimeMillis)
        if (elapsedMillis < effectiveSettings.sessionLimitMillis || alertedSessionKey == detected.sessionKey) {
            return previousAlertState
        }

        val wasSent = notifier.showLimitExceeded(detected.packageName, elapsedMillis)
        alertedSessionKey = detected.sessionKey

        return if (wasSent) {
            AlertState(
                wasSent = true,
                lastAlertTimeMillis = currentTimeMillis,
                lastAlertPackageName = detected.packageName
            )
        } else {
            previousAlertState
        }
    }

    private fun updateMonitoringNotification(state: WatcherState) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(WatcherNotificationId, buildMonitoringNotification(state))
    }

    private fun buildMonitoringNotification(state: WatcherState): Notification {
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
            .setContentText(monitoringNotificationText(state))
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun monitoringNotificationText(state: WatcherState): String {
        val detected = state.foregroundAppState as? ForegroundAppState.Detected
        return detected?.let {
            "${it.packageName}: ${it.sessionStatus}"
        } ?: "Last watcher tick: ${state.lastTickTimeMillis?.let(::formatTimestamp) ?: "-"}"
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
