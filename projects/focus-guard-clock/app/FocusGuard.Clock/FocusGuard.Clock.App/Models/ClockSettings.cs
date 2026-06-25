namespace FocusGuard.Clock.App.Models;

public sealed record ClockSettings(
    int TotalDuration,
    int FocusPeriod,
    int BreakPeriod,
    bool SkipBreaks,
    bool UseSeconds)
{
    public static ClockSettings Defaults => new(
        TotalDuration: 200,
        FocusPeriod: 25,
        BreakPeriod: 10,
        SkipBreaks: false,
        UseSeconds: false);
}
