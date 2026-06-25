namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// User-selected inputs used to build a focus cycle.
/// TotalDurationMinutes is the whole available time budget, including breaks.
/// </summary>
public sealed record FocusCycleRequest(
    int TotalDurationMinutes,
    int FocusPeriodMinutes,
    int BreakPeriodMinutes,
    bool SkipBreaks);

