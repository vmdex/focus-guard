# Focus Guard Android Current State

Last updated: 2026-06-28

This document is the recovery point for future Codex chats after context compaction.
It should be updated only when the user asks to capture the current state.

## Product direction

Focus Guard Android v0.1 is an app usage watcher.

The first useful version should notice when the user stays in a distracting app for too long and gently interrupt the autopilot loop.

The current prototype is still developer-friendly and intentionally verbose. The priority is understanding Android foreground-app behavior on a real Pixel 7 before polishing user-facing UI.

## Current Android app location

Main project:

```text
projects/focus-guard-android/app
```

Main package:

```text
com.vmdex.focusguard
```

Important files:

```text
MainActivity.kt
FocusGuardApp.kt
UsageWatcherService.kt
UsageEventReader.kt
SessionEngine.kt
SessionModels.kt
PersistedSessionState.kt
SessionStateStore.kt
WatcherState.kt
WatcherStateStore.kt
FocusGuardSettings.kt
FocusGuardSettingsStore.kt
FocusGuardNotifier.kt
OverlayPermission.kt
InstalledAppProvider.kt
TrackedAppsStore.kt
DebugSettings.kt
DebugSettingsStore.kt
```

## Current architecture

Monitoring is owned by `UsageWatcherService`, not by `MainActivity`.

`MainActivity`:

- loads saved settings and watcher snapshot;
- loads debug settings and selected tracked apps;
- loads launchable apps for app selection UI;
- starts/stops `UsageWatcherService`;
- resumes the service when the saved state says monitoring is on;
- renders the Compose prototype UI.

`UsageWatcherService`:

- runs as a foreground service while monitoring is enabled;
- ticks every second;
- reads foreground-app information through `UsageStatsManager`;
- saves a `WatcherState` snapshot;
- updates the foreground service notification;
- updates the floating debug overlay;
- sends limit-exceeded notifications.

`SessionStateStore` stores the logical session memory.

`WatcherStateStore` stores the UI/debug snapshot.

`UsageEvents` are now used as foreground transition input, not as the whole source of truth for session memory.

Foreground-start detection uses:

- `UsageEvents.Event.ACTIVITY_RESUMED` on Android 10+;
- `UsageEvents.Event.MOVE_TO_FOREGROUND` only as a legacy fallback on Android 8/9.

`SessionEngine` owns the pure session rules. It has no Android service, notification, store, or overlay dependency.

`UsageWatcherService` now acts mostly as wiring:

- reads settings/state/session stores;
- reads selected tracked apps from `TrackedAppsStore`;
- reads foreground transitions;
- calls `SessionEngine`;
- persists the returned session;
- sends Android notifications when the engine returns a limit-alert request;
- derives `InterventionState` for current notification/debug display;
- updates the foreground notification and floating overlay.

## Monitoring on/off semantics

Monitoring off should mean truly idle:

- no watcher tick loop;
- no usage detection refresh loop;
- no session elapsed counting;
- no limit checks;
- no alert notifications;
- no floating debug overlay.

The explicit Stop action saves `isRunning = false`.

Service lifecycle teardown does not mean user stop. `UsageWatcherService.onDestroy()` removes callbacks/overlay/foreground state but does not mark monitoring off. This lets the app resume monitoring after Android Studio re-run or process restart when saved `isRunning = true`.

## Session model

Current strategy: Grace period.

Tracked apps are configurable in the app UI and persisted by `TrackedAppsStore`.

The selection list is built from launchable apps only through `InstalledAppProvider`.

By default, no apps are selected. The user chooses apps through the `Choose apps` screen.

The app still ignores its own package (`com.vmdex.focusguard`) even if it is accidentally selected.

Foreground states:

- `Unknown`;
- `PermissionMissing`;
- `Untracked(packageName)`;
- `Detected(...)` for tracked app sessions.

The active logical session is stored as `PersistedSessionState`:

```text
packageName
sessionKey
sessionStartedAtMillis
sessionElapsedMillis
currentActiveStartedAtMillis
interruptionStartedAtMillis
status
effectiveSettings
alertedSessionKey
lastUpdatedTimeMillis
lastForegroundPackageName
```

## Session timing rules

`sessionElapsedMillis` counts only actual foreground time in a tracked app.

Example:

```text
Chrome active 4 sec
Focus Guard / launcher 10 sec
Chrome active 2 sec
sessionElapsed = 6 sec, not 16 sec
```

During `GracePeriod`, the session is still alive, but `sessionElapsedMillis` is frozen.

`currentActiveElapsedMillis` means how long the currently tracked app has been continuously active since the last foreground return.

