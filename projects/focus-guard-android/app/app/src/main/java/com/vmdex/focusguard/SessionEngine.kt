package com.vmdex.focusguard

class SessionEngine(
    private val trackedAppPackages: Set<String>,
    private val ignoredPackageName: String
) {
    fun buildNextSession(
        previousSession: PersistedSessionState?,
        snapshot: ForegroundUsageSnapshot,
        savedSettings: FocusGuardSettings,
        currentTimeMillis: Long,
        isScreenLocked: Boolean = false
    ): SessionEngineResult {
        var session = previousSession?.stopIfNoLongerTracked(currentTimeMillis, snapshot.lastForegroundPackageName)
        if (isScreenLocked) {
            session = pauseForScreenLock(session, currentTimeMillis)
            return SessionEngineResult(
                session = session,
                limitAlertRequest = null
            )
        }

        for (transition in snapshot.transitions.sortedBy { it.timestampMillis }) {
            session = advanceSessionClock(session, transition.timestampMillis)
            session = applyForegroundTransition(
                session = session,
                transition = transition,
                savedSettings = savedSettings
            )
        }

        session = advanceSessionClock(session, currentTimeMillis)
        session = reconcileLatestForegroundApp(
            session = session,
            snapshot = snapshot,
            savedSettings = savedSettings,
            currentTimeMillis = currentTimeMillis
        )

        if (session == null && snapshot.lastForegroundPackageName?.isTrackedApp() == true) {
            session = startSession(
                transition = ForegroundTransition(
                    packageName = snapshot.lastForegroundPackageName,
                    className = null,
                    eventType = 0,
                    timestampMillis = currentTimeMillis
                ),
                settings = savedSettings
            )
            session = advanceSessionClock(session, currentTimeMillis)
        }

        return SessionEngineResult(
            session = session,
            limitAlertRequest = limitAlertRequest(session)
        )
    }

    private fun reconcileLatestForegroundApp(
        session: PersistedSessionState?,
        snapshot: ForegroundUsageSnapshot,
        savedSettings: FocusGuardSettings,
        currentTimeMillis: Long
    ): PersistedSessionState? {
        val latestForegroundPackageName = snapshot.lastForegroundPackageName
        if (latestForegroundPackageName == null) {
            return session
        }

        if (session == null) {
            return session
        }

        if (!latestForegroundPackageName.isTrackedApp()) {
            return if (session.status == SessionStatus.PausedByScreenLock) {
                session.copy(
                    status = SessionStatus.GracePeriod,
                    currentActiveStartedAtMillis = null,
                    interruptionStartedAtMillis = currentTimeMillis,
                    lastForegroundPackageName = latestForegroundPackageName,
                    lastUpdatedTimeMillis = currentTimeMillis
                )
            } else {
                session
            }
        }

        return when (session.status) {
            SessionStatus.Active -> session.copy(
                packageName = latestForegroundPackageName,
                lastForegroundPackageName = latestForegroundPackageName,
                lastUpdatedTimeMillis = currentTimeMillis
            )
            SessionStatus.GracePeriod -> session.copy(
                packageName = latestForegroundPackageName,
                status = SessionStatus.Active,
                currentActiveStartedAtMillis = currentTimeMillis,
                interruptionStartedAtMillis = null,
                lastForegroundPackageName = latestForegroundPackageName,
                lastUpdatedTimeMillis = currentTimeMillis
            )
            SessionStatus.PausedByScreenLock -> session.copy(
                packageName = latestForegroundPackageName,
                status = SessionStatus.Active,
                currentActiveStartedAtMillis = currentTimeMillis,
                interruptionStartedAtMillis = null,
                lastForegroundPackageName = latestForegroundPackageName,
                lastUpdatedTimeMillis = currentTimeMillis
            )
            SessionStatus.Ended -> startSession(
                transition = ForegroundTransition(
                    packageName = latestForegroundPackageName,
                    className = null,
                    eventType = 0,
                    timestampMillis = currentTimeMillis
                ),
                settings = savedSettings
            )
        }
    }

    private fun applyForegroundTransition(
        session: PersistedSessionState?,
        transition: ForegroundTransition,
        savedSettings: FocusGuardSettings
    ): PersistedSessionState? {
        if (transition.packageName.isTrackedApp()) {
            if (session == null ||
                session.status == SessionStatus.Ended
            ) {
                return startSession(transition, savedSettings)
            }

            if (session.status == SessionStatus.PausedByScreenLock) {
                return session.copy(
                    packageName = transition.packageName,
                    status = SessionStatus.Active,
                    currentActiveStartedAtMillis = transition.timestampMillis,
                    interruptionStartedAtMillis = null,
                    className = transition.className,
                    eventType = transition.eventType,
                    lastForegroundPackageName = transition.packageName,
                    lastUpdatedTimeMillis = transition.timestampMillis
                )
            }

            return session.copy(
                packageName = transition.packageName,
                status = SessionStatus.Active,
                currentActiveStartedAtMillis = session.currentActiveStartedAtMillis ?: transition.timestampMillis,
                interruptionStartedAtMillis = null,
                className = transition.className,
                eventType = transition.eventType,
                lastForegroundPackageName = transition.packageName,
                lastUpdatedTimeMillis = transition.timestampMillis
            )
        }

        if (session == null) {
            return null
        }

        return when (session.status) {
            SessionStatus.Active -> session.copy(
                status = SessionStatus.GracePeriod,
                currentActiveStartedAtMillis = null,
                interruptionStartedAtMillis = transition.timestampMillis,
                lastForegroundPackageName = transition.packageName,
                lastUpdatedTimeMillis = transition.timestampMillis
            )

            SessionStatus.PausedByScreenLock -> session.copy(
                status = SessionStatus.GracePeriod,
                currentActiveStartedAtMillis = null,
                interruptionStartedAtMillis = transition.timestampMillis,
                lastForegroundPackageName = transition.packageName,
                lastUpdatedTimeMillis = transition.timestampMillis
            )

            else -> session.copy(
                lastForegroundPackageName = transition.packageName,
                lastUpdatedTimeMillis = transition.timestampMillis
            )
        }
    }

    private fun advanceSessionClock(
        session: PersistedSessionState?,
        currentTimeMillis: Long
    ): PersistedSessionState? {
        if (session == null || currentTimeMillis < session.lastUpdatedTimeMillis) {
            return session
        }

        return when (session.status) {
            SessionStatus.Active -> {
                val elapsedDelta = currentTimeMillis - session.lastUpdatedTimeMillis
                session.copy(
                    sessionElapsedMillis = session.sessionElapsedMillis + elapsedDelta,
                    appElapsedMillis = session.appElapsedMillis.plusElapsed(
                        packageName = session.packageName,
                        elapsedDelta = elapsedDelta
                    ),
                    lastUpdatedTimeMillis = currentTimeMillis
                )
            }

            SessionStatus.GracePeriod -> {
                val interruptionStartedAt = session.interruptionStartedAtMillis
                val hasGraceExpired = interruptionStartedAt != null &&
                    currentTimeMillis - interruptionStartedAt > session.effectiveSettings.gracePeriodMillis

                session.copy(
                    status = if (hasGraceExpired) SessionStatus.Ended else SessionStatus.GracePeriod,
                    lastUpdatedTimeMillis = currentTimeMillis
                )
            }

            SessionStatus.PausedByScreenLock -> session.copy(lastUpdatedTimeMillis = currentTimeMillis)

            SessionStatus.Ended -> session.copy(lastUpdatedTimeMillis = currentTimeMillis)
        }
    }

    private fun pauseForScreenLock(
        session: PersistedSessionState?,
        currentTimeMillis: Long
    ): PersistedSessionState? {
        if (session == null || session.status == SessionStatus.Ended) {
            return session
        }

        val sessionToPause = if (session.status == SessionStatus.Active) {
            advanceSessionClock(session, currentTimeMillis) ?: session
        } else {
            session
        }

        if (sessionToPause.status == SessionStatus.Ended) {
            return sessionToPause
        }

        return sessionToPause.copy(
            status = SessionStatus.PausedByScreenLock,
            currentActiveStartedAtMillis = null,
            interruptionStartedAtMillis = null,
            lastUpdatedTimeMillis = currentTimeMillis
        )
    }

    private fun startSession(
        transition: ForegroundTransition,
        settings: FocusGuardSettings
    ): PersistedSessionState {
        return PersistedSessionState(
            packageName = transition.packageName,
            sessionStartedAtMillis = transition.timestampMillis,
            appElapsedMillis = mapOf(transition.packageName to 0L),
            currentActiveStartedAtMillis = transition.timestampMillis,
            status = SessionStatus.Active,
            effectiveSettings = settings,
            lastUpdatedTimeMillis = transition.timestampMillis,
            className = transition.className,
            eventType = transition.eventType,
            lastForegroundPackageName = transition.packageName
        )
    }

    private fun limitAlertRequest(session: PersistedSessionState?): LimitAlertRequest? {
        if (session == null || session.status != SessionStatus.Active) {
            return null
        }

        if (session.sessionElapsedMillis < session.effectiveSettings.sessionLimitMillis ||
            session.currentActiveElapsedMillis < session.effectiveSettings.alertDelayAfterResumeMillis ||
            session.alertedSessionKey == session.sessionKey
        ) {
            return null
        }

        return LimitAlertRequest(
            packageName = session.packageName,
            sessionElapsedMillis = session.sessionElapsedMillis,
            sessionKey = session.sessionKey
        )
    }

    private fun String.isTrackedApp(): Boolean {
        return this != ignoredPackageName && this in trackedAppPackages
    }

    private fun PersistedSessionState.stopIfNoLongerTracked(
        currentTimeMillis: Long,
        lastForegroundPackageName: String?
    ): PersistedSessionState {
        if (packageName.isTrackedApp()) {
            return this
        }

        return copy(
            status = SessionStatus.Ended,
            currentActiveStartedAtMillis = null,
            interruptionStartedAtMillis = null,
            lastForegroundPackageName = lastForegroundPackageName ?: this.lastForegroundPackageName,
            lastUpdatedTimeMillis = currentTimeMillis
        )
    }

    private fun Map<String, Long>.plusElapsed(
        packageName: String,
        elapsedDelta: Long
    ): Map<String, Long> {
        if (elapsedDelta <= 0L) {
            return this
        }

        return this + (packageName to ((this[packageName] ?: 0L) + elapsedDelta))
    }
}

data class SessionEngineResult(
    val session: PersistedSessionState?,
    val limitAlertRequest: LimitAlertRequest?
)

data class LimitAlertRequest(
    val packageName: String,
    val sessionElapsedMillis: Long,
    val sessionKey: String
)
