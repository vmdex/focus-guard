namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// Events produced by the timer when time crosses an important boundary.
/// Later the app can map these events to sounds, notifications, and progress writes.
/// </summary>
public enum FocusTimerEventKind
{
    BreakStarted,
    FocusStarted,
    TimerCompleted
}

