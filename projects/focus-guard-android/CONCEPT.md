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
