package com.vmdex.focusguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.graphics.drawable.GradientDrawable
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.app.NotificationCompat

class UsageWatcherService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var stateStore: WatcherStateStore
    private lateinit var sessionStore: SessionStateStore
    private lateinit var settingsStore: FocusGuardSettingsStore
    private lateinit var interventionSettingsStore: InterventionSettingsStore
    private lateinit var debugSettingsStore: DebugSettingsStore
    private lateinit var overlayWindowPositionStore: OverlayWindowPositionStore
    private lateinit var trackedAppsStore: TrackedAppsStore
    private lateinit var visualVideoSettingsStore: VisualInterventionVideoSettingsStore
    private lateinit var notifier: FocusGuardNotifier
    private lateinit var windowManager: WindowManager
    private var effectiveSettings = FocusGuardSettings()
    private var floatingDebugView: TextView? = null
    private var floatingDebugLayoutParams: WindowManager.LayoutParams? = null
    private var floatingDebugOrientation: OverlayOrientation? = null
    private var sessionTimerView: TextView? = null
    private var sessionTimerLayoutParams: WindowManager.LayoutParams? = null
    private var sessionTimerOrientation: OverlayOrientation? = null
    private var interventionPopupView: TextView? = null
    private var interventionPopupLayoutParams: WindowManager.LayoutParams? = null
    private var hideInterventionPopupRunnable: Runnable? = null
    private var visualInterventionView: View? = null
    private var visualInterventionLayoutParams: WindowManager.LayoutParams? = null
    private var hideVisualInterventionRunnable: Runnable? = null
    private var visualInterventionMediaPlayer: MediaPlayer? = null
    private var visualPositionEditorView: View? = null
    private var visualPositionEditorLayoutParams: WindowManager.LayoutParams? = null
    private var visualPositionEditorVideoId: String? = null
    private var isScreenEventReceiverRegistered = false
    private var serviceRestoreState = ServiceRestoreState()

    private val screenEventReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = buildNextWatcherState(screenEvent = intent.action?.screenEventLabel())
            stateStore.save(state)
            updateMonitoringNotification(state)
            updateFloatingDebugWindow(state)
            updateSessionTimerWindow(state)
            scheduleNextTick(state)
        }
    }

    private val tickRunnable = object : Runnable {
        override fun run() {
            val state = buildNextWatcherState()
            stateStore.save(state)
            updateMonitoringNotification(state)
            updateFloatingDebugWindow(state)
            updateSessionTimerWindow(state)
            scheduleNextTick(state)
        }
    }

    override fun onCreate() {
        super.onCreate()
        stateStore = WatcherStateStore(this)
        sessionStore = SessionStateStore(this)
        settingsStore = FocusGuardSettingsStore(this)
        interventionSettingsStore = InterventionSettingsStore(this)
        debugSettingsStore = DebugSettingsStore(this)
        overlayWindowPositionStore = OverlayWindowPositionStore(this)
        trackedAppsStore = TrackedAppsStore(this)
        visualVideoSettingsStore = VisualInterventionVideoSettingsStore(this)
        notifier = FocusGuardNotifier(this)
        windowManager = getSystemService(WindowManager::class.java)
        val savedState = stateStore.load()
        effectiveSettings = savedState.effectiveSettings
        createMonitoringChannel()
        notifier.createLimitChannel()
        notifier.createBootResumeChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ActionStop) {
            stopMonitoring(markNotRunning = true)
            stopSelf()
            return START_NOT_STICKY
        }

        if (intent?.action == ActionPlayVisualPreview) {
            playVisualInterventionPreview(intent)
            return START_NOT_STICKY
        }

        if (intent?.action == ActionEditVisualPosition) {
            showVisualPositionEditor(intent)
            return START_NOT_STICKY
        }

        if (intent?.action == ActionHideVisualPositionEditor) {
            removeVisualPositionEditor()
            return START_NOT_STICKY
        }

        startMonitoring(startReason = serviceStartReason(intent))
        return START_STICKY
    }

    override fun onDestroy() {
        stopMonitoring(markNotRunning = false)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMonitoring(startReason: String = ServiceStartReasonDirectCall) {
        serviceRestoreState = restoreStateForServiceStart(startReason)
        val state = buildNextWatcherState()
        stateStore.save(state)
        startForeground(WatcherNotificationId, buildMonitoringNotification(state))
        registerScreenEventReceiver()
        updateFloatingDebugWindow(state)
        updateSessionTimerWindow(state)
        handler.removeCallbacks(tickRunnable)
        scheduleNextTick(state)
    }

    private fun stopMonitoring(markNotRunning: Boolean) {
        handler.removeCallbacks(tickRunnable)
        unregisterScreenEventReceiver()
        hideInterventionPopupRunnable?.let(handler::removeCallbacks)
        hideVisualInterventionRunnable?.let(handler::removeCallbacks)
        removeFloatingDebugWindow()
        removeSessionTimerWindow()
        removeInterventionPopup()
        removeVisualIntervention()
        removeVisualPositionEditor()
        if (markNotRunning) {
            sessionStore.clear()
            stateStore.save(WatcherState(isRunning = false, lastTickTimeMillis = null))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun scheduleNextTick(state: WatcherState) {
        handler.removeCallbacks(tickRunnable)
        handler.postDelayed(tickRunnable, tickDelayMillis(state))
    }

    private fun tickDelayMillis(state: WatcherState): Long {
        val debugSettings = debugSettingsStore.load()
        if (state.deviceInteractionState.isScreenLocked) {
            return debugSettings.screenLockedTickMillis
        }

        return when (val foregroundAppState = state.foregroundAppState) {
            is ForegroundAppState.Detected -> when (foregroundAppState.sessionStatus) {
                SessionStatus.Active -> debugSettings.activeTickMillis
                SessionStatus.GracePeriod -> debugSettings.graceTickMillis
                SessionStatus.PausedByScreenLock -> debugSettings.screenLockedTickMillis
                SessionStatus.Ended -> debugSettings.idleTickMillis
            }

            ForegroundAppState.PermissionMissing,
            ForegroundAppState.Unknown,
            is ForegroundAppState.Untracked -> debugSettings.idleTickMillis
        }
    }

    private fun buildNextWatcherState(): WatcherState {
        return buildNextWatcherState(screenEvent = null)
    }

    private fun buildNextWatcherState(screenEvent: String?): WatcherState {
        val now = System.currentTimeMillis()
        val savedSettings = settingsStore.load()
        val previousState = stateStore.load()
        val deviceInteractionState = currentDeviceInteractionState(
            previousState = previousState.deviceInteractionState,
            screenEvent = screenEvent,
            currentTimeMillis = now
        )
        if (!hasUsageAccessPermission(this)) {
            return WatcherState(
                isRunning = true,
                lastTickTimeMillis = now,
                foregroundAppState = ForegroundAppState.PermissionMissing,
                usageDebugState = previousState.usageDebugState,
                deviceInteractionState = deviceInteractionState,
                serviceRestoreState = serviceRestoreState,
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
        val engineResult = sessionEngine().buildNextSession(
            previousSession = previousSession,
            snapshot = snapshot,
            savedSettings = savedSettings,
            currentTimeMillis = now,
            isScreenLocked = deviceInteractionState.isScreenLocked
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
            usageDebugState = snapshot.debugState,
            deviceInteractionState = deviceInteractionState,
            serviceRestoreState = serviceRestoreState,
            alertState = alertState,
            interventionState = interventionState,
            effectiveSettings = effectiveSettings,
            sessionResetTimeMillis = previousState.sessionResetTimeMillis
        )
    }

    private fun restoreStateForServiceStart(startReason: String): ServiceRestoreState {
        val restoredSession = sessionStore.load()
        return ServiceRestoreState(
            serviceStartReason = startReason,
            serviceStartTimeMillis = System.currentTimeMillis(),
            restoredSessionKey = restoredSession?.sessionKey,
            restoredSessionStatus = restoredSession?.status,
            restoredSessionElapsedMillis = restoredSession?.sessionElapsedMillis
        )
    }

    private fun serviceStartReason(intent: Intent?): String {
        return when (intent?.action) {
            null -> "sticky restart"
            ActionStart -> manualStartReason()
            else -> "unknown action"
        }
    }

    private fun manualStartReason(): String {
        val previousReason = stateStore.load().serviceRestoreState.serviceStartReason
        return if (previousReason?.startsWith("boot resume") == true) {
            "manual start after $previousReason"
        } else {
            "manual start"
        }
    }

    private fun currentDeviceInteractionState(
        previousState: DeviceInteractionState,
        screenEvent: String?,
        currentTimeMillis: Long
    ): DeviceInteractionState {
        val powerManager = getSystemService(PowerManager::class.java)
        val keyguardManager = getSystemService(KeyguardManager::class.java)

        return DeviceInteractionState(
            isInteractive = powerManager?.isInteractive ?: previousState.isInteractive,
            isKeyguardLocked = keyguardManager?.isKeyguardLocked ?: previousState.isKeyguardLocked,
            lastScreenEvent = screenEvent ?: previousState.lastScreenEvent,
            lastScreenEventTimeMillis = if (screenEvent != null) {
                currentTimeMillis
            } else {
                previousState.lastScreenEventTimeMillis
            }
        )
    }

    private fun sessionEngine(): SessionEngine {
        return SessionEngine(
            trackedAppPackages = trackedAppsStore.load(),
            ignoredPackageName = packageName
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

        val interventionSettings = interventionSettingsStore.load()
        var wasDelivered = false

        if (interventionSettings.isNotificationEnabled) {
            wasDelivered = notifier.showLimitExceeded(
                limitAlertRequest.packageName,
                limitAlertRequest.sessionElapsedMillis,
                interventionSettings
            )
        }

        if (interventionSettings.isPopupEnabled) {
            wasDelivered = showInterventionPopup(
                message = interventionSettings.popupMessage
                    .replace("{app}", limitAlertRequest.packageName)
                    .replace("{time}", formatElapsed(limitAlertRequest.sessionElapsedMillis))
            ) || wasDelivered
        }

        if (interventionSettings.isVisualInterventionEnabled) {
            wasDelivered = showVisualIntervention(interventionSettings) || wasDelivered
        }

        return if (wasDelivered) {
            val alertState = AlertState(
                wasSent = true,
                lastAlertTimeMillis = currentTimeMillis,
                lastAlertPackageName = limitAlertRequest.packageName,
                alertedSessionKey = limitAlertRequest.sessionKey
            )
            val alertedSession = session.copy(alertedSessionKey = limitAlertRequest.sessionKey)
            sessionStore.save(alertedSession)
            stateStore.save(stateStore.load().copy(alertState = alertState))
            alertedSession
        } else {
            session
        }
    }

    private fun showInterventionPopup(message: String): Boolean {
        if (!hasOverlayPermission(this)) {
            return false
        }

        val view = interventionPopupView ?: createInterventionPopupView().also { interventionPopupView = it }
        view.text = message.ifBlank { DefaultInterventionPopupMessage }

        if (view.parent == null) {
            val params = interventionPopupLayoutParams ?: createInterventionPopupLayoutParams()
                .also { interventionPopupLayoutParams = it }
            windowManager.addView(view, params)
        }

        hideInterventionPopupRunnable?.let(handler::removeCallbacks)
        hideInterventionPopupRunnable = Runnable {
            removeInterventionPopup()
        }.also { runnable ->
            handler.postDelayed(runnable, InterventionPopupDurationMillis)
        }

        return true
    }

    private fun showVisualIntervention(settings: InterventionSettings): Boolean {
        if (!hasOverlayPermission(this)) {
            return false
        }

        removeVisualIntervention()

        val params = createVisualInterventionLayoutParams(settings)
        val container = createVisualInterventionView(settings, params)
        visualInterventionView = container
        visualInterventionLayoutParams = params
        windowManager.addView(container, params)

        hideVisualInterventionRunnable?.let(handler::removeCallbacks)
        hideVisualInterventionRunnable = Runnable {
            removeVisualIntervention()
        }.also { runnable ->
            handler.postDelayed(runnable, InterventionPopupDurationMillis)
        }

        return true
    }

    private fun playVisualInterventionPreview(intent: Intent) {
        val videoId = intent.getStringExtra(ExtraVisualVideoId) ?: return
        val video = visualInterventionVideoById(videoId) ?: return
        val settings = if (intent.getBooleanExtra(ExtraUseDraftVisualSettings, false)) {
            visualVideoSettingsStore.loadDraft(videoId)
        } else {
            visualVideoSettingsStore.load(videoId)
        }
        showVisualInterventionVideo(
            resourceId = video.resourceId,
            settings = settings,
            durationMillis = InterventionPopupDurationMillis
        )
    }

    private fun showVisualInterventionVideo(
        resourceId: Int,
        settings: VisualInterventionVideoSettings,
        durationMillis: Long
    ): Boolean {
        if (!hasOverlayPermission(this)) {
            return false
        }

        removeVisualIntervention()

        val size = visualInterventionVideoSizePx(resourceId, settings.zoomPercent)
        val container = createVisualInterventionVideoView(
            resourceId = resourceId,
            settings = settings,
            size = size
        )
        val params = createVisualInterventionVideoLayoutParams(size, settings)
        visualInterventionView = container
        visualInterventionLayoutParams = params
        windowManager.addView(container, params)

        hideVisualInterventionRunnable?.let(handler::removeCallbacks)
        hideVisualInterventionRunnable = Runnable {
            removeVisualIntervention()
        }.also { runnable ->
            handler.postDelayed(runnable, durationMillis)
        }

        return true
    }

    private fun showVisualPositionEditor(intent: Intent) {
        val videoId = intent.getStringExtra(ExtraVisualVideoId) ?: return
        if (!hasOverlayPermission(this)) {
            return
        }

        removeVisualPositionEditor()

        val video = visualInterventionVideoById(videoId) ?: return
        val settings = visualVideoSettingsStore.loadDraft(videoId)
        val size = visualInterventionVideoSizePx(video.resourceId, settings.zoomPercent)
        val params = createVisualInterventionVideoLayoutParams(size, settings)
        val view = createVisualPositionEditorView(videoId)
        visualPositionEditorVideoId = videoId
        visualPositionEditorView = view
        visualPositionEditorLayoutParams = params
        windowManager.addView(view, params)
        visualVideoSettingsStore.saveDraft(
            videoId,
            settings.copy(positionX = params.x, positionY = params.y)
        )
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
            appElapsedMillis = session.appElapsedMillis,
            currentActiveElapsedMillis = session.currentActiveElapsedMillis,
            isAlertSentForSession = session.alertedSessionKey == session.sessionKey
        )
    }

    private fun updateMonitoringNotification(state: WatcherState) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(WatcherNotificationId, buildMonitoringNotification(state))
    }

    private fun updateFloatingDebugWindow(state: WatcherState) {
        if (!debugSettingsStore.load().isFloatingDebugWindowEnabled) {
            removeFloatingDebugWindow()
            return
        }

        if (!hasOverlayPermission(this)) {
            removeFloatingDebugWindow()
            return
        }

        val view = floatingDebugView ?: createFloatingDebugView().also { floatingDebugView = it }
        view.text = floatingDebugText(state)
        val orientation = currentOverlayOrientation()
        if (view.parent != null && floatingDebugOrientation != orientation) {
            windowManager.removeView(view)
            floatingDebugLayoutParams = null
        }

        if (view.parent == null) {
            val params = floatingDebugLayoutParams ?: createFloatingDebugLayoutParams()
                .also { floatingDebugLayoutParams = it }
            floatingDebugOrientation = orientation
            windowManager.addView(view, params)
        }
    }

    private fun updateSessionTimerWindow(state: WatcherState) {
        val detected = state.foregroundAppState as? ForegroundAppState.Detected
        if (!settingsStore.load().isSessionTimerEnabled ||
            detected == null ||
            detected.sessionStatus != SessionStatus.Active ||
            detected.lastForegroundPackageName != detected.packageName
        ) {
            removeSessionTimerWindow()
            return
        }

        if (!hasOverlayPermission(this)) {
            removeSessionTimerWindow()
            return
        }

        val view = sessionTimerView ?: createSessionTimerView().also { sessionTimerView = it }
        val timerText = formatSessionTimer(detected.sessionElapsedMillis)
        view.text = timerText
        view.textSize = if (timerText.length > 5) 12f else 14f
        val orientation = currentOverlayOrientation()
        if (view.parent != null && sessionTimerOrientation != orientation) {
            windowManager.removeView(view)
            sessionTimerLayoutParams = null
        }

        if (view.parent == null) {
            val params = sessionTimerLayoutParams ?: createSessionTimerLayoutParams()
                .also { sessionTimerLayoutParams = it }
            sessionTimerOrientation = orientation
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

    private fun createSessionTimerView(): TextView {
        return SessionTimerTextView(this).apply {
            text = "00:00"
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            textSize = 14f
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(230, 20, 24, 28))
                setStroke(timerStrokeWidthPx(), Color.argb(245, 96, 165, 250))
            }
            setOnTouchListener(SessionTimerDragListener())
        }
    }

    private fun createInterventionPopupView(): TextView {
        return TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            setPadding(28, 18, 28, 18)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 28f
                setColor(Color.argb(235, 20, 24, 28))
                setStroke(2, Color.argb(245, 250, 204, 21))
            }
        }
    }

    private fun createVisualInterventionView(
        settings: InterventionSettings,
        params: WindowManager.LayoutParams
    ): FrameLayout {
        val frameThicknessPx = if (settings.isVisualInterventionFrameEnabled) {
            dpToPx(settings.visualInterventionFrameThicknessDp)
        } else {
            0
        }
        val videoSizePx = visualInterventionVideoSizePx(settings)
        val containerSizePx = videoSizePx + frameThicknessPx * 2

        val container = FrameLayout(this).apply {
            setPadding(frameThicknessPx, frameThicknessPx, frameThicknessPx, frameThicknessPx)
            if (settings.isVisualInterventionFrameEnabled) {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.WHITE)
                }
            }
            clipToPadding = false
        }

        val videoView = TextureView(this).apply {
            surfaceTextureListener = VisualInterventionTextureListener(
                resourceId = R.raw.visual_intervention_happi_happi_happi,
                isSoundEnabled = settings.isVisualInterventionSoundEnabled
            )
        }
        container.addView(
            videoView,
            FrameLayout.LayoutParams(videoSizePx, videoSizePx, Gravity.CENTER)
        )

        if (settings.isVisualInterventionDebugInfoEnabled) {
            val debugText = TextView(this).apply {
                setTextColor(Color.WHITE)
                textSize = 12f
                setPadding(8, 4, 8, 4)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    setColor(Color.argb(200, 0, 0, 0))
                }
                val pivotScreenX = params.x
                val pivotScreenY = params.y + containerSizePx
                text = "w:${containerSizePx}px h:${containerSizePx}px angle:${settings.visualInterventionRotationDegrees} pivot:0,$containerSizePx screen:$pivotScreenX,$pivotScreenY"
            }
            container.addView(
                debugText,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                )
            )
        }

        container.post {
            container.pivotX = 0f
            container.pivotY = container.height.toFloat()
            container.rotation = settings.visualInterventionRotationDegrees.toFloat()
        }

        return container
    }

    private fun createVisualInterventionVideoView(
        resourceId: Int,
        settings: VisualInterventionVideoSettings,
        size: VideoOverlaySize
    ): FrameLayout {
        return FrameLayout(this).apply {
            val videoView = TextureView(this@UsageWatcherService).apply {
                surfaceTextureListener = VisualInterventionTextureListener(
                    resourceId = resourceId,
                    isSoundEnabled = settings.isSoundEnabled
                )
            }
            addView(
                videoView,
                FrameLayout.LayoutParams(size.widthPx, size.heightPx, Gravity.CENTER)
            )
        }
    }

    private fun createVisualPositionEditorView(videoId: String): FrameLayout {
        return FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.argb(35, 255, 255, 255))
                setStroke(dpToPx(3).coerceAtLeast(1), Color.WHITE)
            }
            setOnTouchListener(VisualPositionEditorDragListener(videoId))
        }
    }

    private fun createFloatingDebugLayoutParams(): WindowManager.LayoutParams {
        val position = overlayWindowPositionStore.load(
            kind = OverlayWindowKind.FloatingDebug,
            orientation = currentOverlayOrientation()
        )
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position?.x ?: 32
            y = position?.y ?: 160
        }
    }

    private fun createSessionTimerLayoutParams(): WindowManager.LayoutParams {
        val size = sessionTimerSizePx()
        val position = overlayWindowPositionStore.load(
            kind = OverlayWindowKind.SessionTimer,
            orientation = currentOverlayOrientation()
        )
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = position?.x ?: 32
            y = position?.y ?: 320
        }
    }

    private fun createInterventionPopupLayoutParams(): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 220
        }
    }

    private fun createVisualInterventionLayoutParams(
        settings: InterventionSettings
    ): WindowManager.LayoutParams {
        val frameThicknessPx = if (settings.isVisualInterventionFrameEnabled) {
            dpToPx(settings.visualInterventionFrameThicknessDp)
        } else {
            0
        }
        val size = visualInterventionVideoSizePx(settings) + frameThicknessPx * 2
        return WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = ((resources.displayMetrics.widthPixels - size) / 2).coerceAtLeast(0)
            y = ((resources.displayMetrics.heightPixels - size) / 2).coerceAtLeast(0)
        }
    }

    private fun createVisualInterventionVideoLayoutParams(
        size: VideoOverlaySize,
        settings: VisualInterventionVideoSettings
    ): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            size.widthPx,
            size.heightPx,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = settings.positionX ?: ((resources.displayMetrics.widthPixels - size.widthPx) / 2).coerceAtLeast(0)
            y = settings.positionY ?: ((resources.displayMetrics.heightPixels - size.heightPx) / 2).coerceAtLeast(0)
        }
    }

    private fun floatingDebugText(state: WatcherState): String {
        return floatingDebugTextForState(state)
    }

    private fun removeFloatingDebugWindow() {
        val view = floatingDebugView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
        floatingDebugView = null
        floatingDebugLayoutParams = null
        floatingDebugOrientation = null
    }

    private fun removeSessionTimerWindow() {
        val view = sessionTimerView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
        sessionTimerView = null
        sessionTimerLayoutParams = null
        sessionTimerOrientation = null
    }

    private fun removeInterventionPopup() {
        val view = interventionPopupView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
        interventionPopupView = null
        interventionPopupLayoutParams = null
        hideInterventionPopupRunnable = null
    }

    private fun removeVisualIntervention() {
        val view = visualInterventionView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
        visualInterventionMediaPlayer?.release()
        visualInterventionMediaPlayer = null
        visualInterventionView = null
        visualInterventionLayoutParams = null
        hideVisualInterventionRunnable = null
    }

    private fun removeVisualPositionEditor() {
        val view = visualPositionEditorView ?: return
        if (view.parent != null) {
            windowManager.removeView(view)
        }
        visualPositionEditorView = null
        visualPositionEditorLayoutParams = null
        visualPositionEditorVideoId = null
    }

    private fun registerScreenEventReceiver() {
        if (isScreenEventReceiverRegistered) {
            return
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        registerReceiver(screenEventReceiver, filter)
        isScreenEventReceiverRegistered = true
    }

    private fun unregisterScreenEventReceiver() {
        if (!isScreenEventReceiverRegistered) {
            return
        }

        unregisterReceiver(screenEventReceiver)
        isScreenEventReceiverRegistered = false
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
                    saveOverlayWindowPosition(
                        kind = OverlayWindowKind.FloatingDebug,
                        params = params
                    )
                    view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private inner class SessionTimerDragListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = sessionTimerLayoutParams ?: return false

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
                    saveOverlayWindowPosition(
                        kind = OverlayWindowKind.SessionTimer,
                        params = params
                    )
                    view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private inner class VisualInterventionTextureListener(
        private val resourceId: Int,
        private val isSoundEnabled: Boolean
    ) : TextureView.SurfaceTextureListener {
        private var surface: Surface? = null

        override fun onSurfaceTextureAvailable(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
            val playbackSurface = Surface(surfaceTexture)
            surface = playbackSurface
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(
                    this@UsageWatcherService,
                    Uri.parse("android.resource://$packageName/$resourceId")
                )
                setSurface(playbackSurface)
                isLooping = true
                val volume = if (isSoundEnabled) 1f else 0f
                setVolume(volume, volume)
                setOnPreparedListener { player -> player.start() }
                prepareAsync()
            }
            visualInterventionMediaPlayer?.release()
            visualInterventionMediaPlayer = mediaPlayer
        }

        override fun onSurfaceTextureSizeChanged(
            surfaceTexture: SurfaceTexture,
            width: Int,
            height: Int
        ) = Unit

        override fun onSurfaceTextureDestroyed(surfaceTexture: SurfaceTexture): Boolean {
            visualInterventionMediaPlayer?.release()
            visualInterventionMediaPlayer = null
            surface?.release()
            surface = null
            return true
        }

        override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) = Unit
    }

    private inner class VisualPositionEditorDragListener(
        private val videoId: String
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val params = visualPositionEditorLayoutParams ?: return false

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
                    saveVisualDraftPosition(videoId, params)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    saveVisualDraftPosition(videoId, params)
                    view.performClick()
                    true
                }

                else -> false
            }
        }
    }

    private fun sessionTimerSizePx(): Int {
        return (72 * resources.displayMetrics.density).toInt()
    }

    private fun timerStrokeWidthPx(): Int {
        return (3 * resources.displayMetrics.density).toInt().coerceAtLeast(1)
    }

    private fun visualInterventionVideoSizePx(settings: InterventionSettings): Int {
        return visualInterventionVideoSizePx(settings.visualInterventionZoomPercent)
    }

    private fun visualInterventionVideoSizePx(zoomPercent: Int): Int {
        return (dpToPx(260) * zoomPercent.coerceAtLeast(1)) / 100
    }

    private fun visualInterventionVideoSizePx(
        resourceId: Int,
        zoomPercent: Int
    ): VideoOverlaySize {
        val baseMaxPx = visualInterventionVideoSizePx(zoomPercent)
        val intrinsicSize = readRawVideoSize(resourceId)
        val width = intrinsicSize.widthPx.coerceAtLeast(1)
        val height = intrinsicSize.heightPx.coerceAtLeast(1)

        return if (width >= height) {
            VideoOverlaySize(
                widthPx = baseMaxPx,
                heightPx = (baseMaxPx * height / width).coerceAtLeast(1)
            )
        } else {
            VideoOverlaySize(
                widthPx = (baseMaxPx * width / height).coerceAtLeast(1),
                heightPx = baseMaxPx
            )
        }
    }

    private fun readRawVideoSize(resourceId: Int): VideoOverlaySize {
        return runCatching {
            val retriever = MediaMetadataRetriever()
            resources.openRawResourceFd(resourceId).use { descriptor ->
                retriever.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }
            val width = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull()
                ?: 1
            val height = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull()
                ?: 1
            retriever.release()
            VideoOverlaySize(widthPx = width, heightPx = height)
        }.getOrDefault(VideoOverlaySize(widthPx = 1, heightPx = 1))
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt().coerceAtLeast(0)
    }

    private fun saveVisualDraftPosition(
        videoId: String,
        params: WindowManager.LayoutParams
    ) {
        val settings = visualVideoSettingsStore.loadDraft(videoId)
        visualVideoSettingsStore.saveDraft(
            videoId,
            settings.copy(positionX = params.x, positionY = params.y)
        )
    }

    private fun saveOverlayWindowPosition(
        kind: OverlayWindowKind,
        params: WindowManager.LayoutParams
    ) {
        overlayWindowPositionStore.save(
            kind = kind,
            orientation = currentOverlayOrientation(),
            position = OverlayWindowPosition(x = params.x, y = params.y)
        )
    }

    private fun currentOverlayOrientation(): OverlayOrientation {
        return if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            OverlayOrientation.Landscape
        } else {
            OverlayOrientation.Portrait
        }
    }

    companion object {
        private const val ActionStart = "com.vmdex.focusguard.action.START_USAGE_WATCHER"
        private const val ActionStop = "com.vmdex.focusguard.action.STOP_USAGE_WATCHER"
        private const val ActionPlayVisualPreview = "com.vmdex.focusguard.action.PLAY_VISUAL_PREVIEW"
        private const val ActionEditVisualPosition = "com.vmdex.focusguard.action.EDIT_VISUAL_POSITION"
        private const val ActionHideVisualPositionEditor =
            "com.vmdex.focusguard.action.HIDE_VISUAL_POSITION_EDITOR"
        private const val ExtraVisualVideoId = "visual_video_id"
        private const val ExtraUseDraftVisualSettings = "use_draft_visual_settings"
        private const val InterventionPopupDurationMillis = 5_000L
        private const val ServiceStartReasonDirectCall = "direct call"

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

        fun playVisualPreview(
            context: Context,
            videoId: String,
            useDraftSettings: Boolean
        ) {
            val intent = Intent(context, UsageWatcherService::class.java)
                .setAction(ActionPlayVisualPreview)
                .putExtra(ExtraVisualVideoId, videoId)
                .putExtra(ExtraUseDraftVisualSettings, useDraftSettings)
            context.startService(intent)
        }

        fun editVisualPosition(context: Context, videoId: String) {
            val intent = Intent(context, UsageWatcherService::class.java)
                .setAction(ActionEditVisualPosition)
                .putExtra(ExtraVisualVideoId, videoId)
            context.startService(intent)
        }

        fun hideVisualPositionEditor(context: Context) {
            val intent = Intent(context, UsageWatcherService::class.java)
                .setAction(ActionHideVisualPositionEditor)
            context.startService(intent)
        }
    }
}

private data class VideoOverlaySize(
    val widthPx: Int,
    val heightPx: Int
)

private class FloatingDebugTextView(context: Context) : TextView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private class SessionTimerTextView(context: Context) : TextView(context) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}

private fun String.screenEventLabel(): String {
    return when (this) {
        Intent.ACTION_SCREEN_OFF -> "screen off"
        Intent.ACTION_SCREEN_ON -> "screen on"
        Intent.ACTION_USER_PRESENT -> "user present"
        else -> this
    }
}
