# Focus Guard Clock Core Logic

## Мета

Core-шар містить бізнес-логіку, яка не залежить від WinUI.

Це потрібно, щоб:

- тестувати розрахунок focus/break cycle окремо від UI;
- не змішувати таймерну логіку з XAML;
- пізніше перевикористати логіку в іншому клієнті або backend;
- легше підтримувати правила звуків, прогресу і переходів.

## Focus cycle formula

У v0.1 користувач задає total duration як весь доступний час циклу, включно з breaks.

Приклад:

```text
total duration = 200 minutes
focus period = 25 minutes
break period = 10 minutes
```

Формула:

```text
focusPeriods = floor((totalDuration + breakPeriod) / (focusPeriod + breakPeriod))
breaks = focusPeriods - 1
```

Для прикладу:

```text
focusPeriods = floor((200 + 10) / (25 + 10))
focusPeriods = floor(210 / 35)
focusPeriods = 6

breaks = 6 - 1 = 5
```

Отримуємо:

```text
Focus, Break, Focus, Break, Focus, Break, Focus, Break, Focus, Break, Focus
```

Загальна використана тривалість:

```text
6 * 25 + 5 * 10 = 200 minutes
```

## Skip breaks

Якщо `SkipBreaks = true`, breaks не створюються.

Формула:

```text
focusPeriods = floor(totalDuration / focusPeriod)
breaks = 0
```

Приклад:

```text
total duration = 200 minutes
focus period = 25 minutes
focusPeriods = 8
```

## Неповний focus period

У v0.1 неповний focus period не створюється.

Якщо після розрахунку лишається короткий залишок, він потрапляє в `UnusedDurationMinutes`.

## Основні типи

- `FocusCycleRequest` - вхідні налаштування користувача.
- `FocusCyclePlan` - готовий розрахований план циклу.
- `CycleStage` - один етап циклу.
- `CycleStageKind` - тип етапу: `Focus` або `Break`.
- `FocusCycleCalculator` - сервіс, який будує `FocusCyclePlan`.

## Приклад використання

```csharp
var calculator = new FocusCycleCalculator();

var plan = calculator.Calculate(new FocusCycleRequest(
    TotalDurationMinutes: 200,
    FocusPeriodMinutes: 25,
    BreakPeriodMinutes: 10,
    SkipBreaks: false));

Console.WriteLine(plan.FocusPeriodCount); // 6
Console.WriteLine(plan.BreakCount);       // 5
```

