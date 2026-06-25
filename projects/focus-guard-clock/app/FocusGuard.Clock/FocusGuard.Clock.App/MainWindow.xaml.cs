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
                _settingsService.Save(settings);

                var request = new FocusCycleRequest(
                    settings.TotalDurationMinutes,
                    settings.FocusPeriodMinutes,
                    settings.BreakPeriodMinutes,
                    settings.SkipBreaks);

                var plan = _calculator.Calculate(request);
                _timer.Stop();
                _timerRunner = new FocusTimerRunner(plan);

                ErrorTextBlock.Visibility = Visibility.Collapsed;
                FocusCountTextBlock.Text = plan.FocusPeriodCount.ToString();
                BreakCountTextBlock.Text = plan.BreakCount.ToString();
                UsedDurationTextBlock.Text = $"{plan.UsedDurationMinutes} min";
                UnusedDurationTextBlock.Text = $"{plan.UnusedDurationMinutes} min";
                StagesListView.ItemsSource = plan.Stages.Select(FormatStage).ToList();
                TimerEventTextBlock.Text = string.Empty;
                RenderTimer(_timerRunner.Snapshot);
            }
            catch (Exception exception)
            {
                _timer.Stop();
                _timerRunner = null;
                ErrorTextBlock.Text = exception.Message;
                ErrorTextBlock.Visibility = Visibility.Visible;
                StagesListView.ItemsSource = null;
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
            RenderTimer(_timerRunner.Resume());
            _timer.Start();
        }

        private void ResetButton_Click(object sender, RoutedEventArgs e)
        {
            _timer.Stop();
            TimerEventTextBlock.Text = string.Empty;
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
            TimerEventTextBlock.Text = $"Stopped. Focus progress: {FormatTime(result.FocusElapsed)}";
            RenderTimer(_timerRunner.Snapshot);
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
            TotalDurationBox.Value = settings.TotalDurationMinutes;
            FocusPeriodBox.Value = settings.FocusPeriodMinutes;
            BreakPeriodBox.Value = settings.BreakPeriodMinutes;
            SkipBreaksCheckBox.IsChecked = settings.SkipBreaks;
        }

        private ClockSettings ReadSettingsFromInputs()
        {
            return new ClockSettings(
                TotalDurationMinutes: ReadWholeMinutes(TotalDurationBox),
                FocusPeriodMinutes: ReadWholeMinutes(FocusPeriodBox),
                BreakPeriodMinutes: ReadWholeMinutes(BreakPeriodBox),
                SkipBreaks: SkipBreaksCheckBox.IsChecked == true);
        }

        private static int ReadWholeMinutes(NumberBox numberBox)
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

            return $"{label}: {stage.DurationMinutes} min";
        }

        private void RenderTimer(FocusTimerSnapshot? snapshot)
        {
            if (snapshot is null)
            {
                TimerStatusTextBlock.Text = "Invalid settings";
                CurrentStageTextBlock.Text = "-";
                RemainingTimeTextBlock.Text = "--:--";
                FocusElapsedTextBlock.Text = "--:--";
                TotalElapsedTextBlock.Text = "--:--";
                SetTimerButtonState(FocusTimerStatus.Idle, hasTimer: false);
                return;
            }

            TimerStatusTextBlock.Text = snapshot.Status.ToString();
            CurrentStageTextBlock.Text = FormatCurrentStage(snapshot.CurrentStage);
            RemainingTimeTextBlock.Text = FormatTime(snapshot.RemainingInCurrentStage);
            FocusElapsedTextBlock.Text = FormatTime(snapshot.FocusElapsed);
            TotalElapsedTextBlock.Text = FormatTime(snapshot.TotalElapsed);

            SetTimerButtonState(snapshot.Status, hasTimer: true);
        }

        private void SetTimerButtonState(FocusTimerStatus status, bool hasTimer)
        {
            StartButton.IsEnabled = hasTimer && status is FocusTimerStatus.Idle or FocusTimerStatus.Completed;
            PauseButton.IsEnabled = hasTimer && status is FocusTimerStatus.Running;
            ResumeButton.IsEnabled = hasTimer && status is FocusTimerStatus.Paused;
            ResetButton.IsEnabled = hasTimer && status is not FocusTimerStatus.Idle;
            StopButton.IsEnabled = hasTimer && status is FocusTimerStatus.Running or FocusTimerStatus.Paused;
        }

        private void RenderEvents(IReadOnlyList<FocusTimerEvent> events)
        {
            if (events.Count == 0)
            {
                return;
            }

            TimerEventTextBlock.Text = events[^1].Kind switch
            {
                FocusTimerEventKind.BreakStarted => "Break started",
                FocusTimerEventKind.FocusStarted => "Focus started",
                FocusTimerEventKind.TimerCompleted => "Timer completed",
                _ => string.Empty
            };
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
    }
}
