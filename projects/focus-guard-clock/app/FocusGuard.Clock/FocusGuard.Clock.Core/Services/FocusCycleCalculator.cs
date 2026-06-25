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
    /// Breaks are placed only between focus periods, never after the last one.
    /// </summary>
    public FocusCyclePlan Calculate(FocusCycleRequest request)
    {
        Validate(request);

        // First decide how many full focus periods fit into the user's time budget.
        // v0.1 does not create a shorter "partial" focus period from leftover time.
        var focusPeriodCount = CalculateFocusPeriodCount(request);

        // Breaks are only inserted between focus periods, never after the last one.
        var breakCount = CalculateBreakCount(request, focusPeriodCount);

        // The UI runner can later execute these stages one by one.
        var stages = BuildStages(request, focusPeriodCount, breakCount);

        // Used duration can be smaller than total duration when the leftover time
        // is not long enough for another full focus period.
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

    private static int CalculateFocusPeriodCount(FocusCycleRequest request)
    {
        if (request.SkipBreaks)
        {
            return request.TotalDurationMinutes / request.FocusPeriodMinutes;
        }

        // With breaks enabled, one full pair is:
        // focus + break.
        //
        // The last focus period does not need a break after it, so we add one
        // break duration to the numerator. This lets the formula count that
        // final focus period without requiring an extra trailing break.
        //
        // Example:
        // total = 200, focus = 25, break = 10
        // floor((200 + 10) / (25 + 10)) = 6 focus periods.
        var fullFocusAndBreakPair = request.FocusPeriodMinutes + request.BreakPeriodMinutes;
        var budgetAdjustedForFinalFocus = request.TotalDurationMinutes + request.BreakPeriodMinutes;

        return budgetAdjustedForFinalFocus / fullFocusAndBreakPair;
    }

    private static int CalculateBreakCount(FocusCycleRequest request, int focusPeriodCount)
    {
        if (request.SkipBreaks)
        {
            return 0;
        }

        return focusPeriodCount - 1;
    }

    private static List<CycleStage> BuildStages(
        FocusCycleRequest request,
        int focusPeriodCount,
        int breakCount)
    {
        var stages = new List<CycleStage>(focusPeriodCount + breakCount);

        for (var focusPeriodNumber = 1; focusPeriodNumber <= focusPeriodCount; focusPeriodNumber++)
        {
            stages.Add(new CycleStage(
                CycleStageKind.Focus,
                request.FocusPeriodMinutes,
                focusPeriodNumber,
                focusPeriodCount));

            var shouldAddBreak = !request.SkipBreaks && focusPeriodNumber < focusPeriodCount;

            if (shouldAddBreak)
            {
                stages.Add(new CycleStage(
                    CycleStageKind.Break,
                    request.BreakPeriodMinutes,
                    focusPeriodNumber,
                    focusPeriodCount));
            }
        }

        return stages;
    }

    private static void Validate(FocusCycleRequest request)
    {
        if (request.TotalDurationMinutes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Total duration must be greater than zero.");
        }

        if (request.FocusPeriodMinutes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Focus period duration must be greater than zero.");
        }

        if (request.TotalDurationMinutes < request.FocusPeriodMinutes)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Total duration must be greater than or equal to the focus period duration.");
        }

        if (request.BreakPeriodMinutes <= 0)
        {
            throw new ArgumentOutOfRangeException(
                nameof(request),
                "Break period duration must be greater than zero.");
        }
    }
}
