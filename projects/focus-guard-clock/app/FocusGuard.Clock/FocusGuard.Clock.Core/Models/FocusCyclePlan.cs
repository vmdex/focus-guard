namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// Calculated focus cycle that the app can display and run.
/// </summary>
public sealed record FocusCyclePlan(
    int TotalDurationMinutes,
    int UsedDurationMinutes,
    int UnusedDurationMinutes,
    int FocusPeriodMinutes,
    int BreakPeriodMinutes,
    bool SkipBreaks,
    IReadOnlyList<CycleStage> Stages)
{
    public int FocusPeriodCount => Stages.Count(stage => stage.IsFocus);

    public int BreakCount => Stages.Count(stage => stage.IsBreak);
}

