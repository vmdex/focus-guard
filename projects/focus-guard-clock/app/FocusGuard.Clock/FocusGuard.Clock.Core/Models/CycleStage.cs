namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// A single scheduled step in the cycle, for example "Focus period 2 of 6"
/// or the break after it.
/// </summary>
public sealed record CycleStage(
    CycleStageKind Kind,
    TimeSpan Duration,
    int FocusPeriodNumber,
    int TotalFocusPeriods)
{
    public CycleStage(
        CycleStageKind Kind,
        int DurationMinutes,
        int FocusPeriodNumber,
        int TotalFocusPeriods)
        : this(
            Kind,
            TimeSpan.FromMinutes(DurationMinutes),
            FocusPeriodNumber,
            TotalFocusPeriods)
    {
    }

    public int DurationMinutes => (int)Duration.TotalMinutes;

    public bool IsFocus => Kind == CycleStageKind.Focus;

    public bool IsBreak => Kind == CycleStageKind.Break;
}
