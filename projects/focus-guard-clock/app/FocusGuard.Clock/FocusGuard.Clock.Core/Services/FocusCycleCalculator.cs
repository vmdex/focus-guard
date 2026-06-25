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

        var focusPeriodCount = request.SkipBreaks
            ? request.TotalDurationMinutes / request.FocusPeriodMinutes
            : (request.TotalDurationMinutes + request.BreakPeriodMinutes)
                / (request.FocusPeriodMinutes + request.BreakPeriodMinutes);

        focusPeriodCount = Math.Max(1, focusPeriodCount);

        var breakCount = request.SkipBreaks ? 0 : focusPeriodCount - 1;
        var stages = BuildStages(request, focusPeriodCount, breakCount);
        var usedDuration = stages.Sum(stage => stage.DurationMinutes);

        return new FocusCyclePlan(
            request.TotalDurationMinutes,
            usedDuration,
            request.TotalDurationMinutes - usedDuration,
            request.FocusPeriodMinutes,
            request.BreakPeriodMinutes,
            request.SkipBreaks,
            stages);
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
