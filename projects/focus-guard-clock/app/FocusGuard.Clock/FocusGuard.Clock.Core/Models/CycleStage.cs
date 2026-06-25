namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// A single scheduled step in the cycle, for example "Focus period 2 of 6"
/// or the break after it.
/// </summary>
public sealed record CycleStage(
    CycleStageKind Kind,
    int DurationMinutes,
    int FocusPeriodNumber,
    int TotalFocusPeriods)
{
    public bool IsFocus => Kind == CycleStageKind.Focus;

    public bool IsBreak => Kind == CycleStageKind.Break;
}

