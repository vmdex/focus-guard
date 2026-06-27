# Focus Guard Android Concept

Last updated: 2026-06-26

## Product direction

Focus Guard Android v0.1 is an app usage watcher.

The first version should help notice when the user has been stuck in one distracting app for too long and gently interrupt that autopilot loop.

This is not a daily app limit tool at first.
This is not a hard blocker at first.
The core problem is long uninterrupted use.

## Main idea

The app should answer:

```text
Am I using this app for too long without noticing?
```

If yes, Focus Guard Android should warn the user and help them return to the planned activity.

## MVP behavior

The first working prototype should:

- request and explain Usage Access permission;
- detect the current or latest foreground app;
- track how long the current app session has been active;
- let the user configure a session limit;
- show a notification when a monitored app exceeds the limit;
- avoid notification spam by using a cooldown;
- show developer/debug information so we can understand Android behavior on a real Pixel 7.

## Future intervention model

The app should not treat Android notifications as the only possible response.

Long term, Focus Guard should think in terms of interventions:

```text
Rule matched -> Intervention planned -> Intervention delivered
```

Possible intervention types:

- normal notification;
- repeated notification;
- floating overlay;
- fullscreen overlay;
- in-app warning;
- sound or vibration;
- future app/site blocker.

For the MVP, keep the behavior simple:

```text
session limit exceeded -> one notification
```

But the implementation should avoid making notification delivery the permanent center of the domain model.

Future structure idea:

- `SessionState` knows whether a session crossed a rule threshold;
- `InterventionState` knows what was already delivered for the session;
- an `InterventionEngine` decides what should happen next;
- platform-specific notifiers only deliver the selected intervention.

Future debug/display state idea:

```text
NotificationState(
    status = NotNeeded / WaitingLimit / WaitingResumeDelay / Sent / RepeatScheduled / OverlayActive,
    leftMillis = ...
)
```

The floating overlay could then display a ready-made notification/intervention status instead of recalculating UI text from session and alert fields.

Benefits:

- this is the cleanest long-term model;
- it fits future repeated notifications and different intervention scenarios;
- it can explicitly model `NotNeeded`, `WaitingLimit`, `WaitingResumeDelay`, `Sent`, `RepeatScheduled`, and `OverlayActive`.

Tradeoffs:

- this is too much refactor for the current MVP;
- it requires separating alert/intervention architecture more clearly;
- for now, a small debug field such as `isAlertSentForSession` is enough to make the overlay honest without rebuilding the domain model.

This will make repeated notifications and escalation easier later, for example:

```text
first alert after limit
repeat every N minutes while still active
escalate to overlay after M repeats
```

## Session logic

The first preferred strategy is Grace period.

Grace period means:

- a tracked app starts a session when it moves to foreground;
- short switches through launcher, recents, Focus Guard, or another temporary screen do not immediately end the session;
- if the user returns to the tracked app before the grace period expires, the same session continues;
- if the user does not return before the grace period expires, the session ends;
- the grace period duration should be configurable and saved locally.

Important future idea: allow the user to choose the session strategy.

Possible strategies:

- Strict: session is active only while the foreground app is tracked; leaving the tracked app ends the session immediately.
- Grace period: short interruptions are allowed before ending the session.
- Pause model: leaving the tracked app pauses the session; returning resumes it; a long absence can end it.

If a strategy has its own settings, those settings should appear only after that strategy is selected.

## Monitoring off rule

Important implementation rule for the next Android step:

When Monitoring is off, Focus Guard should be truly idle.

That means:

- no watcher timer should run;
- no usage detection refresh loop should run;
- no session elapsed timer should continue;
- no limit checks should run;
- no alert notifications should be shown;
- the app should behave as stopped, not merely hidden.

This should be fixed before or during the move from Activity-owned prototype monitoring to `UsageWatcherService`-owned monitoring.

## Android process and battery behavior

Future topic to design carefully: Android may stop app processes or restrict background work to save battery.

Focus Guard needs a balanced approach:

- when monitoring is needed, Android should be less likely to stop it unexpectedly;
- when monitoring is off, the app should not keep work alive;
- the app should not abuse battery, CPU, notifications, or foreground services;
- the user should understand when Focus Guard is actively monitoring and why a foreground notification is visible.

