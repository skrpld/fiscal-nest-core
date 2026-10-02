# API Contract

> Public API, configuration, and output data structures. Aligned with `FISCAL_NEST_CORE_LOCKED.md` v1.2, which prevails on any conflict.

---

## 1. Visibility & Module Boundaries

- **Public API** — everything a client application is allowed to import. Kept intentionally small.
  - `BudgetCalculator` (object)
  - `EngineConfig`, `CriticalityLevel`, `TopupMode`, `PiggyBankMode`, `CashReserve`
  - `IncomeEvent`, `ExpenseEvent`, `EventRecurrence`
  - `CushionState`
  - `WhatIfInput`, `ForecastInput`
  - `DistributionResult`, `ForecastResult`, `CashFlow`, `DailyMetrics`
- **Internal API** — implementation details marked `internal`. Clients must not depend on these.
  - `DistributionEngine`, `CalendarEngine`, `PeriodSchedule`, `PeriodSnapshot`, `Period`
  - `DecimalUtils`, `InputValidator`

---

## 2. Configuration API

All behavior is configured through a single `EngineConfig` object passed on every calculation call. All ratios use the `0.0–1.0` scale; `0–100` is a display concern of the client.

### 2.1 `EngineConfig`

| Field | Type | Description |
|-------|------|-------------|
| `roundingMode` | `RoundingMode` | e.g., `HALF_UP`. |
| `moneyScale` | `Int` | Decimal places of money, `0..1000`. Recommended: `2`. |
| `percentageScale` | `Int` | Decimal places of reported ratios, `0..1000`. Recommended: `4` (`0.2500` → `25.00%`). |
| `criticalityLevels` | `List<CriticalityLevel>` | Non-empty, sorted by `maxFillPct` ascending, no two levels with the same `maxFillPct`. |
| `piggyBankMode` | `PiggyBankMode` | `PERCENT_OF_REMAINDER` or `FIXED_AMOUNT`. |
| `piggyBankTarget` | `BigDecimal` | Ratio `0.0–1.0` of the post-cushion remainder, or a fixed amount `>= 0`. |
| `piggyBankAdmissibilityPct` | `BigDecimal` | `0.0–1.0`. Max share of the post-cushion remainder allocatable to the piggy bank. |
| `cashReserves` | `Set<CashReserve>` | What the forecast cash view sets aside besides upcoming mandatory expenses (always reserved). Any combination of `CUSHION_TOPUP`, `PIGGY_BANK`, `UPCOMING_OPTIONAL`; may be empty. |

### 2.2 `CriticalityLevel`

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Opaque identifier, returned as `activeCriticalityLevel`. |
| `maxFillPct` | `BigDecimal` | `(0.0, 1.0]`. Level applies when `fillPct < maxFillPct`. |
| `topupMode` | `TopupMode` | `PERCENT_OF_TARGET` or `PERCENT_OF_REMAINDER`. |
| `topupValue` | `BigDecimal` | Ratio `0.0–1.0` to redirect. |
| `admissibilityPct` | `BigDecimal` | `0.0–1.0`. Max share of `netRemainder` that can be taken. |

### 2.3 Enums

```kotlin
enum class TopupMode { PERCENT_OF_TARGET, PERCENT_OF_REMAINDER }
enum class PiggyBankMode { PERCENT_OF_REMAINDER, FIXED_AMOUNT }
enum class CashReserve { CUSHION_TOPUP, PIGGY_BANK, UPCOMING_OPTIONAL }
```

| `CashReserve` | Reserved amount |
|---------------|-----------------|
| `CUSHION_TOPUP` | The period's planned `cushionTopup`. |
| `PIGGY_BANK` | The period's planned `piggyBankActual`. |
| `UPCOMING_OPTIONAL` | Optional expenses dated after `currentDate`. |

`setOf(CUSHION_TOPUP)` gives the classic conservative daily budget; an empty set reserves mandatory expenses only; all three give the strictest one.

---

## 3. Event Model

### 3.1 `EventRecurrence` (sealed class)

```kotlin
sealed class EventRecurrence {
    data object OneTime : EventRecurrence()
    data class EveryNDays(val n: Int) : EventRecurrence()
    data class EveryNMonths(val n: Int, val dayOfMonth: Int) : EventRecurrence()
}
```

Every pattern is anchored at the event's `startDate` and limited to `[startDate, endDate]`.

- `OneTime` — exactly once, on `startDate`.
- `EveryNDays` — `startDate`, then every `n` calendar days. `n >= 1`.
- `EveryNMonths` — on `dayOfMonth` every `n` months; the first occurrence is the first `dayOfMonth` on or after `startDate`. `n >= 1`, `dayOfMonth` in `1..31`. A day beyond the month length falls on the last day of that month, so `31` means "end of month".

