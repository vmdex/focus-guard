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
        var usedDuration = stages.Sum(stage => stage.DurationMinutes);
        var unusedDuration = request.TotalDurationMinutes - usedDuration;

        return new FocusCyclePlan(
            request.TotalDurationMinutes,
            usedDuration,
            unusedDuration,
            request.FocusPeriodMinutes,
            request.BreakPeriodMinutes,
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
                request.TotalDurationMinutes,
                FocusPeriodNumber: 1,
                TotalFocusPeriods: 1)
        };

        return new FocusCyclePlan(
            request.TotalDurationMinutes,
            UsedDurationMinutes: request.TotalDurationMinutes,
            UnusedDurationMinutes: 0,
            request.FocusPeriodMinutes,
            request.BreakPeriodMinutes,
            request.SkipBreaks,
            stages);
    }

    private static List<CycleStage> BuildStagesWithBreaks(FocusCycleRequest request)
    {
        var drafts = new List<StageDraft>();
        var remainingMinutes = request.TotalDurationMinutes;
        var focusPeriodNumber = 0;

        while (remainingMinutes > 0)
        {
            focusPeriodNumber++;

            // The final focus stage can be shorter than the configured focus period.
            // Example: total 30, focus 20, break 5 -> Focus 20, Break 5, Focus 5.
            var focusDuration = Math.Min(request.FocusPeriodMinutes, remainingMinutes);
            drafts.Add(new StageDraft(CycleStageKind.Focus, focusDuration, focusPeriodNumber));
            remainingMinutes -= focusDuration;

            if (remainingMinutes <= 0)
            {
                break;
            }

            // A break is useful only if it fully fits and there is still time
            // after it for another focus stage.
            if (remainingMinutes > request.BreakPeriodMinutes)
            {
                drafts.Add(new StageDraft(CycleStageKind.Break, request.BreakPeriodMinutes, focusPeriodNumber));
                remainingMinutes -= request.BreakPeriodMinutes;
                continue;
            }

            break;
        }

        var totalFocusPeriods = drafts.Count(stage => stage.Kind == CycleStageKind.Focus);

        return drafts
            .Select(stage => new CycleStage(
                stage.Kind,
                stage.DurationMinutes,
                stage.FocusPeriodNumber,
                totalFocusPeriods))
            .ToList();
    }

    private static void Validate(FocusCycleRequest request)
    {
        if (request.TotalDurationMinutes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Total duration must be greater than zero.");
        }

        if (request.SkipBreaks)
        {
            return;
        }

        if (request.FocusPeriodMinutes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Focus period duration must be greater than zero.");
        }

        if (request.BreakPeriodMinutes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
            "Break period duration must be greater than zero.");
        }
    }

    private sealed record StageDraft(
        CycleStageKind Kind,
        int DurationMinutes,
        int FocusPeriodNumber);
}
