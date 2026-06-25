namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// Calculated focus cycle that the app can display and run.
/// </summary>
public sealed record FocusCyclePlan(
    TimeSpan TotalDuration,
    TimeSpan UsedDuration,
    TimeSpan UnusedDuration,
    TimeSpan FocusPeriod,
    TimeSpan BreakPeriod,
    bool SkipBreaks,
    IReadOnlyList<CycleStage> Stages)
{
    public int TotalDurationMinutes => (int)TotalDuration.TotalMinutes;

    public int UsedDurationMinutes => (int)UsedDuration.TotalMinutes;

    public int UnusedDurationMinutes => (int)UnusedDuration.TotalMinutes;

    public int FocusPeriodMinutes => (int)FocusPeriod.TotalMinutes;

    public int BreakPeriodMinutes => (int)BreakPeriod.TotalMinutes;

    public int FocusPeriodCount => Stages.Count(stage => stage.IsFocus);

    public int BreakCount => Stages.Count(stage => stage.IsBreak);
}
