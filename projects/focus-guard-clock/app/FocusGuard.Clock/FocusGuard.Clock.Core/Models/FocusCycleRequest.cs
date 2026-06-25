namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// User-selected inputs used to build a focus cycle.
/// TotalDuration is the whole available time budget, including breaks.
/// </summary>
public sealed record FocusCycleRequest(
    TimeSpan TotalDuration,
    TimeSpan FocusPeriod,
    TimeSpan BreakPeriod,
    bool SkipBreaks)
{
    public FocusCycleRequest(
        int TotalDurationMinutes,
        int FocusPeriodMinutes,
        int BreakPeriodMinutes,
        bool SkipBreaks)
        : this(
            TimeSpan.FromMinutes(TotalDurationMinutes),
            TimeSpan.FromMinutes(FocusPeriodMinutes),
            TimeSpan.FromMinutes(BreakPeriodMinutes),
            SkipBreaks)
    {
    }

    public int TotalDurationMinutes => (int)TotalDuration.TotalMinutes;

    public int FocusPeriodMinutes => (int)FocusPeriod.TotalMinutes;

    public int BreakPeriodMinutes => (int)BreakPeriod.TotalMinutes;
}
