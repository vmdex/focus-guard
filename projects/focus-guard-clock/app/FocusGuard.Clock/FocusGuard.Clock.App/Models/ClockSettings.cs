namespace FocusGuard.Clock.App.Models;

public sealed record ClockSettings(
    int TotalDurationMinutes,
    int FocusPeriodMinutes,
    int BreakPeriodMinutes,
    bool SkipBreaks)
{
    public static ClockSettings Defaults => new(
        TotalDurationMinutes: 200,
        FocusPeriodMinutes: 25,
        BreakPeriodMinutes: 10,
        SkipBreaks: false);
}

