package com.vmdex.focusguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SessionEngineTest {
    private val engine = SessionEngine(
        trackedAppPackages = setOf(ChromePackage),
        ignoredPackageName = FocusGuardPackage
    )

    private val settings = FocusGuardSettings(
        sessionLimitMillis = 5_000L,
        gracePeriodMillis = 10_000L,
        alertDelayAfterResumeMillis = 2_000L
    )

    // Перевіряємо, що session elapsed росте тільки поки tracked app активний.
    @Test
    fun sessionElapsedGrowsOnlyWhileTrackedAppIsActive() {
        val result = engine.buildNextSession(
            previousSession = null,
            snapshot = snapshot(
                lastForegroundPackageName = LauncherPackage,
                transition(ChromePackage, 1_000L),
                transition(LauncherPackage, 4_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 7_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.GracePeriod, session.status)
        assertEquals(3_000L, session.sessionElapsedMillis)
        assertEquals(3_000L, session.appElapsedMillis[ChromePackage])
        assertEquals(0L, session.currentActiveElapsedMillis)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що повернення до tracked app до завершення grace продовжує ту саму session.
    @Test
    fun sessionContinuesWhenReturningBeforeGraceExpires() {
        val graceSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = LauncherPackage,
                    transition(ChromePackage, 1_000L),
                    transition(LauncherPackage, 4_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 7_000L
            ).session
        )

        val result = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(
                lastForegroundPackageName = ChromePackage,
                transition(ChromePackage, 8_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 9_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.Active, session.status)
        assertEquals(4_000L, session.sessionElapsedMillis)
        assertEquals(1_000L, session.currentActiveElapsedMillis)
    }

    // Перевіряємо, що повернення з recents відновлює active session навіть без нового foreground transition.
    @Test
    fun sessionResumesFromLatestForegroundWhenReturnHasNoTransition() {
        val graceSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = LauncherPackage,
                    transition(ChromePackage, 1_000L),
                    transition(LauncherPackage, 4_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 7_000L
            ).session
        )

        val result = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = settings,
            currentTimeMillis = 9_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.Active, session.status)
        assertEquals(graceSession.sessionKey, session.sessionKey)
        assertEquals(3_000L, session.sessionElapsedMillis)
        assertEquals(0L, session.currentActiveElapsedMillis)
        assertEquals(9_000L, session.currentActiveStartedAtMillis)
    }

    // Перевіряємо, що session завершується, якщо користувач не повернувся до tracked app до кінця grace.
    @Test
    fun sessionEndsAfterGraceExpires() {
        val graceSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = LauncherPackage,
                    transition(ChromePackage, 1_000L),
                    transition(LauncherPackage, 4_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 7_000L
            ).session
        )

        val result = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(lastForegroundPackageName = LauncherPackage),
            savedSettings = settings,
            currentTimeMillis = 14_001L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.Ended, session.status)
        assertEquals(3_000L, session.sessionElapsedMillis)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що alert створюється після limit і не дублюється для тієї самої session.
    @Test
    fun alertFiresOnceAfterLimit() {
        val firstResult = engine.buildNextSession(
            previousSession = null,
            snapshot = snapshot(
                lastForegroundPackageName = ChromePackage,
                transition(ChromePackage, 1_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 7_000L
        )

        val firstSession = requireNotNull(firstResult.session)
        val firstAlert = requireNotNull(firstResult.limitAlertRequest)
        assertEquals(firstSession.sessionKey, firstAlert.sessionKey)

        val alertedSession = firstSession.copy(alertedSessionKey = firstSession.sessionKey)
        val secondResult = engine.buildNextSession(
            previousSession = alertedSession,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = settings,
            currentTimeMillis = 8_000L
        )

        assertNull(secondResult.limitAlertRequest)
        assertEquals(7_000L, requireNotNull(secondResult.session).sessionElapsedMillis)
    }

    // Перевіряємо, що після повернення в already-over-limit session alert чекає resume delay.
    @Test
    fun alertWaitsForResumeDelayAfterReturningOverLimit() {
        val graceSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = LauncherPackage,
                    transition(ChromePackage, 1_000L),
                    transition(LauncherPackage, 7_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 8_000L
            ).session
        )

        val beforeDelayResult = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(
                lastForegroundPackageName = ChromePackage,
                transition(ChromePackage, 8_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 9_000L
        )

        assertNull(beforeDelayResult.limitAlertRequest)

        val afterDelayResult = engine.buildNextSession(
            previousSession = beforeDelayResult.session,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = settings,
            currentTimeMillis = 10_000L
        )

        assertNotNull(afterDelayResult.limitAlertRequest)
    }

    // Перевіряємо, що активна session зберігає свої effective settings навіть якщо saved settings змінились.
    @Test
    fun existingSessionKeepsEffectiveSettings() {
        val activeSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = ChromePackage,
                    transition(ChromePackage, 1_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 2_000L
            ).session
        )

        val changedSettings = settings.copy(sessionLimitMillis = 1_000L)
        val result = engine.buildNextSession(
            previousSession = activeSession,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = changedSettings,
            currentTimeMillis = 4_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(settings, session.effectiveSettings)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що без persisted session tracked foreground app стартує нову session з поточного часу.
    @Test
    fun noPreviousSessionWithTrackedForegroundStartsFreshAtCurrentTime() {
        val result = engine.buildNextSession(
            previousSession = null,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = settings,
            currentTimeMillis = 20_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(20_000L, session.sessionStartedAtMillis)
        assertEquals(0L, session.sessionElapsedMillis)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що перехід з одного tracked app в інший продовжує ту саму session.
    @Test
    fun switchingToAnotherTrackedAppContinuesSameSession() {
        val engineWithTwoTrackedApps = SessionEngine(
            trackedAppPackages = setOf(ChromePackage, TwitchPackage),
            ignoredPackageName = FocusGuardPackage
        )

        val result = engineWithTwoTrackedApps.buildNextSession(
            previousSession = null,
            snapshot = snapshot(
                lastForegroundPackageName = TwitchPackage,
                transition(ChromePackage, 1_000L),
                transition(TwitchPackage, 4_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 5_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(TwitchPackage, session.packageName)
        assertEquals(1_000L, session.sessionStartedAtMillis)
        assertEquals("tracked:1000", session.sessionKey)
        assertEquals(4_000L, session.sessionElapsedMillis)
        assertEquals(3_000L, session.appElapsedMillis[ChromePackage])
        assertEquals(1_000L, session.appElapsedMillis[TwitchPackage])
        assertEquals(4_000L, session.currentActiveElapsedMillis)
    }

    // Перевіряємо, що Focus Guard не трекається сам себе, навіть якщо випадково є у tracked packages.
    @Test
    fun ignoredPackageDoesNotStartSession() {
        val engineWithSelfInTrackedApps = SessionEngine(
            trackedAppPackages = setOf(FocusGuardPackage),
            ignoredPackageName = FocusGuardPackage
        )

        val result = engineWithSelfInTrackedApps.buildNextSession(
            previousSession = null,
            snapshot = snapshot(
                lastForegroundPackageName = FocusGuardPackage,
                transition(FocusGuardPackage, 1_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 3_000L
        )

        assertNull(result.session)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що transitions обробляються за timestamp, навіть якщо прийшли не по порядку.
    @Test
    fun unorderedTransitionsAreSortedByTimestamp() {
        val result = engine.buildNextSession(
            previousSession = null,
            snapshot = snapshot(
                lastForegroundPackageName = LauncherPackage,
                transition(LauncherPackage, 4_000L),
                transition(ChromePackage, 1_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 7_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.GracePeriod, session.status)
        assertEquals(3_000L, session.sessionElapsedMillis)
        assertEquals(4_000L, session.interruptionStartedAtMillis)
    }

    // Перевіряємо, що якщо app прибрали з tracked packages, його active session більше не продовжується.
    @Test
    fun removingAppFromTrackedPackagesEndsExistingSession() {
        val activeSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = ChromePackage,
                    transition(ChromePackage, 1_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 3_000L
            ).session
        )
        val engineWithoutTrackedApps = SessionEngine(
            trackedAppPackages = emptySet(),
            ignoredPackageName = FocusGuardPackage
        )

        val result = engineWithoutTrackedApps.buildNextSession(
            previousSession = activeSession,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = settings,
            currentTimeMillis = 5_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.Ended, session.status)
        assertEquals(2_000L, session.sessionElapsedMillis)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що intervention state показує очікування limit, коли session ще не дійшла до ліміту.
    @Test
    fun interventionWaitsForLimitBeforeSessionLimit() {
        val session = activeSession(
            sessionElapsedMillis = 3_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 4_000L
        )

        val interventionState = interventionStateForSession(session)

        assertEquals(InterventionNotificationStatus.WaitingLimit, interventionState.notificationStatus)
        assertEquals(2_000L, interventionState.notificationLeftMillis)
        assertEquals(session.sessionKey, interventionState.sessionKey)
    }

    // Перевіряємо, що intervention state показує resume delay, якщо session вже over limit, але користувач щойно повернувся.
    @Test
    fun interventionWaitsForResumeDelayWhenOverLimitAfterReturn() {
        val session = activeSession(
            sessionElapsedMillis = 6_000L,
            currentActiveStartedAtMillis = 8_000L,
            lastUpdatedTimeMillis = 9_000L
        )

        val interventionState = interventionStateForSession(session)

        assertEquals(InterventionNotificationStatus.WaitingResumeDelay, interventionState.notificationStatus)
        assertEquals(1_000L, interventionState.notificationLeftMillis)
    }

    // Перевіряємо, що intervention state показує sent, якщо notification вже відправлений для цієї session.
    @Test
    fun interventionShowsSentAfterSessionWasAlerted() {
        val session = activeSession(
            sessionElapsedMillis = 7_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 8_000L
        ).let { it.copy(alertedSessionKey = it.sessionKey) }

        val interventionState = interventionStateForSession(session)

        assertEquals(InterventionNotificationStatus.Sent, interventionState.notificationStatus)
        assertNull(interventionState.notificationLeftMillis)
    }

    // Перевіряємо, що alert не повторюється в тій самій session навіть після виходу і повернення через grace.
    @Test
    fun alertDoesNotRepeatAfterReturningToAlreadyAlertedSession() {
        val alertedActiveSession = activeSession(
            sessionElapsedMillis = 7_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 8_000L
        ).let { it.copy(alertedSessionKey = it.sessionKey) }

        val graceResult = engine.buildNextSession(
            previousSession = alertedActiveSession,
            snapshot = snapshot(
                lastForegroundPackageName = LauncherPackage,
                transition(LauncherPackage, 9_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 10_000L
        )
        val graceSession = requireNotNull(graceResult.session)
        assertEquals(SessionStatus.GracePeriod, graceSession.status)
        assertNull(graceResult.limitAlertRequest)

        val returnedResult = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(
                lastForegroundPackageName = ChromePackage,
                transition(ChromePackage, 11_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 14_000L
        )

        val returnedSession = requireNotNull(returnedResult.session)
        assertEquals(SessionStatus.Active, returnedSession.status)
        assertEquals(alertedActiveSession.sessionKey, returnedSession.alertedSessionKey)
        assertNull(returnedResult.limitAlertRequest)
    }

    // Перевіряємо, що після sent overlay показує саме Notification sent.
    @Test
    fun floatingOverlayShowsNotificationSentAfterAlert() {
        val session = activeSession(
            sessionElapsedMillis = 7_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 8_000L
        ).let { it.copy(alertedSessionKey = it.sessionKey) }
        val overlayText = floatingDebugTextForState(watcherStateForSession(session))

        assertEquals(
            """
            Tracking: chrome
            Session: 00:07
            Limit left: 00:00
            Notification sent
            """.trimIndent(),
            overlayText
        )
    }

    // Перевіряємо, що resume delay не показується в overlay, якщо notification уже sent для session.
    @Test
    fun floatingOverlayDoesNotShowResumeDelayAfterNotificationWasSent() {
        val session = activeSession(
            sessionElapsedMillis = 7_000L,
            currentActiveStartedAtMillis = 12_000L,
            lastUpdatedTimeMillis = 13_000L
        ).let { it.copy(alertedSessionKey = it.sessionKey) }
        val overlayText = floatingDebugTextForState(watcherStateForSession(session))

        assertEquals(InterventionNotificationStatus.Sent, interventionStateForSession(session).notificationStatus)
        assertFalse(overlayText.contains("resume delay"))
        assertFalse(overlayText.contains("Notification left"))
    }

    // Перевіряємо формат timer widget: до години mm:ss, після години hh:mm:ss.
    @Test
    fun sessionTimerUsesHoursOnlyAfterOneHour() {
        assertEquals("59:59", formatSessionTimer(3_599_000L))
        assertEquals("01:00:00", formatSessionTimer(3_600_000L))
    }

    // Перевіряємо, що session стабільно переживає interruption/grace і не рахує elapsed поза tracked app.
    @Test
    fun sessionStaysStableAcrossInterruptionAndGraceReturn() {
        val graceResult = engine.buildNextSession(
            previousSession = null,
            snapshot = snapshot(
                lastForegroundPackageName = LauncherPackage,
                transition(ChromePackage, 1_000L),
                transition(LauncherPackage, 4_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 9_000L
        )
        val graceSession = requireNotNull(graceResult.session)
        assertEquals(SessionStatus.GracePeriod, graceSession.status)
        assertEquals(3_000L, graceSession.sessionElapsedMillis)

        val returnedResult = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(
                lastForegroundPackageName = ChromePackage,
                transition(ChromePackage, 10_000L)
            ),
            savedSettings = settings,
            currentTimeMillis = 12_000L
        )

        val returnedSession = requireNotNull(returnedResult.session)
        assertEquals(SessionStatus.Active, returnedSession.status)
        assertEquals(5_000L, returnedSession.sessionElapsedMillis)
        assertEquals(2_000L, returnedSession.currentActiveElapsedMillis)
        assertEquals(graceSession.sessionKey, returnedSession.sessionKey)
    }

    // Перевіряємо, що lock screen ставить active session на паузу і не запускає grace.
    @Test
    fun screenLockPausesActiveSessionWithoutStartingGrace() {
        val activeSession = requireNotNull(
            engine.buildNextSession(
                previousSession = null,
                snapshot = snapshot(
                    lastForegroundPackageName = ChromePackage,
                    transition(ChromePackage, 1_000L)
                ),
                savedSettings = settings,
                currentTimeMillis = 4_000L
            ).session
        )

        val result = engine.buildNextSession(
            previousSession = activeSession,
            snapshot = snapshot(lastForegroundPackageName = LauncherPackage),
            savedSettings = settings,
            currentTimeMillis = 6_000L,
            isScreenLocked = true
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.PausedByScreenLock, session.status)
        assertEquals(5_000L, session.sessionElapsedMillis)
        assertNull(session.interruptionStartedAtMillis)
        assertNull(result.limitAlertRequest)
    }

    // Перевіряємо, що lock screen під час grace заморожує session і не дає grace завершитись.
    @Test
    fun screenLockPausesGraceSessionWithoutExpiringGrace() {
        val graceSession = activeSession(
            sessionElapsedMillis = 5_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 6_000L
        ).copy(
            status = SessionStatus.GracePeriod,
            currentActiveStartedAtMillis = null,
            interruptionStartedAtMillis = 6_000L,
            lastForegroundPackageName = LauncherPackage
        )

        val result = engine.buildNextSession(
            previousSession = graceSession,
            snapshot = snapshot(lastForegroundPackageName = LauncherPackage),
            savedSettings = settings,
            currentTimeMillis = 60_000L,
            isScreenLocked = true
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.PausedByScreenLock, session.status)
        assertEquals(5_000L, session.sessionElapsedMillis)
        assertNull(session.interruptionStartedAtMillis)
    }

    // Перевіряємо, що після unlock у tracked app session продовжується без рахування часу lock screen.
    @Test
    fun unlockToTrackedAppResumesPausedSession() {
        val pausedSession = activeSession(
            sessionElapsedMillis = 5_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 6_000L
        ).copy(
            status = SessionStatus.PausedByScreenLock,
            currentActiveStartedAtMillis = null
        )

        val result = engine.buildNextSession(
            previousSession = pausedSession,
            snapshot = snapshot(lastForegroundPackageName = ChromePackage),
            savedSettings = settings,
            currentTimeMillis = 66_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.Active, session.status)
        assertEquals(5_000L, session.sessionElapsedMillis)
        assertEquals(0L, session.currentActiveElapsedMillis)
        assertEquals(66_000L, session.currentActiveStartedAtMillis)
    }

    // Перевіряємо, що після unlock у non-tracked app paused session переходить у grace з нового моменту.
    @Test
    fun unlockToUntrackedAppStartsGraceAfterPausedSession() {
        val pausedSession = activeSession(
            sessionElapsedMillis = 5_000L,
            currentActiveStartedAtMillis = 1_000L,
            lastUpdatedTimeMillis = 6_000L
        ).copy(
            status = SessionStatus.PausedByScreenLock,
            currentActiveStartedAtMillis = null
        )

        val result = engine.buildNextSession(
            previousSession = pausedSession,
            snapshot = snapshot(lastForegroundPackageName = LauncherPackage),
            savedSettings = settings,
            currentTimeMillis = 66_000L
        )

        val session = requireNotNull(result.session)
        assertEquals(SessionStatus.GracePeriod, session.status)
        assertEquals(5_000L, session.sessionElapsedMillis)
        assertEquals(66_000L, session.interruptionStartedAtMillis)
    }

    private fun snapshot(
        lastForegroundPackageName: String?,
        vararg transitions: ForegroundTransition
    ): ForegroundUsageSnapshot {
        return ForegroundUsageSnapshot(
            lastForegroundPackageName = lastForegroundPackageName,
            transitions = transitions.toList()
        )
    }

    private fun transition(packageName: String, timestampMillis: Long): ForegroundTransition {
        return ForegroundTransition(
            packageName = packageName,
            className = null,
            eventType = 1,
            timestampMillis = timestampMillis
        )
    }

    private fun activeSession(
        sessionElapsedMillis: Long,
        currentActiveStartedAtMillis: Long,
        lastUpdatedTimeMillis: Long
    ): PersistedSessionState {
        return PersistedSessionState(
            packageName = ChromePackage,
            sessionStartedAtMillis = 1_000L,
            sessionElapsedMillis = sessionElapsedMillis,
            appElapsedMillis = mapOf(ChromePackage to sessionElapsedMillis),
            currentActiveStartedAtMillis = currentActiveStartedAtMillis,
            status = SessionStatus.Active,
            effectiveSettings = settings,
            lastUpdatedTimeMillis = lastUpdatedTimeMillis,
            lastForegroundPackageName = ChromePackage
        )
    }

    private fun watcherStateForSession(session: PersistedSessionState): WatcherState {
        return WatcherState(
            isRunning = true,
            foregroundAppState = ForegroundAppState.Detected(
                packageName = session.packageName,
                className = session.className,
                eventType = session.eventType,
                timestampMillis = session.sessionStartedAtMillis,
                isTracked = true,
                sessionStatus = session.status,
                lastForegroundPackageName = session.lastForegroundPackageName,
                interruptionStartedAtMillis = session.interruptionStartedAtMillis,
                sessionElapsedMillis = session.sessionElapsedMillis,
                appElapsedMillis = session.appElapsedMillis,
                currentActiveElapsedMillis = session.currentActiveElapsedMillis,
                isAlertSentForSession = session.alertedSessionKey == session.sessionKey
            ),
            interventionState = interventionStateForSession(session),
            effectiveSettings = session.effectiveSettings
        )
    }

    private companion object {
        const val ChromePackage = "com.android.chrome"
        const val FocusGuardPackage = "com.vmdex.focusguard"
        const val LauncherPackage = "com.android.launcher"
        const val TwitchPackage = "tv.twitch.android.app"
    }
}
