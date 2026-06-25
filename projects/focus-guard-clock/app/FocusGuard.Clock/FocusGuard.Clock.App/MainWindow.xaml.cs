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

        public MainWindow()
        {
            InitializeComponent();

            TotalDurationBox.Value = 200;
            FocusPeriodBox.Value = 25;
            BreakPeriodBox.Value = 10;
            SkipBreaksCheckBox.IsChecked = false;

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
                var request = new FocusCycleRequest(
                    TotalDurationMinutes: ReadWholeMinutes(TotalDurationBox),
                    FocusPeriodMinutes: ReadWholeMinutes(FocusPeriodBox),
                    BreakPeriodMinutes: ReadWholeMinutes(BreakPeriodBox),
                    SkipBreaks: SkipBreaksCheckBox.IsChecked == true);

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
