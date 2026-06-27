package com.vmdex.focusguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import androidx.core.app.NotificationCompat

class UsageWatcherService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stateStore: WatcherStateStore
    private lateinit var settingsStore: FocusGuardSettingsStore
    private lateinit var notifier: FocusGuardNotifier
    private lateinit var windowManager: WindowManager
    private var effectiveSettings = FocusGuardSettings()
    private var alertedSessionKey: String? = null
    private var floatingDebugView: TextView? = null
    private var floatingDebugLayoutParams: WindowManager.LayoutParams? = null

    private val tickRunnable = object : Runnable {
        override fun run() {
            val state = buildNextWatcherState()
            stateStore.save(state)
            updateMonitoringNotification(state)
            updateFloatingDebugWindow(state)
            handler.postDelayed(this, WatcherTickMillis)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = WatcherStateStore(this)
        settingsStore = FocusGuardSettingsStore(this)
        notifier = FocusGuardNotifier(this)
        windowManager = getSystemService(WindowManager::class.java)
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
        updateFloatingDebugWindow(state)
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, WatcherTickMillis)
    }

    private fun stopMonitoring() {
        handler.removeCallbacks(tickRunnable)
        removeFloatingDebugWindow()
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
        if (detected.sessionStatus != SessionStatus.Active) {
            return previousAlertState
        }

        val elapsedMillis = calculateSessionElapsedMillis(detected, currentTimeMillis)
        if (elapsedMillis < effectiveSettings.sessionLimitMillis ||
            detected.currentActiveElapsedMillis < effectiveSettings.alertDelayAfterResumeMillis ||
            alertedSessionKey == detected.sessionKey
        ) {
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

    private fun updateFloatingDebugWindow(state: WatcherState) {
        if (!hasOverlayPermission(this)) {
            removeFloatingDebugWindow()
            return
        }

        val view = floatingDebugView ?: createFloatingDebugView().also { floatingDebugView = it }
        view.text = floatingDebugText(state)

        if (view.parent == null) {
            val params = floatingDebugLayoutParams ?: createFloatingDebugLayoutParams()
                .also { floatingDebugLayoutParams = it }
            windowManager.addView(view, params)
        }
    }

    private fun createFloatingDebugView(): TextView {
        return TextView(this).apply {
            text = "FG"
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(18, 10, 18, 10)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f
                setColor(Color.argb(220, 24, 24, 28))
                setStroke(2, Color.argb(230, 96, 165, 250))
            }
            setOnTouchListener(FloatingDebugDragListener())
        }
    }

    private fun createFloatingDebugLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 32
            y = 160
        }
    }

    private fun floatingDebugText(state: WatcherState): String {
        val detected = state.foregroundAppState as? ForegroundAppState.Detected

        return detected?.let {
            "FG ${it.sessionStatus} ${formatElapsed(it.sessionElapsedMillis)}"
        } ?: "FG monitoring"
    }

    private fun removeFloatingDebugWindow() {
        val view = floatingDebugView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
        floatingDebugView = null
        floatingDebugLayoutParams = null
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

    private inner class FloatingDebugDragListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = floatingDebugLayoutParams ?: return false

            return when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(view, params)
                    true
                }

                else -> false
            }
        }
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
