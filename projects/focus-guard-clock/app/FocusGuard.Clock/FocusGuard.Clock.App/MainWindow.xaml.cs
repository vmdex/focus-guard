using FocusGuard.Clock.App.Models;
using FocusGuard.Clock.App.Services;
using FocusGuard.Clock.Core.Models;
using FocusGuard.Clock.Core.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using System.Collections.Generic;
using System.Linq;

// To learn more about WinUI, the WinUI project structure,
// and more about our project templates, see: http://aka.ms/winui-project-info.

namespace FocusGuard.Clock.App
{
    /// <summary>
    /// An empty window that can be used on its own or navigated to within a Frame.
    /// </summary>
    public sealed partial class MainWindow : Window
    {
        private readonly FocusCycleCalculator _calculator = new();
        private readonly ClockSettingsService _settingsService = new();
        private readonly DispatcherTimer _timer = new();

        private FocusTimerRunner? _timerRunner;
        private ClockSettings _currentSettings = ClockSettings.Defaults;
        private DateTimeOffset _lastTickAt;

        public MainWindow()
        {
            InitializeComponent();

            _timer.Interval = TimeSpan.FromSeconds(1);
            _timer.Tick += Timer_Tick;

            ApplySettings(_settingsService.Load());
            CalculateAndRenderPlan();
        }

        private void CalculateButton_Click(object sender, RoutedEventArgs e)
        {
            CalculateAndRenderPlan();
        }

        private void CalculateAndRenderPlan()
        {
            try
            {
                var settings = ReadSettingsFromInputs();
                _currentSettings = settings;
                _settingsService.Save(settings);

                var request = new FocusCycleRequest(
                    TimeSpan.FromMinutes(settings.TotalDuration),
                    TimeSpan.FromMinutes(settings.FocusPeriod),
                    TimeSpan.FromMinutes(settings.BreakPeriod),
                    settings.SkipBreaks);

                var plan = _calculator.Calculate(request);
                _timer.Stop();
                _timerRunner = new FocusTimerRunner(plan);

                ErrorTextBlock.Visibility = Visibility.Collapsed;
                DeveloperFocusCountTextBlock.Text = plan.FocusPeriodCount.ToString();
                DeveloperBreakCountTextBlock.Text = plan.BreakCount.ToString();
                DeveloperUsedDurationTextBlock.Text = FormatDuration(plan.UsedDuration);
                DeveloperUnusedDurationTextBlock.Text = FormatDuration(plan.UnusedDuration);
                DeveloperStagesListView.ItemsSource = plan.Stages
                    .Select(FormatStage)
                    .ToList();
                DeveloperTimerEventTextBlock.Text = string.Empty;
                RenderTimer(_timerRunner.Snapshot);
            }
            catch (Exception exception)
            {
                _timer.Stop();
                _timerRunner = null;
                ErrorTextBlock.Text = exception.Message;
                ErrorTextBlock.Visibility = Visibility.Visible;
                DeveloperStagesListView.ItemsSource = null;
                RenderTimer(null);
            }
        }

        private void StartButton_Click(object sender, RoutedEventArgs e)
        {
            if (_timerRunner is null)
            {
                CalculateAndRenderPlan();
            }

            if (_timerRunner is null)
            {
                return;
            }

            _lastTickAt = DateTimeOffset.Now;
            RenderTimer(_timerRunner.Start());
            _timer.Start();
        }

        private void PauseButton_Click(object sender, RoutedEventArgs e)
        {
            _timer.Stop();
            RenderTimer(_timerRunner?.Pause());
        }

        private void ResumeButton_Click(object sender, RoutedEventArgs e)
        {
            if (_timerRunner is null)
            {
                return;
            }

            _lastTickAt = DateTimeOffset.Now;
            var snapshot = _timerRunner.Resume(out var events);
            RenderEvents(events);
            RenderTimer(snapshot);
            _timer.Start();
        }

        private void ResetButton_Click(object sender, RoutedEventArgs e)
        {
            _timer.Stop();
            DeveloperTimerEventTextBlock.Text = string.Empty;
            RenderTimer(_timerRunner?.Reset());
        }

        private void StopButton_Click(object sender, RoutedEventArgs e)
        {
            if (_timerRunner is null)
            {
                return;
            }

            _timer.Stop();
            var result = _timerRunner.Stop();
            var message = $"Stopped. Focus progress: {FormatTime(result.FocusElapsed)}";
            DeveloperTimerEventTextBlock.Text = message;
            RenderTimer(_timerRunner.Snapshot);
        }

        private void AdvanceButton_Click(object sender, RoutedEventArgs e)
        {
            if (_timerRunner is null)
            {
                return;
            }

            if (_timerRunner.Snapshot.Status is not FocusTimerStatus.Running)
            {
                DeveloperTimerEventTextBlock.Text = "Start or resume the timer before advancing time.";
                return;
            }

            var seconds = ReadWholeNumber(AdvanceSecondsBox);
            if (seconds <= 0)
            {
                DeveloperTimerEventTextBlock.Text = "Advance seconds must be greater than zero.";
                return;
            }

            _lastTickAt = DateTimeOffset.Now;
            var events = _timerRunner.Advance(TimeSpan.FromSeconds(seconds));
            RenderEvents(events);
            RenderTimer(_timerRunner.Snapshot);

            if (_timerRunner.Snapshot.Status is FocusTimerStatus.Completed)
            {
                _timer.Stop();
            }
        }

