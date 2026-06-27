# Focus Guard Android Current State

Last updated: 2026-06-27

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
SessionModels.kt
WatcherState.kt
WatcherStateStore.kt
FocusGuardSettings.kt
FocusGuardSettingsStore.kt
FocusGuardNotifier.kt
OverlayPermission.kt
```

## Current architecture

Monitoring is owned by `UsageWatcherService`, not by `MainActivity`.

`MainActivity`:

- loads saved settings and watcher snapshot;
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

`WatcherStateStore` is currently both UI snapshot storage and some lightweight debug/session control storage.

Important limitation: session state is still reconstructed from Android `UsageEvents` on each tick. A more explicit persisted session model is a future step.

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

Tracked apps are still hardcoded in `TrackedAppPackages` inside `FocusGuardSettings.kt`.

Current tracked packages:

```text
com.google.android.youtube
com.android.chrome
com.chrome.beta
tv.twitch.android.app
```

Foreground states:

- `Unknown`;
- `PermissionMissing`;
- `Untracked(packageName)`;
- `Detected(...)` for tracked app sessions.

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

`AlertState.alertedSessionKey` is persisted so the UI can show which session was already alerted and the service can restore duplicate-alert protection after restart.

## Debug overlay

The app has a floating debug overlay while monitoring is enabled.

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

The overlay is draggable.

## Current UI

The current UI is a developer-friendly Compose prototype.

Main visible cards:

- Usage Access;
- Floating debug window;
- Monitoring;
- Dev settings;
- Dev info.

Dev settings currently include:

- grace period seconds;
- session limit seconds;
- alert delay after resume seconds.

Dev info is grouped into sections:

- App;
- Settings;
- Foreground;
- Session;
- Alerts;
- Actions.

Dev actions:

- Refresh usage data;
- Reset session.

## Reset session behavior

`Reset session` is a dev tool.

Because the current session is reconstructed from Android `UsageEvents`, reset uses `sessionResetTimeMillis` as a cutoff.

After reset:

- old usage history before reset is ignored;
- if the user is already inside a tracked app, its session starts from the reset time;
- if the user is outside the tracked app during grace, the old grace session is discarded;
- alert state is cleared.

This is a temporary dev mechanism. A future explicit `PersistedSessionState` should make reset cleaner.

## Deferred UI: choose tracked apps

Configurable tracked apps are intentionally postponed until UI work.

Current note in `CONCEPT.md`:

- add a `Choose apps` action;
- show `N tracking apps` above it;
- list launchable apps only;
- show app names with checkboxes;
- search filters by app name;
- selected apps are pinned at the top only when search is empty;
- checkmark applies changes and returns to the previous screen;
- Back/swipe returns without applying changes.

## Important future work

Recommended next technical step:

- create a real `PersistedSessionState` so session memory is not reconstructed only from `UsageEvents`.

Future session persistence should probably store:

```text
packageName
sessionKey
sessionElapsedMillis
currentActiveStartedAtMillis
interruptionStartedAtMillis
status
effectiveSettings
alertedSessionKey
```

Other planned work:

- configurable tracked apps UI;
- statistics/history for tracked apps;
- stronger process-restart behavior;
- battery optimization strategy;
- better foreground detection edge-case testing;
- polished setup flow.

## Testing notes

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

This has been passing after the recent Android changes.

`testDebugUnitTest` previously failed due to a local Windows/JDK/PATH test executor issue, not due to Kotlin compilation.

## Git status note

Recent Android commits include:

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
