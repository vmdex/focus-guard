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
    public void Calculate_WithSkipBreaks_CreatesOnlyFocusPeriods()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 200,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: true));

        Assert.AreEqual(8, plan.FocusPeriodCount);
        Assert.AreEqual(0, plan.BreakCount);
        Assert.AreEqual(8, plan.Stages.Count);
        Assert.IsTrue(plan.Stages.All(stage => stage.IsFocus));
    }

    [TestMethod]
    public void Calculate_WhenTimeDoesNotFitFullPeriod_TracksUnusedDuration()
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: 50,
            FocusPeriodMinutes: 25,
            BreakPeriodMinutes: 10,
            SkipBreaks: false));

        Assert.AreEqual(1, plan.FocusPeriodCount);
        Assert.AreEqual(0, plan.BreakCount);
        Assert.AreEqual(25, plan.UsedDurationMinutes);
        Assert.AreEqual(25, plan.UnusedDurationMinutes);
    }

    [TestMethod]
    public void Calculate_WhenTotalDurationIsShorterThanFocusPeriod_Throws()
    {
        Assert.ThrowsExactly<ArgumentOutOfRangeException>(() =>
            _calculator.Calculate(new FocusCycleRequest(
                TotalDurationMinutes: 10,
                FocusPeriodMinutes: 25,
                BreakPeriodMinutes: 10,
                SkipBreaks: false)));
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
