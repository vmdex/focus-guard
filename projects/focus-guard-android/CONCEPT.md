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

## Target device

Primary development and testing device:

```text
Pixel 7
Android 16
Wireless debugging over Wi-Fi
```

Codex can install and launch the debug app through ADB over Wi-Fi, while the user tests directly on the phone.
