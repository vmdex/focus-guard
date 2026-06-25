using FocusGuard.Clock.App.Models;
using Windows.Storage;

namespace FocusGuard.Clock.App.Services;

public sealed class ClockSettingsService
{
    private const string TotalDurationKey = "Clock.TotalDurationMinutes";
    private const string FocusPeriodKey = "Clock.FocusPeriodMinutes";
    private const string BreakPeriodKey = "Clock.BreakPeriodMinutes";
    private const string SkipBreaksKey = "Clock.SkipBreaks";

    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;

    public ClockSettings Load()
    {
        var defaults = ClockSettings.Defaults;

        return new ClockSettings(
            TotalDurationMinutes: ReadInt(TotalDurationKey, defaults.TotalDurationMinutes),
            FocusPeriodMinutes: ReadInt(FocusPeriodKey, defaults.FocusPeriodMinutes),
            BreakPeriodMinutes: ReadInt(BreakPeriodKey, defaults.BreakPeriodMinutes),
            SkipBreaks: ReadBool(SkipBreaksKey, defaults.SkipBreaks));
    }

    public void Save(ClockSettings settings)
    {
        _localSettings.Values[TotalDurationKey] = settings.TotalDurationMinutes;
        _localSettings.Values[FocusPeriodKey] = settings.FocusPeriodMinutes;
        _localSettings.Values[BreakPeriodKey] = settings.BreakPeriodMinutes;
        _localSettings.Values[SkipBreaksKey] = settings.SkipBreaks;
    }

    private int ReadInt(string key, int fallback)
    {
        var value = _localSettings.Values[key];

        return value is int intValue
            ? intValue
            : fallback;
    }

    private bool ReadBool(string key, bool fallback)
    {
        var value = _localSettings.Values[key];

        return value is bool boolValue
            ? boolValue
            : fallback;
    }
}