### 3.2 `IncomeEvent`

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Opaque identifier for client-side correlation. |
| `amount` | `BigDecimal` | Amount per occurrence, `>= 0`. |
| `recurrence` | `EventRecurrence` | Recurrence rule. |
| `startDate` | `LocalDate` | First possible occurrence (inclusive); anchors the recurrence. |
| `endDate` | `LocalDate?` | Last possible occurrence (inclusive), `>= startDate`. `null` = unbounded. |

### 3.3 `ExpenseEvent`

Same fields as `IncomeEvent`, plus `isMandatory: Boolean` (`true` = mandatory, `false` = optional).

Names, categories and similar display data stay in the client, keyed by `id`.

---

## 4. Entry Points

```kotlin
object BudgetCalculator {
    fun calculateWhatIf(input: WhatIfInput): DistributionResult
    fun calculateForecast(input: ForecastInput): List<ForecastResult>
}
```

Both validate their input first and throw `IllegalArgumentException` before any calculation.

### 4.1 `WhatIfInput`

| Field | Type | Description |
|-------|------|-------------|
| `income` | `BigDecimal` | Total income for the period, `>= 0`. |
| `mandatory` | `BigDecimal` | Total mandatory expenses, `>= 0`. |
| `optional` | `BigDecimal` | Total optional expenses, `>= 0`. |
| `cushionState` | `CushionState` | Current and target cushion balance. |
| `config` | `EngineConfig` | Engine configuration. |

### 4.2 `ForecastInput`

| Field | Type | Description |
|-------|------|-------------|
| `incomeEvents` | `List<IncomeEvent>` | Scheduled income. |
| `expenseEvents` | `List<ExpenseEvent>` | Scheduled mandatory and optional expenses. |
| `periodStart` | `LocalDate` | First day of the first period. |
| `periodEnd` | `LocalDate` | Last day of the first period, `>= periodStart`. A whole number of months makes all periods follow calendar months. |
| `currentDate` | `LocalDate` | Today, within `[periodStart, periodEnd]`. |
| `alreadySpent` | `BigDecimal` | Unscheduled spending in the first period up to and including `currentDate`, `>= 0`. Do not include scheduled expense events. |
| `forecastPeriods` | `Int` | Number of periods to project, `>= 1`. Cost grows linearly. |
| `config` | `EngineConfig` | Engine configuration. |
| `cushionState` | `CushionState` | Cushion state at the start of period 1. Carried forward automatically. |

---

## 5. Output Contract

The engine returns **pure data structures**. No localized strings, no emojis, no currency symbols, no human-readable messages. Money has `moneyScale` decimals; ratios have `percentageScale` decimals on the `0.0–1.0` scale.

### 5.1 `DistributionResult`

| Field | Type | Description |
|-------|------|-------------|
| `expenseCrisis` | `Boolean` | `true` if `netRemainder < 0`. |
| `cushionCrisis` | `Boolean` | `true` if a criticality level matched the fill ratio (also during an expense crisis). |
| `cushionOverfilled` | `Boolean` | `true` if the cushion balance exceeds the target. |
| `piggyBankCappedByAdmissibility` | `Boolean` | `true` if the piggy bank got less than its target; `false` during an expense crisis. |
| `totalIncome` | `BigDecimal` | Income the distribution started from (in FORECAST: all period income, minus a carried deficit). |
| `totalMandatory` | `BigDecimal` | Mandatory expenses. |
| `totalOptional` | `BigDecimal` | Optional expenses. |
| `rawRemainder` | `BigDecimal` | `income - mandatory`. |
| `netRemainder` | `BigDecimal` | `rawRemainder - optional`. |
| `cushionTopup` | `BigDecimal` | Amount redirected to the cushion. |
| `cushionCurrent` | `BigDecimal` | **Post-distribution** balance (`input.current + topup`). |
| `cushionTarget` | `BigDecimal` | Cushion target. |
| `cushionFillPct` | `BigDecimal` | Fill ratio at the **start** of distribution (`input.current / target`, `1` if target is `0`). |
| `cushionNeed` | `BigDecimal` | `max(0, target - input.current)`. |
| `piggyBankActual` | `BigDecimal` | Amount allocated to the piggy bank. |
| `piggyBankTarget` | `BigDecimal` | Amount requested by the configuration. |
| `freeRemainder` | `BigDecimal` | `postCushionRemainder - piggyBankActual`. |
| `expenseDeficit` | `BigDecimal` | `-netRemainder` if expense crisis, else `0`. |
| `activeCriticalityLevel` | `String?` | `name` of the active level, or `null`. |

