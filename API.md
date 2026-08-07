# API Contract

> Source of truth for public API, configuration, and output data structures.

---

## 1. Visibility & Module Boundaries

- **Public API** — everything a client application is allowed to import. Kept intentionally small.
  - `BudgetCalculator` (object)
  - `EngineConfig`, `CriticalityLevel`, `TopupMode`, `PiggyBankMode`
  - `IncomeEvent`, `ExpenseEvent`, `EventRecurrence`
  - `CushionState`
  - `WhatIfInput`, `ForecastInput`
  - `DistributionResult`, `ForecastResult`, `PeriodSnapshot`, `DailyMetrics`
- **Internal API** — engine implementation details. Marked `internal` in Kotlin. Clients must not depend on these.
  - `DistributionEngine`, `CalendarEngine`
  - `DecimalUtils`, `InputValidator`

---

## 2. Configuration API

All behavior is configured through a single `EngineConfig` object passed on every calculation call.

### 2.1 `EngineConfig`

| Field | Type | Description |
|-------|------|-------------|
| `roundingMode` | `RoundingMode` | e.g., `HALF_UP` |
| `moneyScale` | `Int` | Decimal places for money (default: 2). Must be `>= 0`. |
| `percentageScale` | `Int` | Decimal places for percentages (default: 1). Must be `>= 0`. |
| `criticalityLevels` | `List<CriticalityLevel>` | Ordered list of user-defined levels. Must be sorted by `maxFillPct` ascending. Non-empty. |
| `piggyBankMode` | `PiggyBankMode` | `PERCENT_OF_REMAINDER` or `FIXED_AMOUNT`. |
| `piggyBankTarget` | `BigDecimal` | Target percentage (0.0-1.0) or fixed amount. Must be `>= 0`. |
| `piggyBankAdmissibilityPct` | `BigDecimal` | 0.0 - 1.0. Max share of post-cushion remainder allocatable to piggy bank. |

### 2.2 `CriticalityLevel`

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Opaque identifier. |
| `maxFillPct` | `BigDecimal` | Upper bound. Level applies when `fillPct < maxFillPct`. Must be `> 0`. |
| `topupMode` | `TopupMode` | `PERCENT_OF_TARGET` or `PERCENT_OF_REMAINDER`. |
| `topupValue` | `BigDecimal` | Percentage to redirect (0.0-1.0). |
| `admissibilityPct` | `BigDecimal` | Max share of `netRemainder` that can be taken (0.0-1.0). |

### 2.3 Enums

```kotlin
enum class TopupMode { PERCENT_OF_TARGET, PERCENT_OF_REMAINDER }
enum class PiggyBankMode { PERCENT_OF_REMAINDER, FIXED_AMOUNT }
```

---

## 3. Event Model

### 3.1 `EventRecurrence` (sealed class)

```kotlin
sealed class EventRecurrence {
    data class OneTime(val date: LocalDate) : EventRecurrence()
    data class EveryNDays(val n: Int, val startDate: LocalDate) : EventRecurrence()
    data class EveryNMonths(val n: Int, val dayOfMonth: Int, val startDate: LocalDate) : EventRecurrence()
}
```

- `OneTime` — occurs exactly once on `date`.
- `EveryNDays` — repeats every `n` calendar days starting from `startDate`. `n` must be `>= 1`.
- `EveryNMonths` — repeats every `n` calendar months on `dayOfMonth`, starting from `startDate`. `n` must be `>= 1`, `dayOfMonth` must be `>= 1`. If the target day exceeds the month length, the date is coerced to the last day of that month.

### 3.2 `IncomeEvent`

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Opaque unique identifier. |
| `name` | `String` | Human-readable name (opaque to the engine). |
| `amount` | `BigDecimal` | Must be `>= 0`. |
| `recurrence` | `EventRecurrence` | Recurrence rule. |
| `startDate` | `LocalDate` | First possible occurrence (inclusive). |
| `endDate` | `LocalDate?` | Last possible occurrence (inclusive). `null` = unbounded. |
| `category` | `String?` | Opaque category label. |
| `isReliable` | `Boolean` | Opaque flag (engine stores it, does not act on it). |

### 3.3 `ExpenseEvent`

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Opaque unique identifier. |
| `name` | `String` | Human-readable name (opaque to the engine). |
| `amount` | `BigDecimal` | Must be `>= 0`. |
| `isMandatory` | `Boolean` | `true` = mandatory, `false` = optional. |
| `recurrence` | `EventRecurrence` | Recurrence rule. |
| `startDate` | `LocalDate` | First possible occurrence (inclusive). |
| `endDate` | `LocalDate?` | Last possible occurrence (inclusive). `null` = unbounded. |
| `category` | `String?` | Opaque category label. |

