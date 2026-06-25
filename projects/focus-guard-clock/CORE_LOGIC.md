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

Якщо в загальну тривалість вміщується тільки один focus period, trailing break не створюється.

Приклад:

```text
total duration = 20 minutes
focus period = 20 minutes
break period = 5 minutes
```

Результат:

```text
Focus 20
breaks = 0
```

Після завершення цього focus stage застосунок має показати фінальне сповіщення про завершення focus session, але не створює автоматичну break.

## Breaks off

Якщо `SkipBreaks = true`, це означає, що breaks вимкнені.

У цьому режимі:

- цикл не ділиться на focus periods;
- `focusPeriod` і `breakPeriod` не впливають на розрахунок;
- створюється один суцільний focus stage на весь `totalDuration`;
- після завершення цього focus stage застосунок може нагадати, що час зробити break.

Приклад:

```text
total duration = 20 minutes
focus period = ignored
break period = ignored
focusPeriods = 1
breaks = 0
used duration = 20 minutes
```

## Short final focus stage

У v0.1 короткий фінальний focus stage може створюватися.

Короткий focus stage також може бути єдиним stage у циклі, якщо `totalDuration`
менший за `focusPeriod`.

Приклад:

```text
total duration = 10 minutes
focus period = 25 minutes
break period = 10 minutes
```

Результат:

```text
Focus 10
```

Помилка не створюється, бо користувач усе одно може корисно попрацювати доступні 10 хвилин.

Приклад:

```text
total duration = 30 minutes
focus period = 20 minutes
break period = 5 minutes
```

Результат:

```text
Focus 20
Break 5
Focus 5
```

На 25-й хвилині починається короткий фінальний focus stage тривалістю 5 хвилин.
Після його завершення застосунок показує фінальне сповіщення про break / завершення focus session.

Якщо залишок не вміщує повну break, він не використовується як break.

Якщо після break вміщується повний focus stage, він створюється повної тривалості.

Приклад:

```text
total duration = 45 minutes
focus period = 20 minutes
break period = 5 minutes
```

Результат:

```text
Focus 20
Break 5
Focus 20
```

На 45-й хвилині застосунок показує фінальне сповіщення про break / завершення focus session, і робота завершується.

Якщо після останнього focus stage лишається час на break, але вже немає часу на наступний focus stage, trailing break не створюється.

Приклад:

```text
total duration = 50 minutes
focus period = 20 minutes
break period = 5 minutes
```

Результат:

```text
Focus 20
Break 5
Focus 20
unused = 5
```

На 45-й хвилині застосунок показує фінальне сповіщення про break / завершення focus session, і робота завершується. Останні 5 хвилин не стають break, бо після них не буде наступного focus stage.

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