        private void Timer_Tick(object? sender, object e)
        {
            if (_timerRunner is null)
            {
                _timer.Stop();
                return;
            }

            var now = DateTimeOffset.Now;
            var elapsed = now - _lastTickAt;
            _lastTickAt = now;

            var events = _timerRunner.Advance(elapsed);
            RenderEvents(events);
            RenderTimer(_timerRunner.Snapshot);

            if (_timerRunner.Snapshot.Status is FocusTimerStatus.Completed)
            {
                _timer.Stop();
            }
        }

        private void ApplySettings(ClockSettings settings)
        {
            _currentSettings = settings;
            TotalDurationBox.Value = settings.TotalDuration;
            FocusPeriodBox.Value = settings.FocusPeriod;
            BreakPeriodBox.Value = settings.BreakPeriod;
            SkipBreaksCheckBox.IsChecked = settings.SkipBreaks;
        }

        private ClockSettings ReadSettingsFromInputs()
        {
            return new ClockSettings(
                TotalDuration: ReadWholeNumber(TotalDurationBox),
                FocusPeriod: ReadWholeNumber(FocusPeriodBox),
                BreakPeriod: ReadWholeNumber(BreakPeriodBox),
                SkipBreaks: SkipBreaksCheckBox.IsChecked == true);
        }

        private static int ReadWholeNumber(NumberBox numberBox)
        {
            if (double.IsNaN(numberBox.Value))
            {
                return 0;
            }

            return (int)Math.Round(numberBox.Value);
        }

        private static string FormatStage(CycleStage stage)
        {
            var label = stage.IsFocus
                ? $"Focus {stage.FocusPeriodNumber} of {stage.TotalFocusPeriods}"
                : $"Break after focus {stage.FocusPeriodNumber}";

            return $"{label}: {FormatDuration(stage.Duration)}";
        }

        private void RenderTimer(FocusTimerSnapshot? snapshot)
        {
            if (snapshot is null)
            {
                DeveloperTimerStatusTextBlock.Text = "Invalid settings";
                DeveloperCurrentStageTextBlock.Text = "-";
                DeveloperRemainingTimeTextBlock.Text = "--:--";
                DeveloperFocusElapsedTextBlock.Text = "--:--";
                DeveloperTotalElapsedTextBlock.Text = "--:--";
                SetTimerButtonState(FocusTimerStatus.Idle, hasTimer: false);
                return;
            }

            var stageLabel = FormatCurrentStage(snapshot.CurrentStage);
            var remainingTime = FormatTime(snapshot.RemainingInCurrentStage);
            var focusElapsed = FormatTime(snapshot.FocusElapsed);

            DeveloperTimerStatusTextBlock.Text = snapshot.Status.ToString();
            DeveloperCurrentStageTextBlock.Text = stageLabel;
            DeveloperRemainingTimeTextBlock.Text = remainingTime;
            DeveloperFocusElapsedTextBlock.Text = focusElapsed;
            DeveloperTotalElapsedTextBlock.Text = FormatTime(snapshot.TotalElapsed);

            SetTimerButtonState(snapshot.Status, hasTimer: true);
        }

        private void SetTimerButtonState(FocusTimerStatus status, bool hasTimer)
        {
            var canStart = hasTimer && status is FocusTimerStatus.Idle or FocusTimerStatus.Completed;
            var canPause = hasTimer && status is FocusTimerStatus.Running;
            var canResume = hasTimer && status is FocusTimerStatus.Paused;
            var canReset = hasTimer && status is not FocusTimerStatus.Idle;
            var canStop = hasTimer && status is FocusTimerStatus.Running or FocusTimerStatus.Paused;

            DeveloperStartButton.IsEnabled = canStart;
            DeveloperPauseButton.IsEnabled = canPause;
            DeveloperResumeButton.IsEnabled = canResume;
            DeveloperResetButton.IsEnabled = canReset;
            DeveloperStopButton.IsEnabled = canStop;
            AdvanceButton.IsEnabled = hasTimer && status is FocusTimerStatus.Running;
        }

        private void RenderEvents(IReadOnlyList<FocusTimerEvent> events)
        {
            if (events.Count == 0)
            {
                return;
            }

            var message = events[^1].Kind switch
            {
                FocusTimerEventKind.BreakStarted => "Break started",
                FocusTimerEventKind.FocusStarted => "Focus started",
                FocusTimerEventKind.TimerCompleted => "Timer completed",
                _ => string.Empty
            };

            DeveloperTimerEventTextBlock.Text = message;
        }

        private static string FormatCurrentStage(CycleStage? stage)
        {
            if (stage is null)
            {
                return "Completed";
            }

            return stage.IsFocus
                ? $"Focus {stage.FocusPeriodNumber} of {stage.TotalFocusPeriods}"
                : $"Break after focus {stage.FocusPeriodNumber}";
        }

        private static string FormatTime(TimeSpan time)
        {
            var totalSeconds = Math.Max(0, (int)Math.Ceiling(time.TotalSeconds));
            var hours = totalSeconds / 3600;
            var minutes = totalSeconds % 3600 / 60;
            var seconds = totalSeconds % 60;

            return hours > 0
                ? $"{hours}:{minutes:00}:{seconds:00}"
                : $"{minutes:00}:{seconds:00}";
        }

        private static string FormatDuration(TimeSpan duration)
        {
            return $"{(int)duration.TotalMinutes} min";
        }
    }
}
