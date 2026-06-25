using FocusGuard.Clock.Core.Models;
using FocusGuard.Clock.Core.Services;

namespace FocusGuard.Clock.Tests.Services;

[TestClass]
public sealed class FocusTimerRunnerTests
{
    private readonly FocusCycleCalculator _calculator = new();

    [TestMethod]
    public void Start_FromIdle_BeginsFirstFocusWithoutEmittingStartEvent()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);

        var snapshot = runner.Start();

        Assert.AreEqual(FocusTimerStatus.Running, snapshot.Status);
        Assert.AreEqual(CycleStageKind.Focus, snapshot.CurrentStage?.Kind);
        Assert.AreEqual(TimeSpan.FromMinutes(20), snapshot.RemainingInCurrentStage);
    }

    [TestMethod]
    public void Advance_WhenFocusEnds_EmitsBreakStarted()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);
        runner.Start();

        var events = runner.Advance(TimeSpan.FromMinutes(20));

        Assert.AreEqual(1, events.Count);
        Assert.AreEqual(FocusTimerEventKind.BreakStarted, events[0].Kind);
        Assert.AreEqual(CycleStageKind.Break, runner.Snapshot.CurrentStage?.Kind);
        Assert.AreEqual(TimeSpan.FromMinutes(20), runner.Snapshot.FocusElapsed);
    }

    [TestMethod]
    public void Advance_WhenBreakEnds_EmitsFocusStarted()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);
        runner.Start();
        runner.Advance(TimeSpan.FromMinutes(20));

        var events = runner.Advance(TimeSpan.FromMinutes(5));

        Assert.AreEqual(1, events.Count);
        Assert.AreEqual(FocusTimerEventKind.FocusStarted, events[0].Kind);
        Assert.AreEqual(CycleStageKind.Focus, runner.Snapshot.CurrentStage?.Kind);
    }

    [TestMethod]
    public void Advance_WhenFinalFocusEnds_CompletesTimer()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);
        runner.Start();

        var events = runner.Advance(TimeSpan.FromMinutes(30));

        Assert.AreEqual(3, events.Count);
        Assert.AreEqual(FocusTimerEventKind.BreakStarted, events[0].Kind);
        Assert.AreEqual(FocusTimerEventKind.FocusStarted, events[1].Kind);
        Assert.AreEqual(FocusTimerEventKind.TimerCompleted, events[2].Kind);
        Assert.AreEqual(FocusTimerStatus.Completed, runner.Snapshot.Status);
        Assert.AreEqual(TimeSpan.FromMinutes(25), runner.Snapshot.FocusElapsed);
    }

    [TestMethod]
    public void Pause_PreventsAdvanceUntilResume()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);
        runner.Start();
        runner.Advance(TimeSpan.FromMinutes(3));

        runner.Pause();
        runner.Advance(TimeSpan.FromMinutes(10));

        Assert.AreEqual(FocusTimerStatus.Paused, runner.Snapshot.Status);
        Assert.AreEqual(TimeSpan.FromMinutes(3), runner.Snapshot.TotalElapsed);

        runner.Resume();
        runner.Advance(TimeSpan.FromMinutes(2));

        Assert.AreEqual(TimeSpan.FromMinutes(5), runner.Snapshot.TotalElapsed);
    }

    [TestMethod]
    public void Stop_ReturnsElapsedFocusTimeAndResetsToIdle()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);
        runner.Start();
        runner.Advance(TimeSpan.FromMinutes(12));

        var result = runner.Stop();

        Assert.AreEqual(TimeSpan.FromMinutes(12), result.FocusElapsed);
        Assert.AreEqual(FocusTimerStatus.Idle, runner.Snapshot.Status);
        Assert.AreEqual(TimeSpan.Zero, runner.Snapshot.TotalElapsed);
    }

    [TestMethod]
    public void Stop_DoesNotCountBreakTimeAsFocusProgress()
    {
        var runner = CreateRunner(total: 30, focus: 20, breakDuration: 5);
        runner.Start();
        runner.Advance(TimeSpan.FromMinutes(23));

        var result = runner.Stop();

        Assert.AreEqual(TimeSpan.FromMinutes(20), result.FocusElapsed);
    }

    private FocusTimerRunner CreateRunner(int total, int focus, int breakDuration)
    {
        var plan = _calculator.Calculate(new FocusCycleRequest(
            TotalDurationMinutes: total,
            FocusPeriodMinutes: focus,
            BreakPeriodMinutes: breakDuration,
            SkipBreaks: false));

        return new FocusTimerRunner(plan);
    }
}

