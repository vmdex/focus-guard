namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// High-level timer state that the UI can use to decide which buttons to show.
/// </summary>
public enum FocusTimerStatus
{
    Idle,
    Running,
    Paused,
    Completed
}

