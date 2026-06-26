# Focus Guard Clock Current State

Last updated: 2026-06-26

Цей документ фіксує актуальний стан прототипу, щоб не губити рішення між сесіями розробки.

## Product direction

Focus Guard Clock - перший автономний Windows-застосунок у майбутній системі Focus Guard.

Поточна ціль - зробити робочий прототип focus timer, який:

- дає користь одразу;
- не створює зайвих подвійних сповіщень;
- має зрозумілу логіку focus/break cycle;
- зберігає налаштування користувача локально;
- з часом може стати частиною більшої платформи Focus Guard.

## Technology

Поточний стек:

- C#;
- .NET;
- WinUI 3;
- Windows App SDK;
- packaged Windows app model.

Основна solution:

```text
projects/focus-guard-clock/app/FocusGuard.Clock/FocusGuard.Clock.slnx
```

Основні проєкти:

```text
FocusGuard.Clock.App   - WinUI UI
FocusGuard.Clock.Core  - бізнес-логіка таймера
FocusGuard.Clock.Tests - unit tests для core
```

## Architecture rule

Core logic не має залежати від UI.

Core відповідає за:

- розрахунок focus/break stages;
- timer transitions;
- стани `Idle`, `Running`, `Paused`, `Completed`;
- події таймера.

WinUI app відповідає за:

- відображення;
- навігацію;
- збереження settings;
- dev controls;
- майбутні sounds/notifications/history.

## Current UI

Зараз залишений один детальний prototype UI.

Ми прибрали окремий user/developer UI split. Поточний інтерфейс фактично є developer-friendly прототипом: він красивий, але показує більше технічної інформації, ніж фінальний користувацький UI.

Поточні екрани:

- `Focus session`;
- `Settings`.

Sidebar:

- зверху назва `Focus Guard` з іконкою `Admin`;
- кнопка `Focus session` зверху;
- кнопка `Settings` знизу.

Вікно використовує custom title bar:

- контент заходить у title bar area;
- область зверху можна тягнути мишкою для переміщення вікна;
- drag area не має налазити на системні кнопки minimize/maximize/close.

## Focus session page

Поточний екран `Focus session` містить:

- summary cards: focus count, breaks count, used time, unused time;
- `Current timer`;
- `Dev tools`;
- список stages.

У `Current timer` є:

- поточний статус;
- поточний stage;
- remaining time;
- focus elapsed;
- total elapsed;
- кнопки `Start`, `Pause`, `Resume`, `Reset`.

Кнопки `Stop` більше немає.

## Dev tools

У `Dev tools` зараз знаходяться:

- `Total duration`;
- `Skip breaks`;
- `Advance seconds`;
- кнопка `Advance`.

`Advance seconds` потрібна, щоб не чекати реальні хвилини під час ручного тестування.

Окремий режим `Use seconds` видалено. У UI всі duration inputs залишаються хвилинами, а core продовжує працювати з `TimeSpan`.

## Settings page

На сторінці `Settings` зараз знаходяться основні параметри focus periods:

- `Focus period`;
- `Break period`.

Кнопки `Calculate` більше немає.

Правило:

- якщо користувач змінює параметр, застосунок автоматично зберігає settings;
- після зміни параметра план автоматично перераховується.

Default values:

```text
total duration = 200 minutes
focus period = 25 minutes
break period = 10 minutes
skip breaks = false
```

## Cycle calculation rules

`total duration` означає весь доступний час циклу разом із breaks.

Якщо `Skip breaks = false`:

- breaks ставляться тільки між focus stages;
- trailing break не створюється, якщо після неї не буде наступного focus stage;
- якщо після break лишився короткий focus time, створюється короткий фінальний focus stage;
- якщо total duration менший за focus period, створюється один короткий focus stage, помилка не кидається.

Приклади:

```text
total 20, focus 20, break 5 -> Focus 20
total 30, focus 20, break 5 -> Focus 20, Break 5, Focus 5
total 45, focus 20, break 5 -> Focus 20, Break 5, Focus 20
total 50, focus 20, break 5 -> Focus 20, Break 5, Focus 20, unused 5
```

Якщо `Skip breaks = true`:

- focus/break periods не використовуються для поділу;
- створюється один суцільний focus stage на весь total duration;
- після завершення можна показати фінальне нагадування про break.

## Timer behavior

Manual start:

- переходить в перший focus stage;
- звук не відтворюється.

Pause:

- заморожує timer;
- paused time не додається до focus elapsed.

Resume:

- якщо pause був під час focus, продовжує той самий focus stage;
- якщо pause був під час break, break пропускається і запускається наступний focus stage, якщо він існує.

Reset:

- повертає застосунок в `Idle`;
- поточний незавершений period в майбутньому має записуватись як `stopped`;
- якщо це був focus, напрацьований focus time має додаватись до daily progress;
- якщо це був break, break time не додається;
- звук не відтворюється.

Stop як окрема UI-кнопка видалений.

## Daily progress and history

Повноцінні `daily progress` і `history` ще не реалізовані.

У codebase вже є підготовчі речі:

- `FocusTimerRunner.Stop()` повертає focus elapsed;
- Reset у UI використовує цей шлях як майбутню точку інтеграції з daily progress/history.

Поки що не розробляємо daily progress/history, щоб не розширювати scope прототипу.

## Future features to remember

Важливі ідеї для майбутнього:

- floating widget поверх вікон у вигляді краплі або кружечка;
- autostart with Windows;
- pause timer on lock/sleep and resume after unlock/wake;
- on-screen notifications / overlays для початку break або focus;
- eco mode / low resource usage research;
- app icon replacement через PNG assets у `FocusGuard.Clock.App/Assets`.

## App icon notes

Поточний sidebar icon використовує WinUI `SymbolIcon` зі значенням `Admin`.

Для справжньої app icon треба міняти PNG assets:

```text
FocusGuard.Clock.App/Assets/Square44x44Logo.scale-200.png
FocusGuard.Clock.App/Assets/Square150x150Logo.scale-200.png
FocusGuard.Clock.App/Assets/StoreLogo.png
```

Можна швидко взяти SVG shield/admin icon з Fluent UI System Icons, Heroicons або Lucide і експортувати потрібні PNG sizes.

## Working style

Поки що GitHub push не робимо без окремого прохання.

Користувач часто запускає build/run у Visual Studio вручну.
Якщо з'являються помилки build, користувач може надіслати output, або Codex може сам запустити:

```text
dotnet build
dotnet test
```

Перед змінами бажано перевіряти `git status`.
Після завершених логічних кроків користувач часто просить зробити локальний commit.
