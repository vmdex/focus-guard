# Focus Guard Clock User Flow

## Мета документа

Цей документ описує, як користувач проходить через основні сценарії Focus Guard Clock v0.1.

`SPEC.md` відповідає на питання "що має бути", а `USER_FLOW.md` відповідає на питання "як це поводиться під час використання".

## Базові припущення v0.1

- Якщо breaks увімкнені, користувач вводить загальну тривалість усього циклу, включно з breaks.
- Якщо breaks увімкнені, focus periods і breaks розраховуються автоматично.
- Breaks ставляться між focus periods.
- Якщо breaks вимкнені, цикл не ділиться на periods і складається з одного focus stage на весь total duration.
- Після останнього focus stage цикл завершується без автоматичного запуску break.
- Ручний старт не відтворює звук.
- Звуки відтворюються тільки при автоматичному переході в новий режим.
- Tasks, профіль користувача і синхронізація не входять у v0.1.

## Основний сценарій

### 1. Користувач відкриває застосунок

Стан: `Idle`.

Користувач бачить:

- поле або stepper для загальної тривалості циклу;
- розраховану кількість breaks;
- checkbox або toggle `Skip breaks`;
- кнопку `Start focus session`;
- блок `Daily progress`.

За замовчуванням:

- focus period береться з settings;
- break period береться з settings;
- daily goal береться з settings;
- skip breaks вимкнено.

### 2. Користувач задає загальну тривалість

Приклад:

- total duration: `200 minutes`;
- focus period: `25 minutes`;
- break period: `10 minutes`.

Застосунок розраховує:

- `6` focus periods;
- `5` breaks;
- схема: Focus, Break, Focus, Break, Focus, Break, Focus, Break, Focus, Break, Focus.

Формула:

```text
periods = floor((totalDuration + breakPeriod) / (focusPeriod + breakPeriod))
breaks = periods - 1
```

Якщо користувач увімкнув `Skip breaks`, тобто breaks вимкнені:

```text
periods = 1
breaks = 0
focus duration = totalDuration
```

У v0.1 короткий фінальний focus stage може створюватися, якщо після break лишився час.

Приклад:

```text
total duration = 30
focus period = 20
break period = 5
```

Схема:

```text
Focus 20
Break 5
Focus 5
```

Після короткого фінального focus stage застосунок показує фінальне сповіщення про break / завершення focus session.

### 3. Користувач натискає Start focus session

Стан переходить з `Idle` у `Focus`.

Важливо:

- звук не відтворюється;
- сповіщення не показується;
- починається перший focus period.

Користувач бачить:

- `Focus period (1 of N)`;
- великий залишок часу;
- круговий progress timer;
- кнопку pause;
- меню додаткових дій;
- `Up next: X min break`, якщо breaks увімкнені.

## Active focus

Поки йде focus period:

- таймер зменшується;
- progress ring оновлюється;
- daily progress ще не збільшується до завершення period;
- користувач може pause, reset або stop через доступні дії.

### Focus period завершився

Якщо попереду є break:

- поточний focus period записується в history як `completed`;
- completed focus time додається до daily progress;
- стан переходить у `Break`;
- відтворюється `Start break sound`, якщо він увімкнений;
- користувач бачить `Break`;
- показується `Up next: Focus period (next of N)`.

Якщо це останній focus period:

- поточний focus period записується в history як `completed`;
- completed focus time додається до daily progress;
- стан переходить у `Completed`;
- показується фінальне сповіщення про завершення focus session;
- `Start break sound` не відтворюється, бо автоматична break не починається;
- `Start session sound` не відтворюється.

## Break

Поки йде break:

- таймер зменшується;
- progress ring оновлюється;
- daily progress не змінюється;
- користувач може pause, skip break або stop.

### Break завершилась

Якщо попереду є наступний focus period:

