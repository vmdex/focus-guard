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
                => $"Started focus session {FormatMinutes(timerEvent.Stage.Duration)}",
            FocusTimerEventKind.BreakStarted when timerEvent.Stage is not null
                => $"Started break {FormatMinutes(timerEvent.Stage.Duration)}",
            FocusTimerEventKind.TimerCompleted => "Finished focus session",
            _ => null
        };

        if (string.IsNullOrWhiteSpace(message))
        {
            return;
        }

        ShowToast(message);
    }

    private static void ShowToast(string message)
    {
        var toastXml = new XmlDocument();
        toastXml.LoadXml($"""
            <toast>
              <visual>
                <binding template="ToastGeneric">
                  <text>{SecurityElement.Escape(message)}</text>
                </binding>
              </visual>
            </toast>
            """);

        var toast = new ToastNotification(toastXml);
        ToastNotificationManager.CreateToastNotifier().Show(toast);
    }

    private static string FormatMinutes(TimeSpan duration)
    {
        var minutes = Math.Max(1, (int)Math.Ceiling(duration.TotalMinutes));
        return $"{minutes} min";
    }
}
