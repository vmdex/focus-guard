namespace FocusGuard.Clock.Core.Models;

/// <summary>
/// Immutable view of the timer at one moment in time.
/// The UI should render from this instead of reading timer internals directly.
/// </summary>
public sealed record FocusTimerSnapshot(
    FocusTimerStatus Status,
    int CurrentStageIndex,
    CycleStage? CurrentStage,
    TimeSpan ElapsedInCurrentStage,
    TimeSpan TotalElapsed,
    TimeSpan FocusElapsed,
    bool IsComplete)
{
    public TimeSpan CurrentStageDuration => CurrentStage is null
        ? TimeSpan.Zero
        : CurrentStage.Duration;

    public TimeSpan RemainingInCurrentStage
    {
        get
        {
            var remaining = CurrentStageDuration - ElapsedInCurrentStage;

            return remaining > TimeSpan.Zero
                ? remaining
                : TimeSpan.Zero;
        }
    }
}