`GracePeriod` keeps short interruptions inside the same session:

- leaving the tracked app starts grace;
- returning before grace expires continues the same session;
- not returning before grace expires ends the session.

Recent recents/foreground behavior:

- if the session is in `GracePeriod` and the latest foreground snapshot directly reports the same tracked app again, the session resumes as `Active`;
- the app does not use the aggressive fallback “launcher went to background, so previous tracked app must be active” because that caused false `Active` state when returning to Focus Guard itself;
- known remaining edge case: Android may fail to report a fresh foreground event after some recents flows, so `Chrome -> recents -> Chrome` and `Focus Guard -> recents -> Focus Guard` still need more investigation.

## Alert rules

Limit alert is allowed only when the tracked app is currently active.

Alert conditions:

```text
sessionStatus == Active
sessionElapsedMillis >= sessionLimitMillis
currentActiveElapsedMillis >= alertDelayAfterResumeMillis
alertedSessionKey != current sessionKey
```

`alertDelayAfterResumeMillis` prevents an immediate alert when the user returns to a tracked app and the total session time is already over the limit.

`PersistedSessionState.alertedSessionKey` is the logical duplicate-alert protection for the active session.

`AlertState` is UI/debug history for the last delivered alert:

```text
wasSent
lastAlertTimeMillis
lastAlertPackageName
alertedSessionKey
```

`InterventionState` is current debug/display state for notification behavior:

```text
notificationStatus = NotNeeded / WaitingLimit / WaitingResumeDelay / ReadyToNotify / Sent
notificationLeftMillis
sessionKey
```

This is the first small split between alert history and intervention status. It prepares the code for future repeated notifications, floating overlays, fullscreen interventions, and other intervention types without a large refactor yet.

## Debug overlay

The app has an optional floating debug overlay while monitoring is enabled.

Android still requires the foreground service notification; the overlay is an additional visible debugging aid, not a replacement for the foreground notification.

Overlay permission uses:

```text
SYSTEM_ALERT_WINDOW
Settings.canDrawOverlays
```

Overlay text:

For untracked foreground apps:

```text
Not tracking: chrome
```

For tracked active apps:

```text
Tracking: chrome
Session: 00:12
Limit left: 00:18
Notification left: 00:18
```

If the alert is waiting specifically because of the resume delay:

```text
Notification left: 00:02 (resume delay)
```

After the notification was already delivered for the current session:

```text
Notification sent
```

The overlay is draggable.

The debug overlay is controlled by:

```text
Debug settings -> Floating debug window
```

The label in the UI is currently:

```text
Float window
```

## Session timer overlay

The app also has an optional user-facing session timer overlay.

It is shown only when:

- monitoring is enabled;
- the setting is enabled;
- the current foreground app is the tracked app for the active session;
- overlay permission is granted.

The timer is a draggable circular overlay.

Time format:

```text
mm:ss before one hour
hh:mm:ss after one hour
```

The setting is currently:

```text
Debug settings -> Show session timer
```

## Current UI

The current UI is a developer-friendly Compose prototype.

Main visible cards:

- Usage Access;
- Focus settings;
- Tracked apps;
- Monitoring;
- Dev settings;
- Dev info.

Focus settings currently include:

- grace period seconds;
- session limit seconds;
- alert delay after resume seconds.

The numeric fields allow clearing while editing. If the user leaves a field empty, the last valid value is restored instead of forcing a digit while typing.

Tracked apps card:

- shows `N tracking apps`;
- opens `Choose apps`;
- selected apps are persisted only when the user taps the top check/save action;
- Back/swipe returns without applying draft changes;
- when search is empty, selected apps stay pinned at the top;
- while searching, results are filtered alphabetically by app name.

Dev settings currently include:

- Floating debug window / Float window;
- Show session timer.

Dev info is grouped into sections:

- App;
- Settings;
- Foreground;
- Session;
- Alerts;
- Intervention;
- Actions.

Dev actions:

- Refresh usage data;
- Reset session.

## Reset session behavior

`Reset session` is a dev tool.

After reset:

- `SessionStateStore` is cleared;
- old usage history before reset is ignored when starting a new session without persisted state;
- if the user is already inside a tracked app, its session starts from the reset time;
- if the user is outside the tracked app during grace, the old grace session is discarded;
- alert state is cleared.
- intervention state is cleared.

`sessionResetTimeMillis` is now only a safety cutoff for the no-session bootstrap path, not the main session memory mechanism.

## Tracked apps UI

Configurable tracked apps are implemented for the prototype.

Current behavior:

