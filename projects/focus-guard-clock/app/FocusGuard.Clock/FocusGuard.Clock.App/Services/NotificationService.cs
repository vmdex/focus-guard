using FocusGuard.Clock.Core.Models;
using System;
using System.IO;
using System.Runtime.InteropServices;
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
    private const string NotificationSoundPath = @"C:\Windows\Media\Ring02.wav";
    private const int SoundAsync = 0x0001;
    private const int SoundFileName = 0x00020000;

    private readonly object _soundLock = new();
    private bool _isSoundPlaying;

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

    private void ShowToast(string message)
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
        toast.Dismissed += (_, _) => StopCurrentSound();
        toast.Activated += (_, _) => StopCurrentSound();
        toast.Failed += (_, _) => StopCurrentSound();

        PlayNotificationSound();
        ToastNotificationManager.CreateToastNotifier().Show(toast);
    }

    private void PlayNotificationSound()
    {
        if (!File.Exists(NotificationSoundPath))
        {
            return;
        }

        lock (_soundLock)
        {
            StopCurrentSoundCore();
            _isSoundPlaying = PlaySound(NotificationSoundPath, IntPtr.Zero, SoundAsync | SoundFileName);
        }
    }

    private void StopCurrentSound()
    {
        lock (_soundLock)
        {
            StopCurrentSoundCore();
        }
    }

    private void StopCurrentSoundCore()
    {
        if (!_isSoundPlaying)
        {
            return;
        }

        PlaySound(null, IntPtr.Zero, SoundAsync);
        _isSoundPlaying = false;
    }

    [DllImport("winmm.dll", SetLastError = true)]
    private static extern bool PlaySound(string? pszSound, IntPtr hmod, int fdwSound);

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