---

## 4. Entry Points

```kotlin
object BudgetCalculator {
    fun calculateWhatIf(input: WhatIfInput): DistributionResult
    fun calculateForecast(input: ForecastInput): List<ForecastResult>
}
```

### 4.1 `WhatIfInput`

| Field | Type | Description |
|-------|------|-------------|
| `income` | `BigDecimal` | Total income for the period. Must be `>= 0`. |
| `mandatory` | `BigDecimal` | Total mandatory expenses. Must be `>= 0`. |
| `optional` | `BigDecimal` | Total optional expenses. Must be `>= 0`. |
| `cushionState` | `CushionState` | Current and target cushion balance. |
| `alreadySpent` | `BigDecimal` | Amount already spent (for burn rate if client computes it manually). Must be `>= 0`. Ignored by the engine in WHAT_IF mode. |
| `config` | `EngineConfig` | Engine configuration. |

### 4.2 `ForecastInput`

| Field | Type | Description |
|-------|------|-------------|
| `incomeEvents` | `List<IncomeEvent>` | Scheduled income events. |
| `expenseEvents` | `List<ExpenseEvent>` | Scheduled expense events. |
| `periodStart` | `LocalDate` | First day of the first period. |
| `periodEnd` | `LocalDate` | Last day of the first period. Must be `>= periodStart`. |
| `currentDate` | `LocalDate` | Current date within the first period. Must be within `[periodStart, periodEnd]`. |
| `alreadySpent` | `BigDecimal` | Amount already spent before `currentDate` in the first period. Must be `>= 0`. |
| `forecastPeriods` | `Int` | Number of periods to project forward. Must be `>= 1`. |
| `config` | `EngineConfig` | Engine configuration. |
| `cushionState` | `CushionState` | Initial cushion state for period 1. Carried forward automatically. |

---

## 5. Output Contract

The engine returns **pure data structures**. No localized strings, no emojis, no currency symbols, no human-readable messages.

### 5.1 `DistributionResult`

| Field | Type | Description |
|-------|------|-------------|
| `expenseCrisis` | `Boolean` | `true` if `netRemainder < 0`. |
| `cushionCrisis` | `Boolean` | `true` if cushion fill % is below the active criticality threshold. |
| `cushionOverfilled` | `Boolean` | `true` if `cushionCurrent(post) > cushionTarget`. |
| `piggyBankCappedByAdmissibility` | `Boolean` | `true` if piggy bank was reduced by the admissibility cap. |
| `totalIncome` | `BigDecimal` | Income passed into the engine. |
| `totalMandatory` | `BigDecimal` | Mandatory expenses passed into the engine. |
| `totalOptional` | `BigDecimal` | Optional expenses passed into the engine. |
| `rawRemainder` | `BigDecimal` | `income - mandatory`. |
| `netRemainder` | `BigDecimal` | `rawRemainder - optional`. |
| `cushionTopup` | `BigDecimal` | Amount redirected to cushion. |
| `cushionCurrent` | `BigDecimal` | **Post-distribution** cushion balance (`input.current + topup`). |
| `cushionTarget` | `BigDecimal` | Cushion target (copied from input). |
| `cushionFillPct` | `BigDecimal` | Fill percentage at the **start** of distribution (`input.current / target * 100`). |
| `cushionNeed` | `BigDecimal` | `max(0, target - input.current)`. |
| `piggyBankActual` | `BigDecimal` | Actual amount allocated to piggy bank. |
| `piggyBankTarget` | `BigDecimal` | Target amount (computed from mode and value). |
| `freeRemainder` | `BigDecimal` | `postCushionRemainder - piggyBankActual`. |
| `expenseDeficit` | `BigDecimal` | `|netRemainder|` if expense crisis, else `0`. |
| `activeCriticalityLevel` | `String?` | `name` of the active level, or `null` if fully funded. |

### 5.2 `ForecastResult`

`ForecastResult` **wraps** a `DistributionResult` rather than inheriting from it.

