using FocusGuard.Clock.Core.Models;

namespace FocusGuard.Clock.Core.Services;

/// <summary>
/// Builds the Focus/Break/Focus sequence from the user's time settings.
/// The calculator has no UI dependencies so it can be tested independently.
/// </summary>
public sealed class FocusCycleCalculator
{
    /// <summary>
    /// Calculates the focus cycle.
    ///
    /// Rule used by v0.1:
    /// total duration means the whole cycle duration, including breaks.
    /// A short final focus stage is allowed when there is leftover time after a break.
    /// </summary>
    public FocusCyclePlan Calculate(FocusCycleRequest request)
    {
        Validate(request);

        if (request.SkipBreaks)
        {
            return BuildContinuousFocusPlan(request);
        }

        // Build the timeline step by step. This is easier to reason about than
        // a single formula because the final focus stage may be shorter.
        var stages = BuildStagesWithBreaks(request);
        var usedDuration = stages.Aggregate(TimeSpan.Zero, (total, stage) => total + stage.Duration);
        var unusedDuration = request.TotalDuration - usedDuration;

        return new FocusCyclePlan(
            request.TotalDuration,
            usedDuration,
            unusedDuration,
            request.FocusPeriod,
            request.BreakPeriod,
            request.SkipBreaks,
            stages);
    }

    private static FocusCyclePlan BuildContinuousFocusPlan(FocusCycleRequest request)
    {
        // When breaks are off, total duration is the focus duration.
        // Focus/break period settings do not split the session.
        var stages = new List<CycleStage>
        {
            new(
                CycleStageKind.Focus,
                request.TotalDuration,
                FocusPeriodNumber: 1,
                TotalFocusPeriods: 1)
        };

        return new FocusCyclePlan(
            request.TotalDuration,
            UsedDuration: request.TotalDuration,
            UnusedDuration: TimeSpan.Zero,
            request.FocusPeriod,
            request.BreakPeriod,
            request.SkipBreaks,
            stages);
    }

    private static List<CycleStage> BuildStagesWithBreaks(FocusCycleRequest request)
    {
        var drafts = new List<StageDraft>();
        var remainingDuration = request.TotalDuration;
        var focusPeriodNumber = 0;

        while (remainingDuration > TimeSpan.Zero)
        {
            focusPeriodNumber++;

            // The final focus stage can be shorter than the configured focus period.
            // Example: total 30, focus 20, break 5 -> Focus 20, Break 5, Focus 5.
            var focusDuration = Min(request.FocusPeriod, remainingDuration);
            drafts.Add(new StageDraft(CycleStageKind.Focus, focusDuration, focusPeriodNumber));
            remainingDuration -= focusDuration;

            if (remainingDuration <= TimeSpan.Zero)
            {
                break;
            }

            // A break is useful only if it fully fits and there is still time
            // after it for another focus stage.
            if (remainingDuration > request.BreakPeriod)
            {
                drafts.Add(new StageDraft(CycleStageKind.Break, request.BreakPeriod, focusPeriodNumber));
                remainingDuration -= request.BreakPeriod;
                continue;
            }

            break;
        }

        var totalFocusPeriods = drafts.Count(stage => stage.Kind == CycleStageKind.Focus);

        return drafts
            .Select(stage => new CycleStage(
                stage.Kind,
                stage.Duration,
                stage.FocusPeriodNumber,
                totalFocusPeriods))
            .ToList();
    }

    private static void Validate(FocusCycleRequest request)
    {
        if (request.TotalDuration <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Total duration must be greater than zero.");
        }

        if (request.SkipBreaks)
        {
            return;
        }

        if (request.FocusPeriod <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Focus period duration must be greater than zero.");
        }

        if (request.BreakPeriod <= TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
            "Break period duration must be greater than zero.");
        }
    }

    private static TimeSpan Min(TimeSpan first, TimeSpan second)
    {
        return first < second
            ? first
            : second;
    }

    private sealed record StageDraft(
        CycleStageKind Kind,
        TimeSpan Duration,
        int FocusPeriodNumber);
}
