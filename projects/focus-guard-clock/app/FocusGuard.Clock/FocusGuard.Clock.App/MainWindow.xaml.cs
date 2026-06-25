using FocusGuard.Clock.App.Models;
using FocusGuard.Clock.App.Services;
using FocusGuard.Clock.Core.Models;
using FocusGuard.Clock.Core.Services;
using Microsoft.UI.Xaml;
using Microsoft.UI.Xaml.Controls;
using System;
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

        public MainWindow()
        {
            InitializeComponent();

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

                ErrorTextBlock.Visibility = Visibility.Collapsed;
                FocusCountTextBlock.Text = plan.FocusPeriodCount.ToString();
                BreakCountTextBlock.Text = plan.BreakCount.ToString();
                UsedDurationTextBlock.Text = $"{plan.UsedDurationMinutes} min";
                UnusedDurationTextBlock.Text = $"{plan.UnusedDurationMinutes} min";
                StagesListView.ItemsSource = plan.Stages.Select(FormatStage).ToList();
            }
            catch (Exception exception)
            {
                ErrorTextBlock.Text = exception.Message;
                ErrorTextBlock.Visibility = Visibility.Visible;
                StagesListView.ItemsSource = null;
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
    }
}
