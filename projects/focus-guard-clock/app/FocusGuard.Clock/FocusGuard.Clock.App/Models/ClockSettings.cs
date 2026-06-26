namespace FocusGuard.Clock.App.Models;

public sealed record ClockSettings(
    int TotalDuration,
    int FocusPeriod,
    int BreakPeriod,
    int DailyGoalMinutes,
    bool SkipBreaks,
    bool ShowNotifications,
    bool PlayNotificationSound)
{
    public static ClockSettings Defaults => new(
        TotalDuration: 200,
        FocusPeriod: 25,
        BreakPeriod: 10,
        DailyGoalMinutes: 360,
        SkipBreaks: false,
        ShowNotifications: true,
        PlayNotificationSound: true);
}
