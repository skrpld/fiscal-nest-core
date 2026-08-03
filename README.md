# Budget Engine

> Kotlin-движок для расчёта персонального бюджета с учётом «подушки безопасности», копилки и календарного планирования доходов/расходов.

---

## Содержание

- [Обзор](#обзор)
- [Ключевые идеи простым языком](#ключевые-идеи-простым-языком)
- [Быстрый старт](#быстрый-старт)
- [Архитектура](#архитектура)
- [Ядро: алгоритм распределения](#ядро-алгоритм-распределения)
- [Режимы работы](#режимы-работы)
  - [Безвременной режим (`calculateBudget`)](#безвременной-режим-calculatebudget)
  - [Временной режим (`calculateBudgetTimed`)](#временной-режим-calculatebudgettimed)
- [Уровни критичности подушки](#уровни-критичности-подушки)
- [Daily-метрики](#daily-метрики)
- [Форматтеры](#форматтеры)
- [Точность вычислений](#точность-вычислений)

---

## Обзор

Это не просто «калькулятор доход минус расходы». Движок реализует **стратегию распределения денег** по трём направлениям:

1. **Обязательные расходы** — их нужно покрыть в первую очередь.
2. **Подушка безопасности** (cushion) — накопительный фонд на чёрный день.
3. **Копилка** (piggy bank) — целевые накопления.

Если денег не хватает на всё — движок **автоматически** перераспределяет: отменяет копилку, перенаправляет свободный остаток в подушку, а в критических ситуациях сообщает о дефиците.

---

## Ключевые идеи простым языком

### Что происходит под капотом?

Представь, что у тебя есть зарплата и список расходов. Движок делает следующее:

1. **Считает базовый остаток**: доход − обязательные − необязательные − план копилки.
2. **Смотрит на подушку**: сколько в ней сейчас и сколько должно быть.
3. **Выбирает стратегию**:
   - Если подушка полная → всё свободное идёт в копилку.
   - Если подушки мало → часть из копилки и часть свободных денег перенаправляются в подушку.
   - Если денег не хватает даже на обязательное → **кризис**, копилка и подушка отменяются.

### Временной режим — что это?

В реальной жизни зарплата приходит 5-го, а аренда списывается 10-го. Временной режим учитывает **календарь**: сколько денег уже получено, сколько ещё придёт, какие расходы уже прошли, а какие — впереди. Это позволяет считать «дневной бюджет» оставшихся дней.

---

## Быстрый старт

```kotlin
import budget.BudgetCalculator
import budget.BudgetInput

// Простой расчёт
val result = BudgetCalculator.calculateBudget(
    income = BudgetInput.Single(100_000),
    expensesMandatory = BudgetInput.Single(40_000),
    expensesOptional = BudgetInput.Single(20_000),
    piggyPlanned = BudgetInput.Single(10_000),
    cushionCurrent = 50_000,
    cushionTarget = 150_000,
)

println(result.message)
// 📊 Level: High. Cushion is filled at 33.3%. Need to add: 100000. 
// Redirected from piggy bank: 5000, from free remainder: 9000.
```

```kotlin
import budget.BudgetCalculator
import budget.BudgetInput
import budget.IncomeEvent
import budget.ExpenseEvent
import java.time.LocalDate

// Временной расчёт с графиком
val timeResult = BudgetCalculator.calculateBudgetTimed(
    incomeSchedule = listOf(
        IncomeEvent("Salary", BudgetInput.Single(80_000), 5),
        IncomeEvent("Side gig", BudgetInput.Single(20_000), 15),
    ),
    expensesMandatorySchedule = listOf(
        ExpenseEvent("Rent", BudgetInput.Single(30_000), 10),
        ExpenseEvent("Loans", BudgetInput.Single(10_000), 20),
    ),
    expensesOptionalSchedule = listOf(
        ExpenseEvent("Gym", BudgetInput.Single(3_000), 1),
    ),
    piggyPlanned = BudgetInput.Single(5_000),
    cushionCurrent = 20_000,
    cushionTarget = 100_000,
    periodStart = LocalDate.of(2026, 8, 1),
    currentDate = LocalDate.of(2026, 8, 3),
    alreadySpent = BudgetInput.Single(2_000),
)

println(timeResult.messageTime)
// 📅 Day 3 of 31. $28 days remaining. Upcoming mandatory expenses: 40000. ...
```

---

## Архитектура

```
budget/
├── BudgetCalculator.kt          ← публичный API (calculateBudget / calculateBudgetTimed)
├── BudgetInput.kt               ← sealed interface для ValueOrList
├── Types.kt                     ← BudgetResult, TimeBudgetResult
├── DecimalUtils.kt              ← BigDecimal-валидация и квантование
├── Models.kt                    ← IncomeEvent, ExpenseEvent, CriticalityLevel
├── Distribution.kt              ← ядро: DistributionContext, distribute()
├── CalendarEngine.kt            ← календарная математика, staggered events
├── Metrics.kt                   ← DailyMetrics (plan, actual, cashflow, burn)
└── formatters/
    ├── Formatter.kt             ← interface Formatter
    ├── RussianFormatter.kt      ← русские сообщения бюджета
    └── TimeMessageFormatter.kt  ← русские временные строки
```

### Компоненты

| Файл | Ответственность |
|------|-----------------|
| `BudgetCalculator.kt` | Точка входа. Собирает входные данные, вызывает ядро, форматирует результат. |
| `BudgetInput.kt` | Унифицированный вход: одно число или список чисел. Всё внутри превращается в `List<BigDecimal>`. |
| `Types.kt` | DTO-результаты: `BudgetResult` (базовый) и `TimeBudgetResult` (с календарными полями). |
| `DecimalUtils.kt` | Валидация неотрицательности, суммирование `BudgetInput`, квантование до 2 знаков (деньги) и 1 знака (проценты). |
| `Models.kt` | Модели событий (`IncomeEvent`, `ExpenseEvent`) и уровни критичности подушки (`CriticalityLevel`). |
| `Distribution.kt` | **Ядро бизнес-логики**. Алгоритм распределения с приоритетами. |
| `CalendarEngine.kt` | Построение `PeriodSnapshot`: сколько денег уже получено, сколько впереди, сколько дней осталось. |
| `Metrics.kt` | Расчёт дневных метрик: плановый бюджет, фактический, консервативный (cashflow), скорость траты (burn rate). |
| `formatters/` | Генерация человекочитаемых сообщений на русском языке. |

---

## Ядро: алгоритм распределения

Функция `distribute(ctx: DistributionContext)` работает по **приоритетам**:

### 1. Ликвидный кризис (Liquidity Crisis)
**Условие**: `liquidityAvailable < 0`

> Денег на руках не хватает даже на предстоящие обязательные расходы.

- Подушка **не пополняется**
- Копилка **отменяется**
- Возвращается отрицательный остаток = дефицит

### 2. Месячный перерасход (Overspending)
**Условие**: `monthlyBase < 0`, где `monthlyBase = income − mandatory − optional − piggyPlanned`

> Дохода не хватает на обязательные + необязательные + копилку.

- Если не хватает даже на обязательные + необязательные → **базовый дефицит**
- Если хватает на обязательные + необязательные, но не на копилку → **дефицит по копилке**
- Копилка отменяется, подушка не пополняется

### 3. Подушка полностью заполнена
**Условие**: `level == null` (текущая сумма ≥ целевой или цель = 0)

> Подушка не нуждается в пополнении.

- Весь свободный остаток идёт в копилку
- Подушка не трогается

### 4. Активное перераспределение
**Условие**: подушка неполная, денег хватает.

Движок берёт:
- `fromPiggy = piggyPlanned × piggyTakePct` — долю из планируемой копилки
- `fromRemaining = monthlyBase × remainingTakePct` — долю из свободного остатка

Сумма `plannedTopup = fromPiggy + fromRemaining` — план пополнения подушки.

**Капирование**: `actualTopup = min(plannedTopup, cushionNeed, liquidityAvailable?)`

Если запланировано больше, чем нужно или чем есть — пропорционально урезается и `fromPiggy`, и `fromRemaining`.

После пополнения подушки остаток идёт в копилку (но не больше, чем осталось свободных денег).

---

## Режимы работы

### Безвременной режим (`calculateBudget`)

Используется, когда не важно, **когда** приходят деньги и **когда** уходят расходы. Все суммы считаются агрегатами за период.

```kotlin
calculateBudget(
    income = BudgetInput.Single(100_000),           // или BudgetInput.List(listOf(50_000, 50_000))
    expensesMandatory = BudgetInput.Single(40_000),
    expensesOptional = BudgetInput.Single(20_000),
    piggyPlanned = BudgetInput.Single(10_000),
    cushionCurrent = 50_000,
    cushionTarget = 150_000,
)
```

**Что внутри:**
1. Суммирует все `BudgetInput` в `BigDecimal`
2. Создаёт `DistributionContext` без `liquidityAvailable`
3. Вызывает `distribute()`
4. Форматирует через `RussianFormatter`

### Временной режим (`calculateBudgetTimed`)

Учитывает календарь. Позволяет задать **график** событий.

```kotlin
calculateBudgetTimed(
    incomeSchedule = listOf(IncomeEvent("Salary", BudgetInput.Single(80_000), 5)),
    expensesMandatorySchedule = listOf(ExpenseEvent("Rent", BudgetInput.Single(30_000), 10)),
    expensesOptionalSchedule = listOf(ExpenseEvent("Netflix", BudgetInput.Single(500), 15)),
    piggyPlanned = BudgetInput.Single(5_000),
    cushionCurrent = 20_000,
    cushionTarget = 100_000,
    periodStart = LocalDate.of(2026, 8, 1),
    currentDate = LocalDate.of(2026, 8, 3),
    alreadySpent = BudgetInput.Single(2_000),
)
```

**Что внутри:**
1. `buildSnapshot()` — строит `PeriodSnapshot`:
   - `receivedIncome` — доходы, дата которых ≤ `currentDate`
   - `pendingIncome` — доходы, дата которых > `currentDate`
   - `upcomingMandatory` / `upcomingOptional` — расходы, дата которых > `currentDate`
   - `daysElapsed`, `daysRemaining`, `daysInPeriod`
2. Считает `liquid = receivedIncome − alreadySpent`
3. Считает `available = liquid − upcomingMandatory` (деньги, которые реально можно тратить)
4. Создаёт `DistributionContext` с `liquidityAvailable = available`
5. Вызывает `distribute()`
6. Считает `DailyMetrics` — плановый, фактический и консервативный дневной бюджет
7. Форматирует два сообщения: бюджетное (`RussianFormatter`) и временное (`TimeMessageFormatter`)

---

## Уровни критичности подушки

Определяются в `Models.kt` через `CriticalityLevel.Companion.select()`:

| Уровень | Макс. заполнение | Доля из копилки | Доля из остатка |
|---------|------------------|-----------------|-----------------|
| **Critical** | < 30% | 100% | 50% |
| **High** | < 70% | 50% | 30% |
| **Low** | < 100% | 20% | 10% |

**Логика выбора:**
```kotlin
fun select(fillPct: BigDecimal, cushionNeed: BigDecimal): CriticalityLevel? {
    if (cushionNeed <= 0) return null  // подушка полная или цель = 0
    return LEVELS.firstOrNull { fillPct < it.maxFillPct }
}
```

- Если заполнено 25% → **Critical** (забираем всю копилку и половину остатка)
- Если заполнено 50% → **High** (половину копилки и 30% остатка)
- Если заполнено 85% → **Low** (пятую часть копилки и 10% остатка)
- Если заполнено 100% или больше → `null`, режим накопления в копилку

---

## Daily-метрики

Рассчитываются в `DailyMetrics.compute()`:

| Метрика | Формула | Смысл |
|---------|---------|-------|
| **plan** | `monthlyBaseRemaining / daysInPeriod` | Сколько можно было тратить в день, если бы распределяли равномерно |
| **actual** | `remaining / daysRemaining` | Сколько можно тратить сегодня, учитывая реальный остаток |
| **cashflow** | `(liquid − upcomingMandatory − topup) / daysRemaining` | Консервативный дневной бюджет: что останется после всех обязательных расходов и пополнения подушки |
| **burnRate** | `alreadySpent / (daysElapsed + 1)` | Средняя скорость трат в день с начала периода |

---

## Форматтеры

### `RussianFormatter`
Формирует `message` в `BudgetResult` / `TimeBudgetResult`. Три режима:

- **Кризис ликвидности**: 🚨 LIQUIDITY CRISIS
- **Перерасход**: 🚨 CRISIS / ⚠️ OVERSPEND
- **Полная подушка**: ✅ Safety cushion is fully funded
- **Перераспределение**: 📊 Level: X. Cushion is filled at Y%...

### `TimeMessageFormatter`
Формирует `messageTime` только для временного режима. Пример:

```
📅 Day 3 of 31. $28 days remaining. 
Pending income: 20000. 
Upcoming mandatory expenses: 40000. 
Daily: plan 1161.29 | actual 645.16 | conservative 419.35. 
Current spending rate: 666.67/day.
```

---

## Точность вычислений

Все денежные операции выполняются на `BigDecimal` с масштабом:

- **Деньги**: 2 знака после запятой, `RoundingMode.HALF_UP`
- **Проценты**: 1 знак после запятой, `RoundingMode.HALF_UP`

`DecimalUtils` гарантирует:
- Все входные числа неотрицательные (`require(d >= 0)`)
- Поддержка `Int`, `Long`, `Double`, `Float`, `BigDecimal`
- Квантование перед возвратом в `Double` (для JSON/API-совместимости результатов)

---

## Пример полного жизненного цикла

```kotlin
// 1. Пользователь вводит данные
val income = BudgetInput.List(listOf(50000, 30000))  // зарплата + подработка
val mandatory = BudgetInput.Single(35000)             // аренда, кредиты
val optional = BudgetInput.Single(15000)              // еда, развлечения
val piggy = BudgetInput.Single(5000)                  // на отпуск
val cushionCurrent = 20000
val cushionTarget = 100000

// 2. Вызываем API
val result = BudgetCalculator.calculateBudget(
    income, mandatory, optional, piggy, cushionCurrent, cushionTarget
)

// 3. Получаем результат
println(result.fillPct)        // 20.0
println(result.cushionTopup)   // 7000.00
println(result.piggyActual)    // 5000.00
println(result.moneyRemaining) // 13000.00
println(result.message)
// 📊 Level: Critical. Cushion is filled at 20.0%. Need to add: 80000. 
// Redirected from piggy bank: 5000, from free remainder: 5000.
```

В этом примере:
- Доход = 80 000, расходы = 50 000, копилка план = 5 000 → базовый остаток = 25 000
- Подушка заполнена на 20% → уровень **Critical**
- Из копилки забираем 100% = 5 000
- Из остатка забираем 50% = 12 500, но капируем до `cushionNeed` = 80 000
- Итого пополнение подушки = 7 000 (5 000 из копилки + 2 000 из остатка)
- В копилку остаётся 0 (всё забрали)
- Свободный остаток = 25 000 − 7 000 = 18 000... 

*Точные цифры зависят от капирования и пропорций, описанных в `Distribution.kt`.*