Questions to decide later:

- should monitoring always use a foreground service while enabled;
- should the app ask the user to disable battery optimization for Focus Guard;
- should monitoring pause or reduce frequency under some conditions;
- what state should be saved so monitoring can recover after process death;
- how to avoid duplicate alerts after service/process restart;
- how to explain these tradeoffs in user-facing settings.

Performance idea to remember:

- `UsageEvents` polling should be delta-based during normal monitoring, reading only events after the last processed timestamp;
- the larger lookup window should be kept as a fallback for cold start, missing state, or recovery;
- monitoring off should do no polling at all;
- monitoring on does not necessarily need a 1 second tick forever;
- while the foreground app is untracked, polling could be slower;
- while a tracked app is active and close to its limit, polling can be more frequent;
- the floating overlay is a debug aid and can update every second during development, but should become optional or less frequent later;
- background logic should prefer timestamps and persisted session state over constant recomputation.

Modernize foreground usage events:

- `UsageEvents.Event.MOVE_TO_FOREGROUND` is deprecated;
- it still works, but Android marks it as old API that should not be treated as the long-term path;
- consider switching foreground detection to newer event types such as `UsageEvents.Event.ACTIVITY_RESUMED` and `UsageEvents.Event.ACTIVITY_PAUSED`;
- this should be tested carefully on the Pixel 7 for Chrome, Focus Guard itself, launcher/untracked apps, and session transitions.

## First technical path

Start with:

- Usage Access for app usage data;
- notification permission for alerts;
- a foreground service or periodic watcher if needed for stable monitoring;
- Compose UI for the app screens.

Do not start with Accessibility Service unless Usage Access is not enough.
Accessibility is powerful but more sensitive, so it should be a later step.

## First UI shape

The first screen can be a developer-friendly prototype:

- monitoring status;
- Usage Access permission status;
- button to open Android Usage Access settings;
- detected current app;
- current app session elapsed time;
- configured session limit;
- last alert time.

Settings can come later, after the permission and foreground app detection are reliable.

## Future UI: choose tracked apps

Configurable tracked apps should be postponed until the app has a more complete UI flow.

The app should have a `Choose apps` action. It can open either a dialog-like screen or a separate page.

The choose-apps UI should include:

- a search field at the top;
- a scrollable list of installed apps;
- one row per app;
- a checkbox before each app name;
- all apps unchecked by default.

Search behavior:

- when the search field is empty and no apps are selected, show all installed apps sorted by app name A-Z;
- when the user types text, show apps whose visible app name contains the entered text;
- matching should probably be case-insensitive;
- when search text is not empty, filter the whole list by app name; selected apps that do not match the search text should not stay pinned at the top;
- when search text is empty and apps are already selected, selected apps should appear at the top of the list with checked checkboxes.

Selection behavior:

- the user marks apps with checkboxes;
- tapping the checkmark action at the top applies the changes and returns the user to the previous screen;
- if there are unsaved changes, show `Save changes` next to the checkmark;
- if there are no unsaved changes, show only the checkmark;
- tapping the checkmark with no changes just returns to the previous screen;
- swiping back or pressing system Back returns to the previous screen without applying changes;
- no unsaved-changes warning is needed.

App list scope:

- show only user-launchable apps, meaning apps that can be opened from the launcher;
- keep this simple for the first implementation;
- show system apps too if they are launchable;
- each row should show only the app name, not the package name.

Applying changed tracking:

- if the currently active tracked app is unchecked and the user applies changes, Focus Guard should stop tracking that app immediately;
- future app statistics may still show historical data for apps that are no longer currently tracked;
- app statistics behavior will be designed later.

Entry point from the main screen:

- above the `Choose apps` action, show a count like `N tracking apps`;
- if none are selected, show `0 tracking apps`;
- during first setup, the app should show permission buttons and also a button to configure which apps should be tracked.

## Target device

Primary development and testing device:

```text
Pixel 7
Android 16
Wireless debugging over Wi-Fi
```

Codex can install and launch the debug app through ADB over Wi-Fi, while the user tests directly on the phone.
