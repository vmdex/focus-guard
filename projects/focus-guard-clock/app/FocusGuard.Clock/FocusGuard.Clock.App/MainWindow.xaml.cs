using FocusGuard.Clock.App.Models;
using FocusGuard.Clock.App.Services;
using FocusGuard.Clock.Core.Models;
using FocusGuard.Clock.Core.Services;
using Microsoft.UI;
using Microsoft.UI.Windowing;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
using System.Collections.Generic;
using System.Linq;
using WinRT.Interop;

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
        private readonly NotificationService _notificationService = new();
        private readonly DispatcherTimer _timer = new();

        private AppWindow? _appWindow;
        private FocusTimerRunner? _timerRunner;
        private ClockSettings _currentSettings = ClockSettings.Defaults;
        private bool _isApplyingSettings;
        private DateTimeOffset _lastTickAt;
        private TimeSpan _dailyFocusProgress = TimeSpan.Zero;
        private TimeSpan _dailyTotalProgress = TimeSpan.Zero;
        private FocusTimerRunner? _committedTimerRunner;

        public MainWindow()
        {
            InitializeComponent();
            ConfigureTitleBar();
            ConfigureBackgroundClose();
            RootNavigationView.SelectedItem = FocusSessionNavigationItem;
            _notificationService.NotificationActivated += NotificationService_NotificationActivated;

            _timer.Interval = TimeSpan.FromSeconds(1);
            _timer.Tick += Timer_Tick;

            ApplySettings(_settingsService.Load());
            CalculateAndRenderPlan();
        }

        private void ConfigureTitleBar()
        {
            ExtendsContentIntoTitleBar = true;
            SetTitleBar(WindowDragArea);
        }

        private void ConfigureBackgroundClose()
        {
            var windowHandle = WindowNative.GetWindowHandle(this);
            var windowId = Win32Interop.GetWindowIdFromWindow(windowHandle);
            _appWindow = AppWindow.GetFromWindowId(windowId);
            _appWindow.Closing += AppWindow_Closing;
        }

        private void AppWindow_Closing(AppWindow sender, AppWindowClosingEventArgs args)
        {
            if (!IsTimerInProgress())
            {
                return;
            }

            args.Cancel = true;
            sender.Hide();
        }

        private void NotificationService_NotificationActivated(object? sender, EventArgs e)
        {
            DispatcherQueue.TryEnqueue(RestoreFromBackground);
        }

        public void RestoreFromBackground()
        {
            _appWindow?.Show();
            Activate();
        }

        private void RootNavigationView_SelectionChanged(NavigationView sender, NavigationViewSelectionChangedEventArgs args)
        {
            if (args.IsSettingsSelected)
            {
                ShowSettingsPage();
                return;
            }

            if (args.SelectedItemContainer?.Tag as string == "FocusSession")
            {
                ShowFocusSessionPage();
            }
        }

        private void SettingsInput_Changed(object sender, RoutedEventArgs e)
        {
            SaveSettingsAndRecalculate();
        }

        private void SettingsNumberBox_ValueChanged(NumberBox sender, NumberBoxValueChangedEventArgs args)
        {
            SaveSettingsAndRecalculate();
        }

        private void DashboardGrid_SizeChanged(object sender, SizeChangedEventArgs e)
        {
            var isWide = e.NewSize.Width >= 760;
            ApplyDashboardLayout(isWide);
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
            if (_timerRunner?.Snapshot.Status is FocusTimerStatus.Idle or FocusTimerStatus.Completed)
            {
                CommitCurrentTimerToDailyProgress();
                CalculateAndRenderPlan();
            }

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
            RenderEvents(events, showNotifications: false);
            RenderTimer(snapshot);
            _timer.Start();
        }

        private void ResetButton_Click(object sender, RoutedEventArgs e)
        {
            if (_timerRunner is null)
            {
                return;
            }

            _timer.Stop();
            CommitCurrentTimerToDailyProgress();
            var result = _timerRunner.Stop();
            var message = $"Reset. Focus progress: {FormatTime(result.FocusElapsed)}";
            CalculateAndRenderPlan();
            DeveloperTimerEventTextBlock.Text = message;
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
            CommitIfCompleted();
            RenderTimer(_timerRunner.Snapshot);

            if (_timerRunner.Snapshot.Status is FocusTimerStatus.Completed)
            {
                _timer.Stop();
            }
        }

        private void TestBreakNotificationButton_Click(object sender, RoutedEventArgs e)
        {
            _notificationService.ShowBreakStarted(TimeSpan.FromMinutes(_currentSettings.BreakPeriod));
        }

        private void TestFocusNotificationButton_Click(object sender, RoutedEventArgs e)
        {
            _notificationService.ShowFocusStarted(TimeSpan.FromMinutes(_currentSettings.FocusPeriod));
        }

        private void TestFinishedNotificationButton_Click(object sender, RoutedEventArgs e)
        {
            _notificationService.ShowFocusFinished();
        }

        private async void EditDailyGoalButton_Click(object sender, RoutedEventArgs e)
        {
            var dailyGoalBox = new NumberBox
            {
                Header = "Daily goal (minutes)",
                Minimum = 1,
                Value = _currentSettings.DailyGoalMinutes,
                SmallChange = 15,
                LargeChange = 60,
                SpinButtonPlacementMode = NumberBoxSpinButtonPlacementMode.Inline
            };

            var dialog = new ContentDialog
            {
                Title = "Edit daily goal",
                Content = dailyGoalBox,
                PrimaryButtonText = "Save",
                CloseButtonText = "Cancel",
                DefaultButton = ContentDialogButton.Primary,
                XamlRoot = RootNavigationView.XamlRoot
            };

            if (await dialog.ShowAsync() is not ContentDialogResult.Primary)
            {
                return;
            }

            var dailyGoalMinutes = Math.Max(1, ReadWholeNumber(dailyGoalBox));
            _currentSettings = _currentSettings with { DailyGoalMinutes = dailyGoalMinutes };
            _settingsService.Save(_currentSettings);
            RenderDailyProgress(_timerRunner?.Snapshot);
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
            CommitIfCompleted();
            RenderTimer(_timerRunner.Snapshot);

            if (_timerRunner.Snapshot.Status is FocusTimerStatus.Completed)
            {
                _timer.Stop();
            }
        }

        private void ApplySettings(ClockSettings settings)
        {
            _isApplyingSettings = true;
            _currentSettings = settings;
            TotalDurationBox.Value = settings.TotalDuration;
            FocusPeriodBox.Value = settings.FocusPeriod;
            BreakPeriodBox.Value = settings.BreakPeriod;
            SkipBreaksCheckBox.IsChecked = settings.SkipBreaks;
            ShowNotificationsToggleSwitch.IsOn = settings.ShowNotifications;
            PlayNotificationSoundToggleSwitch.IsOn = settings.PlayNotificationSound;
            ApplyNotificationSettings(settings);
            RenderDailyProgress(_timerRunner?.Snapshot);
            _isApplyingSettings = false;
        }

        private ClockSettings ReadSettingsFromInputs()
        {
            return new ClockSettings(
                TotalDuration: ReadWholeNumber(TotalDurationBox),
                FocusPeriod: ReadWholeNumber(FocusPeriodBox),
                BreakPeriod: ReadWholeNumber(BreakPeriodBox),
                DailyGoalMinutes: _currentSettings.DailyGoalMinutes,
                SkipBreaks: SkipBreaksCheckBox.IsChecked == true,
                ShowNotifications: ShowNotificationsToggleSwitch.IsOn,
                PlayNotificationSound: PlayNotificationSoundToggleSwitch.IsOn);
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
                RenderDailyProgress(null);
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
            RenderDailyProgress(snapshot);
        }

        private void RenderDailyProgress(FocusTimerSnapshot? snapshot)
        {
            var includeCurrentTimer = snapshot is not null && !ReferenceEquals(_timerRunner, _committedTimerRunner);
            var focusProgress = _dailyFocusProgress + (includeCurrentTimer ? snapshot!.FocusElapsed : TimeSpan.Zero);
            var totalProgress = _dailyTotalProgress + (includeCurrentTimer ? snapshot!.TotalElapsed : TimeSpan.Zero);
            var goal = TimeSpan.FromMinutes(Math.Max(1, _currentSettings.DailyGoalMinutes));
            var progressPercent = Math.Clamp(focusProgress.TotalMinutes / goal.TotalMinutes * 100, 0, 100);

            DailyGoalTextBlock.Text = FormatDurationLabel(goal);
            DailyFocusProgressTextBlock.Text = FormatDurationLabel(focusProgress);
            DailyTotalProgressTextBlock.Text = FormatDurationLabel(totalProgress);
            DailyProgressBar.Value = progressPercent;
        }

        private void SetTimerButtonState(FocusTimerStatus status, bool hasTimer)
        {
            var canStart = hasTimer && status is FocusTimerStatus.Idle or FocusTimerStatus.Completed;
            var canPause = hasTimer && status is FocusTimerStatus.Running;
            var canResume = hasTimer && status is FocusTimerStatus.Paused;
            var canReset = hasTimer && status is FocusTimerStatus.Running or FocusTimerStatus.Paused;

            DeveloperStartButton.IsEnabled = canStart;
            DeveloperPauseButton.IsEnabled = canPause;
            DeveloperResumeButton.IsEnabled = canResume;
            DeveloperResetButton.IsEnabled = canReset;
            DeveloperStartButton.Visibility = canStart ? Visibility.Visible : Visibility.Collapsed;
            DeveloperPauseButton.Visibility = canPause ? Visibility.Visible : Visibility.Collapsed;
            DeveloperResumeButton.Visibility = canResume ? Visibility.Visible : Visibility.Collapsed;
            DeveloperResetButton.Visibility = canReset ? Visibility.Visible : Visibility.Collapsed;
            AdvanceButton.IsEnabled = hasTimer && status is FocusTimerStatus.Running;
        }

        private void CommitIfCompleted()
        {
            if (_timerRunner?.Snapshot.Status is FocusTimerStatus.Completed)
            {
                CommitCurrentTimerToDailyProgress();
            }
        }

        private void CommitCurrentTimerToDailyProgress()
        {
            if (_timerRunner is null || ReferenceEquals(_timerRunner, _committedTimerRunner))
            {
                return;
            }

            var snapshot = _timerRunner.Snapshot;
            _dailyFocusProgress += snapshot.FocusElapsed;
            _dailyTotalProgress += snapshot.TotalElapsed;
            _committedTimerRunner = _timerRunner;
        }

        private void RenderEvents(IReadOnlyList<FocusTimerEvent> events, bool showNotifications = true)
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

            if (!showNotifications)
            {
                return;
            }

            if (!_currentSettings.ShowNotifications)
            {
                return;
            }

            foreach (var timerEvent in events)
            {
                _notificationService.ShowTimerTransition(timerEvent);
            }
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

        private static string FormatDurationLabel(TimeSpan duration)
        {
            var totalMinutes = Math.Max(0, (int)Math.Round(duration.TotalMinutes));
            if (totalMinutes < 60)
            {
                return $"{totalMinutes} min";
            }

            var hours = totalMinutes / 60;
            var minutes = totalMinutes % 60;

            return minutes == 0
                ? $"{hours} h"
                : $"{hours} h {minutes} min";
        }

        private void SaveSettingsAndRecalculate()
        {
            if (_isApplyingSettings)
            {
                return;
            }

            var settings = ReadSettingsFromInputs();
            _currentSettings = settings;
            _settingsService.Save(settings);
            ApplyNotificationSettings(settings);
            RenderDailyProgress(_timerRunner?.Snapshot);

            if (IsTimerInProgress())
            {
                DeveloperTimerEventTextBlock.Text = "Settings saved. They will apply on the next start.";
                return;
            }

            CalculateAndRenderPlan();
            ApplyNotificationSettings(_currentSettings);
        }

        private bool IsTimerInProgress()
        {
            var status = _timerRunner?.Snapshot.Status;
            return status is FocusTimerStatus.Running or FocusTimerStatus.Paused;
        }

        private void ApplyNotificationSettings(ClockSettings settings)
        {
            PlayNotificationSoundToggleSwitch.IsEnabled = settings.ShowNotifications;
            PlayNotificationSoundStateTextBlock.Opacity = settings.ShowNotifications ? 1 : 0.56;
            TestBreakNotificationButton.IsEnabled = settings.ShowNotifications;
            TestFocusNotificationButton.IsEnabled = settings.ShowNotifications;
            TestFinishedNotificationButton.IsEnabled = settings.ShowNotifications;
            ShowNotificationsStateTextBlock.Text = FormatToggleState(settings.ShowNotifications);
            PlayNotificationSoundStateTextBlock.Text = FormatToggleState(settings.PlayNotificationSound);
            _notificationService.IsSoundEnabled = settings.ShowNotifications && settings.PlayNotificationSound;
        }

        private static string FormatToggleState(bool isOn)
        {
            return isOn ? "On" : "Off";
        }

        private void ApplyDashboardLayout(bool isWide)
        {
            DashboardColumn2.Width = isWide
                ? new GridLength(1, GridUnitType.Star)
                : new GridLength(0);
            DashboardColumn3.Width = isWide
                ? new GridLength(1, GridUnitType.Star)
                : new GridLength(0);

            UsedCard.Visibility = Visibility.Visible;
            UnusedCard.Visibility = Visibility.Visible;

            if (isWide)
            {
                Grid.SetRow(UsedCard, 0);
                Grid.SetColumn(UsedCard, 2);
                Grid.SetRow(UnusedCard, 0);
                Grid.SetColumn(UnusedCard, 3);
                Grid.SetRow(CurrentTimerCard, 1);
                Grid.SetColumn(CurrentTimerCard, 0);
                Grid.SetRow(DailyProgressCard, 1);
                Grid.SetColumn(DailyProgressCard, 2);
                Grid.SetRow(DevToolsCard, 2);
                Grid.SetColumn(DevToolsCard, 0);
                Grid.SetRow(DeveloperStagesListView, 3);
                Grid.SetColumnSpan(ErrorTextBlock, 4);
                return;
            }

            Grid.SetRow(UsedCard, 1);
            Grid.SetColumn(UsedCard, 0);
            Grid.SetRow(UnusedCard, 1);
            Grid.SetColumn(UnusedCard, 1);
            Grid.SetRow(CurrentTimerCard, 2);
            Grid.SetColumn(CurrentTimerCard, 0);
            Grid.SetRow(DailyProgressCard, 3);
            Grid.SetColumn(DailyProgressCard, 0);
            Grid.SetRow(DevToolsCard, 4);
            Grid.SetColumn(DevToolsCard, 0);
            Grid.SetRow(DeveloperStagesListView, 5);
            Grid.SetColumnSpan(ErrorTextBlock, 2);
        }

        private void ShowFocusSessionPage()
        {
            FocusSessionPage.Visibility = Visibility.Visible;
            SettingsPage.Visibility = Visibility.Collapsed;
        }

        private void ShowSettingsPage()
        {
            FocusSessionPage.Visibility = Visibility.Collapsed;
            SettingsPage.Visibility = Visibility.Visible;
        }
    }
}
