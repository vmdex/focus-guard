using FocusGuard.Clock.Core.Models;
using System;
using System.Security;
using Windows.Data.Xml.Dom;
using Windows.UI.Notifications;

namespace FocusGuard.Clock.App.Services;

/// <summary>
/// Shows Windows toast notifications for automatic timer transitions.
/// Manual user actions stay silent so the app does not duplicate the user's intent.
/// </summary>
public sealed class NotificationService
{
    public void ShowTimerTransition(FocusTimerEvent timerEvent)
    {
        var message = timerEvent.Kind switch
        {
            FocusTimerEventKind.FocusStarted when timerEvent.Stage is not null
                => CreateFocusStartedMessage(timerEvent.Stage.Duration),
            FocusTimerEventKind.BreakStarted when timerEvent.Stage is not null
                => CreateBreakStartedMessage(timerEvent.Stage.Duration),
            FocusTimerEventKind.TimerCompleted => "Finished focus session",
            _ => null
        };

        if (string.IsNullOrWhiteSpace(message))
        {
            return;
        }

        ShowToast(message);
    }

    public void ShowFocusStarted(TimeSpan duration)
    {
        ShowToast(CreateFocusStartedMessage(duration));
    }

    public void ShowBreakStarted(TimeSpan duration)
    {
        ShowToast(CreateBreakStartedMessage(duration));
    }

    public void ShowFocusFinished()
    {
        ShowToast("Finished focus session");
    }

    private static void ShowToast(string message)
    {
        var toastXml = new XmlDocument();
        toastXml.LoadXml($"""
            <toast duration="short">
              <visual>
                <binding template="ToastGeneric">
                  <text>{SecurityElement.Escape(message)}</text>
                </binding>
              </visual>
            </toast>
            """);

        var toast = new ToastNotification(toastXml);
        toast.ExpirationTime = DateTimeOffset.Now.AddSeconds(2);
        ToastNotificationManager.CreateToastNotifier().Show(toast);
    }

    private static string FormatMinutes(TimeSpan duration)
    {
        var minutes = Math.Max(1, (int)Math.Ceiling(duration.TotalMinutes));
        return $"{minutes} min";
    }

    private static string CreateFocusStartedMessage(TimeSpan duration)
    {
        return $"Started focus session {FormatMinutes(duration)}";
    }

    private static string CreateBreakStartedMessage(TimeSpan duration)
    {
        return $"Started break {FormatMinutes(duration)}";
    }
}
