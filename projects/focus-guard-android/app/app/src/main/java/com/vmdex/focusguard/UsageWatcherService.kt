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
    private lateinit var sessionStore: SessionStateStore
    private lateinit var settingsStore: FocusGuardSettingsStore
    private lateinit var notifier: FocusGuardNotifier
    private lateinit var sessionEngine: SessionEngine
    private lateinit var windowManager: WindowManager
    private var effectiveSettings = FocusGuardSettings()
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
        sessionStore = SessionStateStore(this)
        settingsStore = FocusGuardSettingsStore(this)
        notifier = FocusGuardNotifier(this)
        sessionEngine = SessionEngine(
            trackedAppPackages = TrackedAppPackages,
            ignoredPackageName = packageName
        )
        windowManager = getSystemService(WindowManager::class.java)
        val savedState = stateStore.load()
        effectiveSettings = savedState.effectiveSettings
        createMonitoringChannel()
        notifier.createLimitChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            stopMonitoring(markNotRunning = true)
            stopSelf()
            return START_NOT_STICKY
        }

        startMonitoring()
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring(markNotRunning = false)
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

    private fun stopMonitoring(markNotRunning: Boolean) {
        handler.removeCallbacks(tickRunnable)
        removeFloatingDebugWindow()
        if (markNotRunning) {
            sessionStore.clear()
            stateStore.save(WatcherState(isRunning = false, lastTickTimeMillis = null))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun buildNextWatcherState(): WatcherState {
        val now = System.currentTimeMillis()
        val savedSettings = settingsStore.load()
        val previousState = stateStore.load()
        if (!hasUsageAccessPermission(this)) {
            return WatcherState(
                isRunning = true,
                lastTickTimeMillis = now,
                foregroundAppState = ForegroundAppState.PermissionMissing,
                alertState = previousState.alertState,
                interventionState = previousState.interventionState,
                effectiveSettings = effectiveSettings,
                sessionResetTimeMillis = previousState.sessionResetTimeMillis
            )
        }

        val previousSession = sessionStore.load()
        val snapshot = readForegroundUsageSnapshot(
            context = this,
            sinceTimeMillis = previousSession?.lastUpdatedTimeMillis ?: previousState.sessionResetTimeMillis
        )
        val engineResult = sessionEngine.buildNextSession(
            previousSession = previousSession,
            snapshot = snapshot,
            savedSettings = savedSettings,
            currentTimeMillis = now
        )
        var session = engineResult.session
        session = maybeShowLimitExceededNotification(session, engineResult.limitAlertRequest, now)
        val currentAlertState = stateStore.load().alertState
        effectiveSettings = session?.effectiveSettings ?: savedSettings

        if (session != null && session.status != SessionStatus.Ended) {
            sessionStore.save(session)
        } else {
            sessionStore.clear()
        }
        val foregroundAppState = foregroundAppStateFromSession(session, snapshot)
        val alertState = currentAlertState.copy(
            alertedSessionKey = session?.alertedSessionKey ?: currentAlertState.alertedSessionKey
        )
        val interventionState = interventionStateForSession(session)

        return WatcherState(
            isRunning = true,
            lastTickTimeMillis = now,
            foregroundAppState = foregroundAppState,
            alertState = alertState,
            interventionState = interventionState,
            effectiveSettings = effectiveSettings,
            sessionResetTimeMillis = previousState.sessionResetTimeMillis
        )
    }

    private fun maybeShowLimitExceededNotification(
        session: PersistedSessionState?,
        limitAlertRequest: LimitAlertRequest?,
        currentTimeMillis: Long
    ): PersistedSessionState? {
        if (session == null || limitAlertRequest == null) {
            return session
        }

        val wasSent = notifier.showLimitExceeded(
            limitAlertRequest.packageName,
            limitAlertRequest.sessionElapsedMillis
        )

        return if (wasSent) {
            val alertState = AlertState(
                wasSent = true,
                lastAlertTimeMillis = currentTimeMillis,
                lastAlertPackageName = limitAlertRequest.packageName,
                alertedSessionKey = limitAlertRequest.sessionKey
            )
            stateStore.save(stateStore.load().copy(alertState = alertState))
            session.copy(alertedSessionKey = limitAlertRequest.sessionKey)
        } else {
            session
        }
    }

    private fun foregroundAppStateFromSession(
        session: PersistedSessionState?,
        snapshot: ForegroundUsageSnapshot
    ): ForegroundAppState {
        if (session == null || session.status == SessionStatus.Ended) {
            return snapshot.lastForegroundPackageName
                ?.let(ForegroundAppState::Untracked)
                ?: ForegroundAppState.Unknown
        }

        return ForegroundAppState.Detected(
            packageName = session.packageName,
            className = session.className,
            eventType = session.eventType,
            timestampMillis = session.sessionStartedAtMillis,
            isTracked = true,
            sessionStatus = session.status,
            lastForegroundPackageName = snapshot.lastForegroundPackageName ?: session.lastForegroundPackageName,
            interruptionStartedAtMillis = session.interruptionStartedAtMillis,
            sessionElapsedMillis = session.sessionElapsedMillis,
            currentActiveElapsedMillis = session.currentActiveElapsedMillis,
            isAlertSentForSession = session.alertedSessionKey == session.sessionKey
        )
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
        return FloatingDebugTextView(this).apply {
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
        return when (val foregroundState = state.foregroundAppState) {
            ForegroundAppState.PermissionMissing -> "Permission missing"
            ForegroundAppState.Unknown -> "FG monitoring"
            is ForegroundAppState.Untracked -> "Not tracking: ${shortPackageName(foregroundState.packageName)}"
            is ForegroundAppState.Detected -> floatingTrackedDebugText(
                foregroundState = foregroundState,
                settings = state.effectiveSettings,
                interventionState = state.interventionState
            )
        }
    }

    private fun floatingTrackedDebugText(
        foregroundState: ForegroundAppState.Detected,
        settings: FocusGuardSettings,
        interventionState: InterventionState
    ): String {
        val currentForegroundPackageName = foregroundState.lastForegroundPackageName
        if (foregroundState.sessionStatus != SessionStatus.Active ||
            currentForegroundPackageName != foregroundState.packageName
        ) {
            return "Not tracking: ${shortPackageName(currentForegroundPackageName)}"
        }

        val limitLeftMillis = (settings.sessionLimitMillis - foregroundState.sessionElapsedMillis).coerceAtLeast(0L)
        val notificationText = floatingNotificationText(interventionState)

        return listOf(
            "Tracking: ${shortPackageName(foregroundState.packageName)}",
            "Session: ${formatElapsed(foregroundState.sessionElapsedMillis)}",
            "Limit left: ${formatElapsed(limitLeftMillis)}",
            notificationText
        ).joinToString(separator = "\n")
    }

    private fun floatingNotificationText(interventionState: InterventionState): String {
        return when (interventionState.notificationStatus) {
            InterventionNotificationStatus.Sent -> "Notification sent"
            InterventionNotificationStatus.WaitingResumeDelay -> {
                "Notification left: ${formatElapsed(interventionState.notificationLeftMillis ?: 0L)} (resume delay)"
            }
            InterventionNotificationStatus.WaitingLimit,
            InterventionNotificationStatus.ReadyToNotify -> {
                "Notification left: ${formatElapsed(interventionState.notificationLeftMillis ?: 0L)}"
            }
            InterventionNotificationStatus.NotNeeded -> "Notification left: -"
        }
    }

    private fun shortPackageName(packageName: String): String {
        return packageName.substringAfterLast('.').ifBlank { packageName }
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

                MotionEvent.ACTION_UP -> {
                    view.performClick()
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

private class FloatingDebugTextView(context: Context) : TextView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
