namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// Result of a manual stop. FocusElapsed is the value that should be added
/// to daily progress, including an unfinished focus period.
/// </summary>
public sealed record FocusTimerStopResult(
    TimeSpan FocusElapsed);

