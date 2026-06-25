using FocusGuard.Clock.Core.Models;

namespace FocusGuard.Clock.Core.Services;

/// <summary>
/// Runs a calculated focus cycle without depending on WinUI or a real clock.
/// The app owns the actual timer and calls Advance with the elapsed time.
/// </summary>
public sealed class FocusTimerRunner
{
    private readonly FocusCyclePlan _plan;

    private FocusTimerStatus _status = FocusTimerStatus.Idle;
    private int _currentStageIndex;
    private TimeSpan _elapsedInCurrentStage = TimeSpan.Zero;
    private TimeSpan _totalElapsed = TimeSpan.Zero;
    private TimeSpan _focusElapsed = TimeSpan.Zero;

    public FocusTimerRunner(FocusCyclePlan plan)
    {
        if (plan.Stages.Count == 0)
        {
            throw new ArgumentException("Timer plan must contain at least one stage.", nameof(plan));
        }

        _plan = plan;
    }

    public FocusTimerSnapshot Snapshot => CreateSnapshot();

    public FocusTimerSnapshot Start()
    {
        if (_status is FocusTimerStatus.Running)
        {
            return Snapshot;
        }

        if (_status is FocusTimerStatus.Completed)
        {
            Reset();
        }

        _status = FocusTimerStatus.Running;
        return Snapshot;
    }

    public FocusTimerSnapshot Pause()
    {
        if (_status is FocusTimerStatus.Running)
        {
            _status = FocusTimerStatus.Paused;
        }

        return Snapshot;
    }

    public FocusTimerSnapshot Resume()
    {
        Resume(out _);
        return Snapshot;
    }

    public FocusTimerSnapshot Resume(out IReadOnlyList<FocusTimerEvent> events)
    {
        if (_status is FocusTimerStatus.Paused)
        {
            var transitionEvents = new List<FocusTimerEvent>();
            var currentStage = _plan.Stages[_currentStageIndex];

            if (currentStage.IsBreak)
            {
                // Resuming a paused break means "I am ready to focus now".
                // The remaining break time is skipped instead of being counted as elapsed.
                MoveToNextStage(transitionEvents);
            }

            if (_status is not FocusTimerStatus.Completed)
            {
                _status = FocusTimerStatus.Running;
            }

            events = transitionEvents;
            return Snapshot;
        }

        events = [];
        return Snapshot;
    }

    public FocusTimerSnapshot Reset()
    {
        _status = FocusTimerStatus.Idle;
        _currentStageIndex = 0;
        _elapsedInCurrentStage = TimeSpan.Zero;
        _totalElapsed = TimeSpan.Zero;
        _focusElapsed = TimeSpan.Zero;

        return Snapshot;
    }

    public FocusTimerStopResult Stop()
    {
        var result = new FocusTimerStopResult(_focusElapsed);
        Reset();

        return result;
    }

    public IReadOnlyList<FocusTimerEvent> Advance(TimeSpan elapsed)
    {
        if (elapsed < TimeSpan.Zero)
        {
            throw new ArgumentOutOfRangeException(nameof(elapsed), "Elapsed time cannot be negative.");
        }

        if (_status is not FocusTimerStatus.Running || elapsed == TimeSpan.Zero)
        {
            return [];
        }

        var events = new List<FocusTimerEvent>();
        var remainingElapsed = elapsed;

        while (remainingElapsed > TimeSpan.Zero && _status is FocusTimerStatus.Running)
        {
            var currentStage = _plan.Stages[_currentStageIndex];
            var timeLeftInStage = currentStage.Duration - _elapsedInCurrentStage;
            var step = remainingElapsed < timeLeftInStage
                ? remainingElapsed
                : timeLeftInStage;

            AddElapsed(step, currentStage);
            remainingElapsed -= step;

            if (_elapsedInCurrentStage >= currentStage.Duration)
            {
                MoveToNextStage(events);
            }
        }

        return events;
    }

    private void AddElapsed(TimeSpan elapsed, CycleStage currentStage)
    {
        _elapsedInCurrentStage += elapsed;
        _totalElapsed += elapsed;

        if (currentStage.IsFocus)
        {
            _focusElapsed += elapsed;
        }
    }

    private void MoveToNextStage(List<FocusTimerEvent> events)
    {
        _currentStageIndex++;
        _elapsedInCurrentStage = TimeSpan.Zero;

        if (_currentStageIndex >= _plan.Stages.Count)
        {
            _status = FocusTimerStatus.Completed;
            events.Add(new FocusTimerEvent(FocusTimerEventKind.TimerCompleted, Stage: null));
            return;
        }

        var nextStage = _plan.Stages[_currentStageIndex];
        var eventKind = nextStage.IsBreak
            ? FocusTimerEventKind.BreakStarted
            : FocusTimerEventKind.FocusStarted;

        events.Add(new FocusTimerEvent(eventKind, nextStage));
    }

    private FocusTimerSnapshot CreateSnapshot()
    {
        var isComplete = _status is FocusTimerStatus.Completed;
        var currentStage = isComplete
            ? null
            : _plan.Stages[_currentStageIndex];

        return new FocusTimerSnapshot(
            _status,
            _currentStageIndex,
            currentStage,
            _elapsedInCurrentStage,
            _totalElapsed,
            _focusElapsed,
            isComplete);
    }
}
