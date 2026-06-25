namespace FocusGuard.Clock.Core.Models;

public sealed record FocusTimerEvent(
    FocusTimerEventKind Kind,
    CycleStage? Stage);