Invariant: `netRemainder == cushionTopup + piggyBankActual + freeRemainder`.

### 5.2 `ForecastResult`

Wraps two independent views of the period: the **plan** (`distribution`, whole-period totals for income and expenses alike) and the **cash view** (`cashFlow`, split by date for income and expenses alike).

| Field | Type | Description |
|-------|------|-------------|
| `periodStart` | `LocalDate` | Start of this period. |
| `periodEnd` | `LocalDate` | End of this period. |
| `currentDate` | `LocalDate` | Date of the cash view: input `currentDate` for period 1, `periodStart` afterwards. |
| `daysInPeriod` | `Int` | Total days in the period. |
| `daysElapsed` | `Int` | Days in `[periodStart, currentDate)`. |
| `daysRemaining` | `Int` | Days in `[currentDate, periodEnd]`. |
| `openingBalance` | `BigDecimal` | Previous `closingBalance` (`0` for period 1; negative carries a deficit). |
| `freeBalance` | `BigDecimal` | Free money of the period: `max(openingBalance, 0) + distribution.freeRemainder`. Carried free money stays free; it never reaches the cushion or the piggy bank. |
| `closingBalance` | `BigDecimal` | `freeBalance - cashFlow.alreadySpent`. |
| `distribution` | `DistributionResult` | Period plan of this period's own income and expenses; a carried deficit is deducted from its income. |
| `cashFlow` | `CashFlow` | Cash view. |
| `dailyMetrics` | `DailyMetrics` | Daily budget figures. |

### 5.3 `CashFlow`

| Field | Type | Description |
|-------|------|-------------|
| `receivedIncome` | `BigDecimal` | Income dated `<= currentDate`. |
| `pendingIncome` | `BigDecimal` | Income dated `> currentDate`. |
| `paidMandatory` | `BigDecimal` | Mandatory expenses dated `<= currentDate`. |
| `upcomingMandatory` | `BigDecimal` | Mandatory expenses dated `> currentDate`. |
| `paidOptional` | `BigDecimal` | Optional expenses dated `<= currentDate`. |
| `upcomingOptional` | `BigDecimal` | Optional expenses dated `> currentDate`. |
| `alreadySpent` | `BigDecimal` | Unscheduled spending (period 1 only; `0` afterwards). |
| `liquidOnHand` | `BigDecimal` | `openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent`. |
| `mustReserve` | `BigDecimal` | `upcomingMandatory` plus the reserves selected in `EngineConfig.cashReserves`. |
| `available` | `BigDecimal` | `liquidOnHand - mustReserve`. May be negative before payday. |

### 5.4 `DailyMetrics`

| Field | Type | Description |
|-------|------|-------------|
| `dailyPlan` | `BigDecimal` | Even split: `freeBalance / daysInPeriod`. |
| `dailyActual` | `BigDecimal` | Unspent free money per remaining day: `(freeBalance - alreadySpent) / daysRemaining`. |
| `dailyCashflow` | `BigDecimal` | Cash-based daily budget: `available / daysRemaining`. |
| `burnRate` | `BigDecimal` | Average unscheduled spending so far: `alreadySpent / (daysElapsed + 1)`. |

> **Note:** `dailyCashflow` uses `available` (cash after the configured reserves), not `freeBalance`. It is the cash-in-hand daily figure.

---

## 6. Exception Reference

All failures throw `IllegalArgumentException`. Exact messages are listed in `FISCAL_NEST_CORE_LOCKED.md` §9.

| Condition | Message prefix |
|-----------|----------------|
| Negative monetary value | `"Amount must be non-negative:"` |
| Ratio outside its range | `"Percentage must be in [0,1]:"` / `"Percentage must be in (0,1]:"` |
| Value with more than 1000 integer or fractional digits | `"Number exceeds supported precision:"` |
| Scale out of range | `"Scale must be >= 0:"` / `"Scale must be <= 1000:"` |
| Empty or unsorted `criticalityLevels` | `"Criticality levels must be non-empty and sorted..."` |
| Duplicate `maxFillPct` | `"Criticality level ranges overlap..."` |
| `periodStart > periodEnd` | `"periodStart must not be after periodEnd"` |
| `currentDate` outside period | `"currentDate must be within [periodStart, periodEnd]"` |
| Period too long | `"Period length must not exceed..."` |
| `forecastPeriods < 1` | `"forecastPeriods must be >= 1"` |
| Horizon beyond `LocalDate` range | `"Forecast horizon exceeds the supported date range"` |
| Invalid recurrence parameter | `"Recurrence parameter must be positive:"` / `"Recurrence parameter must be <= 31:"` |
| Event ends before it starts | `"startDate must not be after endDate"` |
