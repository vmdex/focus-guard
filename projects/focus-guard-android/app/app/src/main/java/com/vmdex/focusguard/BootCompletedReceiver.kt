package com.vmdex.focusguard

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        val stateStore = WatcherStateStore(context)
        val savedState = stateStore.load()
        if (!savedState.isRunning) {
            stateStore.save(
                savedState.copy(
                    serviceRestoreState = bootRestoreState(
                        reason = "boot resume skipped",
                        session = null
                    )
                )
            )
            return
        }

        val session = SessionStateStore(context).load()
        stateStore.save(
            savedState.copy(
                serviceRestoreState = bootRestoreState(
                    reason = "boot resume pending",
                    session = session
                )
            )
        )

        FocusGuardNotifier(context).run {
            createBootResumeChannel()
            showResumeMonitoringNeeded()
        }
    }

    private fun bootRestoreState(
        reason: String,
        session: PersistedSessionState?
    ): ServiceRestoreState {
        return ServiceRestoreState(
            serviceStartReason = reason,
            serviceStartTimeMillis = System.currentTimeMillis(),
            restoredSessionKey = session?.sessionKey,
            restoredSessionStatus = session?.status,
            restoredSessionElapsedMillis = session?.sessionElapsedMillis
        )
    }
}