| Field | Type | Description |
|-------|------|-------------|
| `distribution` | `DistributionResult` | Distribution output for this period. |
| `dailyMetrics` | `DailyMetrics` | Derived daily budget metrics. |
| `periodStart` | `LocalDate` | Start of this period. |
| `periodEnd` | `LocalDate` | End of this period. |
| `openingBalance` | `BigDecimal` | Free remainder carried from previous period (`0` for period 1). |
| `closingBalance` | `BigDecimal` | Free remainder at end of this period (`distribution.freeRemainder`). |
| `liquidOnHand` | `BigDecimal` | `receivedIncome + openingBalance - alreadySpent`. |
| `mustReserve` | `BigDecimal` | `upcomingMandatory`. |
| `available` | `BigDecimal` | `liquidOnHand - mustReserve`. Conservative spending capacity. |
| `receivedIncome` | `BigDecimal` | Income received up to `currentDate`. |
| `pendingIncome` | `BigDecimal` | Income scheduled after `currentDate`. |
| `upcomingMandatory` | `BigDecimal` | Mandatory expenses scheduled after `currentDate`. |
| `upcomingOptional` | `BigDecimal` | Optional expenses scheduled after `currentDate`. |
| `alreadySpent` | `BigDecimal` | Already spent amount (period 1 only; `0` for N > 1). |
| `daysInPeriod` | `Int` | Total days in the period. |
| `daysElapsed` | `Int` | Days elapsed since `periodStart`. |
| `daysRemaining` | `Int` | Days remaining until `periodEnd` inclusive. |

### 5.3 `DailyMetrics`

| Field | Type | Description |
|-------|------|-------------|
| `dailyPlan` | `BigDecimal` | Even split: `freeRemainder / daysInPeriod`. |
| `dailyActual` | `BigDecimal` | Actual per-day budget: `freeRemainder / daysRemaining`. |
| `dailyCashflow` | `BigDecimal` | Conservative daily budget: `(available - cushionTopup) / daysRemaining`. |
| `burnRate` | `BigDecimal` | Average daily spending so far: `alreadySpent / (daysElapsed + 1)`. |

> **Note:** `dailyCashflow` uses `available` (liquidity after mandatory reserve), not `freeRemainder`. This produces a conservative, cash-in-hand daily figure.

### 5.4 `PeriodSnapshot`

An intermediate structure produced by `CalendarEngine`. Exposed publicly because advanced clients may wish to inspect the calendar breakdown.

| Field | Type | Description |
|-------|------|-------------|
| `periodStart` | `LocalDate` | |
| `periodEnd` | `LocalDate` | |
| `currentDate` | `LocalDate` | |
| `daysInPeriod` | `Int` | |
| `daysElapsed` | `Int` | |
| `daysRemaining` | `Int` | |
| `receivedIncome` | `BigDecimal` | |
| `pendingIncome` | `BigDecimal` | |
| `upcomingMandatory` | `BigDecimal` | |
| `upcomingOptional` | `BigDecimal` | |

---

## 6. Daily Metrics Formulas

Computed only in `FORECAST` mode, derived from **Free Remainder** and liquidity.

| Metric | Formula | Description |
|--------|---------|-------------|
| `dailyPlan` | `freeRemainder / daysInPeriod` | Theoretical even split across the whole period. |
| `dailyActual` | `freeRemainder / daysRemaining` | What is actually left per day from today onward. |
| `dailyCashflow` | `(available - cushionTopup) / daysRemaining` | Conservative daily budget after reserving for future mandatory obligations and cushion top-up. |
| `burnRate` | `alreadySpent / (daysElapsed + 1)` | Average daily spending so far. |

---

## 7. Exception Reference

| Condition | Exception | Message prefix |
|-----------|-----------|----------------|
| Negative monetary value | `IllegalArgumentException` | `"Amount must be non-negative:"` |
| Percentage outside `[0,1]` | `IllegalArgumentException` | `"Percentage must be in [0,1]:"` |
| Empty or unsorted `criticalityLevels` | `IllegalArgumentException` | `"Criticality levels must be non-empty and sorted..."` |
| Overlapping criticality ranges | `IllegalArgumentException` | `"Criticality level ranges overlap..."` |
| `periodStart > periodEnd` | `IllegalArgumentException` | `"periodStart must not be after periodEnd"` |
| `currentDate` outside period | `IllegalArgumentException` | `"currentDate must be within [periodStart, periodEnd]"` |
| `forecastPeriods < 1` | `IllegalArgumentException` | `"forecastPeriods must be >= 1"` |
| Invalid recurrence parameter | `IllegalArgumentException` | `"Recurrence parameter must be positive:"` |