- break записується в history як `completed`;
- стан переходить у `Focus`;
- відтворюється `Start session sound`, якщо він увімкнений;
- заголовок оновлюється, наприклад `Focus period (2 of 6)`;
- показується наступний `Up next`.

У v0.1 break не може бути останнім автоматичним етапом циклу, бо breaks ставляться тільки між focus periods.

## Pause / Resume

Користувач може призупинити focus period або break.

Після pause:

- стан переходить у `Paused`;
- таймер не зменшується;
- progress ring заморожений;
- primary action стає `Resume`;
- поруч показується `Reset focus session`;
- текст `Up next` замінюється на `Paused`.

Після resume:

- застосунок повертається в попередній режим: `Focus` або `Break`;
- таймер продовжується з того самого залишку;
- звук не відтворюється;
- сповіщення не показується.

## Reset focus session

`Reset focus session` доступний у paused-стані.

У v0.1 reset означає:

- поточний period повертається до початкової тривалості;
- номер focus period не змінюється;
- history не оновлюється;
- daily progress не змінюється;
- застосунок лишається paused, доки користувач не натисне resume.

Приклад:

- користувач на `Focus period (1 of 6)`;
- пройшла 1 хвилина;
- користувач натиснув pause;
- залишилось `24 min`;
- користувач натиснув reset;
- залишок знову `25 min`;
- стан лишається `Paused`.

## Skip breaks / breaks off

Якщо `Skip breaks` увімкнено до старту:

- breaks не створюються;
- цикл складається з одного focus stage на весь total duration;
- focus period setting не ділить цей час на менші periods;
- break period setting не впливає на розрахунок;
- після завершення focus stage застосунок переходить у `Completed`;
- в кінці можна показати нагадування або звук про те, що час зробити break.

Приклад:

- total duration: `200 minutes`;
- focus period: `25 minutes`;
- skip breaks: on;
- periods: `1`;
- focus duration: `200 minutes`;
- breaks: `0`.

## Stop

Користувач може зупинити поточний цикл.

У v0.1 stop означає:

- confirmation не показується;
- поточний period записується в history як `stopped`, якщо він уже був запущений;
- якщо поточний period був focus period, фактично пройдений focus time додається до daily progress;
- якщо поточний period був break, daily progress не змінюється;
- застосунок переходить у `Idle`;
- звук не відтворюється.

## Completed

Цикл завершено.

Користувач бачить:

- що focus session завершена;
- скільки focus time було завершено;
- оновлений daily progress;
- кнопку `Start new focus session`;
- кнопку `Done`;
- опціонально кнопку `Start break manually`.

Звуки:

- completed state сам по собі не має окремого звуку;
- останнім автоматичним звуком у циклі може бути `Start session sound` або `Start break sound` залежно від переходів, але завершення циклу не дублюється ще одним сигналом.
- якщо breaks увімкнені, але цикл має тільки один focus period і нуль breaks, після focus показується фінальне сповіщення про завершення focus session.

## Daily progress update

Daily progress збільшується після завершеного focus period або після stop під час focus period.

При stop під час focus period:

- додається тільки фактично пройдений focus time;
- stopped period не вважається completed;
- застосунок переходить у `Idle`.

Не додається до progress:

- paused time;
- break time;
- reset period до моменту повного завершення.

Daily progress скидається щодня у час, заданий у settings.

Streak оновлюється на основі daily goal:

- якщо completed focus time за день >= daily goal, день зараховується;
- якщо weekends excluded, вихідні не переривають streak;
- якщо weekends included, вихідні рахуються як звичайні дні.

## Звуки

У v0.1 є два типи звуків:

- `Start session sound`;
- `Start break sound`.

Правила:

- manual start -> без звуку;
- resume -> без звуку;
- reset -> без звуку;
- stop -> без звуку;
- Focus -> Break -> `Start break sound`;
- Break -> Focus -> `Start session sound`;
- Last Focus -> Completed -> без звуку;
- Skip breaks: Focus -> Completed -> break reminder / `Start break sound`, якщо увімкнено.
