using FocusGuard.Clock.Core.Models;
using FocusGuard.Clock.Core.Services;

namespace FocusGuard.Clock.Tests.Services;

[TestClass]
public sealed class FocusCycleCalculatorTests
{
    private readonly FocusCycleCalculator _calculator = new();

    [TestMethod]
    public void Calculate_WithBreaks_UsesTotalDurationAsFullCycleBudget()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 200,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: false));

        Assert.AreEqual(6, plan.FocusPeriodCount);
        Assert.AreEqual(5, plan.BreakCount);
        Assert.AreEqual(200, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);
        Assert.AreEqual(11, plan.Stages.Count);
    }

    [TestMethod]
    public void Calculate_WithBreaks_PlacesBreaksOnlyBetweenFocusPeriods()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 200,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: false));

        Assert.IsTrue(plan.Stages.First().IsFocus);
        Assert.IsTrue(plan.Stages.Last().IsFocus);

        for (var index = 0; index < plan.Stages.Count; index++)
        {
            var expectedKind = index % 2 == 0
                ? CycleStageKind.Focus
                : CycleStageKind.Break;

            Assert.AreEqual(expectedKind, plan.Stages[index].Kind);
        }
    }

    [TestMethod]
    public void Calculate_WithBreaksAndOneFocusPeriod_DoesNotCreateTrailingBreak()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 20,
            FocusPeriodMinutes: 20,
            BreakPeriodMinutes: 5,
            SkipBreaks: false));

        Assert.AreEqual(1, plan.FocusPeriodCount);
        Assert.AreEqual(0, plan.BreakCount);
        Assert.AreEqual(1, plan.Stages.Count);
        Assert.AreEqual(20, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);
        Assert.IsTrue(plan.Stages[0].IsFocus);
    }

    [TestMethod]
    public void Calculate_WithBreaksAndLeftoverAfterBreak_CreatesShortFinalFocusStage()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 30,
            FocusPeriodMinutes: 20,
            BreakPeriodMinutes: 5,
            SkipBreaks: false));

        Assert.AreEqual(2, plan.FocusPeriodCount);
        Assert.AreEqual(1, plan.BreakCount);
        Assert.AreEqual(3, plan.Stages.Count);
        Assert.AreEqual(30, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);

        Assert.AreEqual(CycleStageKind.Focus, plan.Stages[0].Kind);
        Assert.AreEqual(20, plan.Stages[0].DurationMinutes);
        Assert.AreEqual(CycleStageKind.Break, plan.Stages[1].Kind);
        Assert.AreEqual(5, plan.Stages[1].DurationMinutes);
        Assert.AreEqual(CycleStageKind.Focus, plan.Stages[2].Kind);
        Assert.AreEqual(5, plan.Stages[2].DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WithBreaksAndFullFinalFocusStage_EndsAfterSecondFocus()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 45,
            FocusPeriodMinutes: 20,
            BreakPeriodMinutes: 5,
            SkipBreaks: false));

        Assert.AreEqual(2, plan.FocusPeriodCount);
        Assert.AreEqual(1, plan.BreakCount);
        Assert.AreEqual(3, plan.Stages.Count);
        Assert.AreEqual(45, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);

        Assert.AreEqual(CycleStageKind.Focus, plan.Stages[0].Kind);
        Assert.AreEqual(20, plan.Stages[0].DurationMinutes);
        Assert.AreEqual(CycleStageKind.Break, plan.Stages[1].Kind);
        Assert.AreEqual(5, plan.Stages[1].DurationMinutes);
        Assert.AreEqual(CycleStageKind.Focus, plan.Stages[2].Kind);
        Assert.AreEqual(20, plan.Stages[2].DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WithBreaksAndNoRoomForNextFocus_DoesNotCreateTrailingBreak()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 50,
            FocusPeriodMinutes: 20,
            BreakPeriodMinutes: 5,
            SkipBreaks: false));

        Assert.AreEqual(2, plan.FocusPeriodCount);
        Assert.AreEqual(1, plan.BreakCount);
        Assert.AreEqual(3, plan.Stages.Count);
        Assert.AreEqual(45, plan.UsedDurationMinutes);
        Assert.AreEqual(5, plan.UnusedDurationMinutes);

        Assert.AreEqual(CycleStageKind.Focus, plan.Stages[0].Kind);
        Assert.AreEqual(20, plan.Stages[0].DurationMinutes);
        Assert.AreEqual(CycleStageKind.Break, plan.Stages[1].Kind);
        Assert.AreEqual(5, plan.Stages[1].DurationMinutes);
        Assert.AreEqual(CycleStageKind.Focus, plan.Stages[2].Kind);
        Assert.AreEqual(20, plan.Stages[2].DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WithSkipBreaks_CreatesOneContinuousFocusStage()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 20,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: true));

        Assert.AreEqual(1, plan.FocusPeriodCount);
        Assert.AreEqual(0, plan.BreakCount);
        Assert.AreEqual(1, plan.Stages.Count);
        Assert.AreEqual(20, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);
        Assert.IsTrue(plan.Stages[0].IsFocus);
        Assert.AreEqual(20, plan.Stages[0].DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WithSkipBreaks_IgnoresFocusAndBreakPeriodSettings()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 20,
            FocusPeriodMinutes: 0,
            BreakPeriodMinutes: 0,
            SkipBreaks: true));

        Assert.AreEqual(1, plan.FocusPeriodCount);
        Assert.AreEqual(0, plan.BreakCount);
        Assert.AreEqual(20, plan.UsedDurationMinutes);
        Assert.AreEqual(20, plan.Stages[0].DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WhenTimeDoesNotFitFullPeriod_CreatesShortFinalFocusStage()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 50,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: false));

        Assert.AreEqual(2, plan.FocusPeriodCount);
        Assert.AreEqual(1, plan.BreakCount);
        Assert.AreEqual(50, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);
        Assert.AreEqual(15, plan.Stages.Last().DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WhenTotalDurationIsShorterThanFocusPeriod_CreatesShortFocusStage()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 10,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: false));

        Assert.AreEqual(1, plan.FocusPeriodCount);
        Assert.AreEqual(0, plan.BreakCount);
        Assert.AreEqual(10, plan.UsedDurationMinutes);
        Assert.AreEqual(0, plan.UnusedDurationMinutes);
        Assert.AreEqual(10, plan.Stages[0].DurationMinutes);
    }

    [TestMethod]
    public void Calculate_WithInvalidDurations_Throws()
    {
        Assert.ThrowsExactly<ArgumentOutOfRangeException>(() =>
            _calculator.Calculate(new FocusCycleRequest(0, 25, 10, false)));

        Assert.ThrowsExactly<ArgumentOutOfRangeException>(() =>
            _calculator.Calculate(new FocusCycleRequest(200, 0, 10, false)));

        Assert.ThrowsExactly<ArgumentOutOfRangeException>(() =>
            _calculator.Calculate(new FocusCycleRequest(200, 25, 0, false)));
    }
}
