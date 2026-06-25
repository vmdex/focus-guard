using FocusGuard.Clock.App.Models;
using Windows.Storage;

namespace FocusGuard.Clock.App.Services;

public sealed class ClockSettingsService
{
    private const string TotalDurationKey = "Clock.TotalDuration";
    private const string FocusPeriodKey = "Clock.FocusPeriod";
    private const string BreakPeriodKey = "Clock.BreakPeriod";
    private const string LegacyTotalDurationMinutesKey = "Clock.TotalDurationMinutes";
    private const string LegacyFocusPeriodMinutesKey = "Clock.FocusPeriodMinutes";
    private const string LegacyBreakPeriodMinutesKey = "Clock.BreakPeriodMinutes";
    private const string SkipBreaksKey = "Clock.SkipBreaks";

    private readonly ApplicationDataContainer _localSettings = ApplicationData.Current.LocalSettings;

    public ClockSettings Load()
    {
        var defaults = ClockSettings.Defaults;

        return new ClockSettings(
            TotalDuration: ReadInt(TotalDurationKey, LegacyTotalDurationMinutesKey, defaults.TotalDuration),
            FocusPeriod: ReadInt(FocusPeriodKey, LegacyFocusPeriodMinutesKey, defaults.FocusPeriod),
            BreakPeriod: ReadInt(BreakPeriodKey, LegacyBreakPeriodMinutesKey, defaults.BreakPeriod),
            SkipBreaks: ReadBool(SkipBreaksKey, defaults.SkipBreaks));
    }

    public void Save(ClockSettings settings)
    {
        _localSettings.Values[TotalDurationKey] = settings.TotalDuration;
        _localSettings.Values[FocusPeriodKey] = settings.FocusPeriod;
        _localSettings.Values[BreakPeriodKey] = settings.BreakPeriod;
        _localSettings.Values[SkipBreaksKey] = settings.SkipBreaks;
    }

    private int ReadInt(string key, string legacyKey, int fallback)
    {
        var value = _localSettings.Values[key] ?? _localSettings.Values[legacyKey];

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