- `Choose apps` opens a selection screen;
- only launchable apps are listed;
- rows show app name only, no package name;
- every row has a checkbox;
- no apps are selected by default;
- when search is empty, selected apps are pinned at the top and the rest are sorted A-Z;
- when search has text, the list shows only matching app names sorted alphabetically;
- tapping the top save/check action applies the draft and returns to the previous screen;
- if nothing changed, tapping the check action simply returns;
- Back/swipe returns without applying draft changes;
- if an active app is removed from tracked apps and saved, the current session stops being tracked.

Future statistics/history should still be able to show old history for an app even after the app is no longer tracked.

## Battery and performance direction

The app should stay conservative with battery and CPU.

Current direction:

- monitoring off should do no polling at all;
- normal monitoring should use delta-based `UsageEvents` reads after the last processed timestamp;
- the larger lookup window should be only a fallback for cold start, missing state, or recovery;
- tick frequency does not have to stay at 1 second forever;
- untracked foreground apps can probably be polled less frequently later;
- tracked active apps close to their limit can be polled more frequently;
- the floating overlay is currently a debug tool and can update every second during development;
- release behavior should make the overlay optional or less frequent;
- background logic should prefer timestamps and persisted session state over recomputing long event history.

## Important future work

Recommended next technical step:

- continue investigating recents/foreground edge cases on Pixel 7, especially where Android does not report a fresh foreground event after returning from recents;
- decide whether the timer overlay should use a different foreground source or a more explicit “currently visible tracked app” signal.

Other planned work:

- statistics/history for tracked apps;
- stronger process-restart behavior;
- battery optimization strategy;
- better foreground detection edge-case testing;
- polished setup flow.

## Testing notes

The main session logic now has JVM unit tests in:

```text
app/src/test/java/com/vmdex/focusguard/SessionEngineTest.kt
```

The tests simulate foreground transitions and timestamps directly. Covered scenarios:

- session elapsed grows only while a tracked app is active;
- grace freezes elapsed;
- returning before grace expires continues the session;
- returning from recents resumes the session when the latest foreground snapshot directly reports the tracked app again;
- grace expiry ends the session;
- limit alert fires once per session;
- alert waits for `alertDelayAfterResumeMillis` after returning;
- intervention state waits for limit, waits for resume delay, and shows sent after notification delivery;
- floating overlay formatter shows `Notification sent` after delivery and does not show resume delay for an already-alerted session;
- an already-alerted session does not repeat alert after leaving and returning during grace;
- sessions stay stable across interruption/grace return and do not count untracked time;
- effective settings stay attached to an existing session;
- no previous session with tracked foreground starts fresh at the current time.
- configurable tracked app selection ordering/search behavior;
- session timer format uses `mm:ss` before one hour and `hh:mm:ss` after one hour.

Useful manual test settings:

```text
Session limit seconds = 5
Grace period seconds = 10
Alert delay after resume seconds = 2
```

Useful scenarios:

- tracked app active until limit -> notification appears;
- leave tracked app before limit -> no notification during grace;
- return to tracked app after limit -> notification waits for resume delay;
- notification should not repeat for the same session;
- Stop monitoring removes overlay and stops usage refresh;
- Android Studio re-run should restore monitoring and overlay if monitoring was on.

## Build notes

Known useful command:

```text
.\gradlew.bat assembleDebug
```

Known useful test command:

```text
.\gradlew.bat testDebugUnitTest
```

Both `testDebugUnitTest` and `assembleDebug` passed after the recent cleanup. Do not run Gradle test/build tasks in parallel in this repo; run them sequentially to avoid Kotlin incremental cache issues.

## Git status note

Recent Android commits include:

```text
238786c Resume session from latest tracked foreground
48d7a49 Add session timer overlay
c99de42 Add floating debug window setting
0cd8960 Rename dev settings to focus settings
e7231c3 Move monitoring card below tracked apps
58968d7 Allow clearing Android settings fields while editing
4b97747 Reorder Android main screen cards
5787fe2 Keep choose apps order stable while editing
7935c69 Add configurable tracked apps UI
f7df1f8 Expand Android intervention tests
a8670e8 Split Android alert and intervention state
7f1c35d Address Android IDE cleanup suggestions
6ded2b1 Modernize Android foreground usage events
```

Earlier Android commits include:

```text
3af0ece Group Android dev info sections
ebf4e95 Expand Android session debug info
1e84905 Resume Android monitoring after app restart
c901319 Add Android dev session reset
eb22452 Refine Android debug overlay status
5464825 Add Android monitoring debug overlay
3ae033d Refine Android grace alert timing
```

The user prefers local commits after completed logical steps and does not push to GitHub unless explicitly requested.
