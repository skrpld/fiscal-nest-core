# FISCAL_NEST_CORE_LOCKED.md

> **Status: LOCKED — v1.1**
> This document is the single source of truth for the engine.
> No further edits without a formal amendment process (a documented reason in §0, approved by the project owner).
> Supersedes README.md / ARCHITECTURE.md / API.md / GLOSSARY.md wherever they conflict with this document.

---

## 0. Amendments

### 0.1 v1.0 — Step 0 review

| # | Topic | Resolution |
|---|-------|------------|
| 1 | Package name | Simplified to **`fiscalnest.core`** (no reverse-domain prefix). |
| 2 | Percentage scale | **All** percentage-like fields use the `0.0–1.0` scale, with no exceptions. The `0–100` "human" scale is a client-side display concern only; the engine never stores or returns it. |
| 3 | `mandatory` / `optional` in FORECAST mode | Distribution receives the **full period sums** of expenses. *Extended to income by v1.1 #1.* |
| 4 | `CushionState` | Formally specified as a public data class. |
| 5 | `cushionFillPct` vs `cushionCurrent` timing | `cushionFillPct` is measured **before** distribution; `cushionCurrent` in the result is the balance **after** distribution. |
| 6 | `WhatIfInput.alreadySpent` | *Removed by v1.1 #4.* |

### 0.2 v1.1 — audit and owner decisions

| # | Topic | Resolution | Reason |
|---|-------|------------|--------|
| 1 | Plan vs cash split in FORECAST | The period **plan** (`distribution`) counts every event of the period for income **and** expenses. The **cash view** (`cashFlow`) splits income **and** expenses by date at `currentDate`. | v1.0 compared received income only against full-period expenses: a salary due after the rent produced a false expense crisis. Owner decision: if income is counted by date, expenses must be too. |
| 2 | Paid expenses in liquidity | `liquidOnHand` subtracts `paidMandatory` and `paidOptional`. | v1.0 ignored expenses already paid and overstated cash on hand. |
| 3 | `alreadySpent` | Defined as **unscheduled** spending (not covered by expense events). `closingBalance = freeRemainder - alreadySpent`; `dailyActual = (freeRemainder - alreadySpent) / daysRemaining`. | v1.0 carried money already spent into the next period and kept budgeting it per day. |
| 4 | Unused API removed | `IncomeEvent.isReliable`, event `name` / `category`, `WhatIfInput.alreadySpent`, recurrence `startDate` / `OneTime.date`, `DecimalUtils.sum`, `buildSnapshot(alreadySpent)`. `PeriodSnapshot` is internal. | Never read by the engine, duplicated by another field, or unreachable from the public API. |
| 5 | Period chaining | Periods spanning whole months chain by months anchored at the first `periodStart`; others chain by the first period's length in days. | Fixed day lengths drifted off calendar months (Aug 1–31 was followed by Sep 1 – Oct 1). |
| 6 | Money quantization | Inputs are quantized to `moneyScale`; each allocation is quantized when decided. | Quantizing every result field independently could break `netRemainder = topup + piggy + free` by one unit. |
| 7 | Validation | One implementation (`InputValidator`) used by constructors and by `BudgetCalculator`; new rules in §9. | Duplicated checks had already diverged; `BigDecimal` `==` is scale-sensitive; unbounded exponents allowed a denial of service. |
| 8 | Recommended `percentageScale` | `4` (two decimals once displayed as `0–100`). | With the `0.0–1.0` scale, `percentageScale = 1` rounds `0.25` to `0.3`. |

---

## 1. Package

```
fiscalnest.core
```

---

## 2. Public API Surface

### 2.1 Facade

```kotlin
object BudgetCalculator {
    fun calculateWhatIf(input: WhatIfInput): DistributionResult
    fun calculateForecast(input: ForecastInput): List<ForecastResult>
}
```

`BudgetCalculator` is the **only** object intended for direct client use besides the data classes themselves.

### 2.2 Configuration

```kotlin
data class EngineConfig(
    val roundingMode: RoundingMode,
    val moneyScale: Int,                           // 0..1000; 2 for cents
    val percentageScale: Int,                      // 0..1000; 4 recommended
    val criticalityLevels: List<CriticalityLevel>, // non-empty, sorted by maxFillPct ascending, no duplicates
    val piggyBankMode: PiggyBankMode,
    val piggyBankTarget: BigDecimal,               // >= 0; ratio 0.0-1.0 if PERCENT_OF_REMAINDER, amount if FIXED_AMOUNT
    val piggyBankAdmissibilityPct: BigDecimal      // 0.0-1.0
)

data class CriticalityLevel(
    val name: String,
    val maxFillPct: BigDecimal,      // (0.0, 1.0]. Level applies when fillPct < maxFillPct.
    val topupMode: TopupMode,
    val topupValue: BigDecimal,      // 0.0-1.0
    val admissibilityPct: BigDecimal // 0.0-1.0. Max share of netRemainder takeable for this level's top-up.
)

enum class TopupMode { PERCENT_OF_TARGET, PERCENT_OF_REMAINDER }
enum class PiggyBankMode { PERCENT_OF_REMAINDER, FIXED_AMOUNT }
```

> **Percentage scale rule (binding, no exceptions):** every percentage-like field — `maxFillPct`, `topupValue`, `admissibilityPct`, `piggyBankAdmissibilityPct`, `piggyBankTarget` (in `PERCENT_OF_REMAINDER` mode), and `cushionFillPct` in the output — is a ratio on the `0.0–1.0` scale. Calculations always use ratios. Converting to a `0–100` display value is entirely a client concern.

### 2.3 Event Model

```kotlin
sealed class EventRecurrence {
    data object OneTime : EventRecurrence()
    data class EveryNDays(val n: Int) : EventRecurrence()                     // n >= 1
    data class EveryNMonths(val n: Int, val dayOfMonth: Int) : EventRecurrence() // n >= 1, dayOfMonth in 1..31
}

data class IncomeEvent(
    val id: String,                  // opaque; for client-side correlation only
    val amount: BigDecimal,          // >= 0, per occurrence
    val recurrence: EventRecurrence,
    val startDate: LocalDate,        // anchors the recurrence
    val endDate: LocalDate?          // >= startDate; null = unbounded
)

data class ExpenseEvent(
    val id: String,
    val amount: BigDecimal,
    val isMandatory: Boolean,
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?
)
```

Display data such as names, categories or reliability flags stays in the client, keyed by `id`.

### 2.4 State & Inputs

```kotlin
data class CushionState(
    val current: BigDecimal,   // >= 0
    val target: BigDecimal     // >= 0
)

data class WhatIfInput(
    val income: BigDecimal,           // >= 0
    val mandatory: BigDecimal,        // >= 0
    val optional: BigDecimal,         // >= 0
    val cushionState: CushionState,
    val config: EngineConfig
)

data class ForecastInput(
    val incomeEvents: List<IncomeEvent>,
    val expenseEvents: List<ExpenseEvent>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,         // >= periodStart
    val currentDate: LocalDate,       // within [periodStart, periodEnd]
    val alreadySpent: BigDecimal,     // >= 0. Unscheduled spending in period 1 up to and including currentDate.
    val forecastPeriods: Int,         // >= 1
    val config: EngineConfig,
    val cushionState: CushionState    // state at the start of period 1; carried forward automatically
)
```

### 2.5 Results

```kotlin
data class DistributionResult(
    val expenseCrisis: Boolean,                     // netRemainder < 0
    val cushionCrisis: Boolean,                     // an active criticality level was matched
    val cushionOverfilled: Boolean,                 // cushion balance > cushionTarget
    val piggyBankCappedByAdmissibility: Boolean,    // piggyBankActual < piggyBankTarget; false during expenseCrisis
    val totalIncome: BigDecimal,
    val totalMandatory: BigDecimal,
    val totalOptional: BigDecimal,
    val rawRemainder: BigDecimal,                   // income - mandatory
    val netRemainder: BigDecimal,                   // rawRemainder - optional
    val cushionTopup: BigDecimal,
    val cushionCurrent: BigDecimal,                 // POST-DISTRIBUTION: input.current + cushionTopup
    val cushionTarget: BigDecimal,
    val cushionFillPct: BigDecimal,                 // PRE-DISTRIBUTION ratio 0.0-1.0+; 1 when target = 0
    val cushionNeed: BigDecimal,                    // max(0, target - input.current)
    val piggyBankActual: BigDecimal,
    val piggyBankTarget: BigDecimal,                // amount requested by the configuration
    val freeRemainder: BigDecimal,                  // postCushionRemainder - piggyBankActual
    val expenseDeficit: BigDecimal,                 // -netRemainder if expenseCrisis, else 0
    val activeCriticalityLevel: String?             // name of the active level, or null
)

data class ForecastResult(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,             // input currentDate for period 1, periodStart for N > 1
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val openingBalance: BigDecimal,         // previous closingBalance; 0 for period 1
    val closingBalance: BigDecimal,         // distribution.freeRemainder - cashFlow.alreadySpent
    val distribution: DistributionResult,   // PLAN: whole-period totals (composition, NOT inheritance — see §4)
    val cashFlow: CashFlow,                 // CASH: by date as of currentDate
    val dailyMetrics: DailyMetrics
)

data class CashFlow(
    val receivedIncome: BigDecimal,     // income dated <= currentDate
    val pendingIncome: BigDecimal,      // income dated > currentDate
    val paidMandatory: BigDecimal,      // mandatory expenses dated <= currentDate
    val upcomingMandatory: BigDecimal,  // mandatory expenses dated > currentDate
    val paidOptional: BigDecimal,       // optional expenses dated <= currentDate
    val upcomingOptional: BigDecimal,   // optional expenses dated > currentDate
    val alreadySpent: BigDecimal,       // period 1 only; 0 for N > 1
    val liquidOnHand: BigDecimal,       // openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent
    val mustReserve: BigDecimal,        // = upcomingMandatory
    val available: BigDecimal           // liquidOnHand - mustReserve
)

data class DailyMetrics(
    val dailyPlan: BigDecimal,      // freeRemainder / daysInPeriod
    val dailyActual: BigDecimal,    // (freeRemainder - alreadySpent) / daysRemaining
    val dailyCashflow: BigDecimal,  // (available - cushionTopup) / daysRemaining
    val burnRate: BigDecimal        // alreadySpent / (daysElapsed + 1)
)
```

#### 2.5.1 `cushionFillPct` vs `cushionCurrent` — timing (binding)

- `cushionFillPct` — the fill ratio **before** this distribution, computed from the `cushionState.current` that was passed in.
- `cushionCurrent` — the cushion balance **after** this distribution (`input.current + cushionTopup`).

---

## 3. Internal API

Marked `internal` in Kotlin. Clients must never depend on these.

```kotlin
internal object DecimalUtils {
    fun quantizeMoney(value: BigDecimal, config: EngineConfig): BigDecimal
    fun quantizePct(value: BigDecimal, config: EngineConfig): BigDecimal
    fun perDay(value: BigDecimal, days: Int, config: EngineConfig): BigDecimal
}

internal object InputValidator {
    fun validateWhatIf(input: WhatIfInput)
    fun validateForecast(input: ForecastInput)
    fun validateConfig(config: EngineConfig)
    fun validateCriticalityLevel(level: CriticalityLevel)
    fun validateCushionState(state: CushionState)
    fun validateIncomeEvent(event: IncomeEvent)
    fun validateExpenseEvent(event: ExpenseEvent)
    fun validateRecurrence(recurrence: EventRecurrence)
}

internal class PeriodSchedule(firstStart: LocalDate, firstEnd: LocalDate) {
    fun period(index: Int): Period
}

internal class CalendarEngine {
    fun buildSnapshot(
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currentDate: LocalDate,
        incomeEvents: List<IncomeEvent>,
        expenseEvents: List<ExpenseEvent>
    ): PeriodSnapshot
}

internal class DistributionEngine {
    fun distribute(
        income: BigDecimal,
        mandatory: BigDecimal,
        optional: BigDecimal,
        cushionState: CushionState,
        config: EngineConfig
    ): DistributionResult
}
```

> **Binding constraint:** `DistributionEngine.distribute` does **not** take an `available` parameter. Liquidity is derived and consumed exclusively inside `BudgetCalculator`. `DistributionEngine` has no concept of dates or liquidity — only the Remainder Hierarchy.

---

## 4. Composition Rule

`ForecastResult` **wraps** `DistributionResult` and `CashFlow` as fields. It does **not** inherit from them. Composition over inheritance, always.

---

## 5. Distribution Algorithm (exact)

Strict order. Pure function, no I/O. `q(x)` = quantize to `moneyScale` with `roundingMode`.

```
income, mandatory, optional, current, target = q(each input)

rawRemainder = income - mandatory
netRemainder = rawRemainder - optional
expenseCrisis = netRemainder < 0

fillPct = if target == 0: 1 else current / target           // DECIMAL128, compared unrounded
cushionNeed = max(0, target - current)
activeLevel = first level (ascending maxFillPct) with fillPct < level.maxFillPct
cushionCrisis = activeLevel != null                          // evaluated even during expenseCrisis
cushionOverfilled = current > target

if expenseCrisis or activeLevel == null:                     // an overfilled cushion never matches a level
    cushionTopup = 0
else:
    desired = activeLevel.topupValue * (target if PERCENT_OF_TARGET else netRemainder)
    cushionTopup = q(min(desired, activeLevel.admissibilityPct * netRemainder, cushionNeed, netRemainder))

postCushionRemainder = netRemainder - cushionTopup

piggyTarget = piggyBankTarget * max(0, postCushionRemainder)   if PERCENT_OF_REMAINDER
            = piggyBankTarget                                  if FIXED_AMOUNT
piggyBankActual = 0 if expenseCrisis
                  else q(min(piggyTarget, piggyBankAdmissibilityPct * postCushionRemainder, postCushionRemainder))
piggyBankCappedByAdmissibility = not expenseCrisis and piggyBankActual < q(piggyTarget)

freeRemainder = postCushionRemainder - piggyBankActual
expenseDeficit = -netRemainder if expenseCrisis else 0
cushionCurrent(result) = current + cushionTopup
cushionFillPct(result) = fillPct quantized to percentageScale
```

Invariant: `netRemainder == cushionTopup + piggyBankActual + freeRemainder` exactly.

`income` may be negative in FORECAST mode when a deficit is carried in; it is then an expense crisis.

---

## 6. Crisis Semantics

- `expenseCrisis` and `cushionCrisis` are **independent** booleans; both may be `true` simultaneously.
- On `expenseCrisis`: cushion top-up and piggy bank are both `0`; `freeRemainder` equals `netRemainder` (negative); the engine never touches the cushion balance to cover the deficit.
- On `cushionCrisis` with `expenseCrisis`: `cushionCrisis` still reports `true`, but no top-up is made — detection and redirection are reported separately.
- Overfilled cushion: top-up suspended, excess stays in the cushion, no reallocation.

---

## 7. Calendar Logic (FORECAST mode)

### 7.1 Snapshot (`CalendarEngine.buildSnapshot`)

- `daysInPeriod = DAYS.between(periodStart, periodEnd) + 1`
- `daysElapsed = DAYS.between(periodStart, currentDate)`
- `daysRemaining = DAYS.between(currentDate, periodEnd) + 1` (inclusive; `1` on the last day)
- `receivedIncome` / `pendingIncome` — income occurrences dated `<= currentDate` / `> currentDate`
- `paidMandatory` / `upcomingMandatory` — mandatory expense occurrences dated `<= currentDate` / `> currentDate`
- `paidOptional` / `upcomingOptional` — optional expense occurrences dated `<= currentDate` / `> currentDate`

### 7.2 Plan vs cash (binding, v1.1 #1)

- **Plan:** `distribute(income = openingBalance + receivedIncome + pendingIncome, mandatory = paidMandatory + upcomingMandatory, optional = paidOptional + upcomingOptional)`. Income and expenses are compared on the same whole-period basis, exactly like WHAT_IF.
- **Cash:** both income and expenses are split by date (§7.5). It answers "what is safe to spend right now".

### 7.3 Recurrence resolution

Every pattern is anchored at the event's `startDate` and limited to `[startDate, endDate]`.

- `OneTime`: a single occurrence on `startDate`.
- `EveryNDays(n)`: `startDate + k*n days`, `k >= 0`.
- `EveryNMonths(n, dayOfMonth)`: the first occurrence is the first `dayOfMonth` on or after `startDate`; then every `n` months. A `dayOfMonth` beyond the month length is coerced to the month's last day.

Occurrences are counted arithmetically; cost does not depend on how far in the past an event starts.

### 7.4 Multi-period carry-forward (binding)

1. **Opening balance:** period 1 → `0`. Period `N > 1` → `closingBalance` of period `N-1`, where `closingBalance = freeRemainder - alreadySpent`. A negative value carries a deficit.
2. **Cushion state:** period `N > 1` uses `cushionState.current = distribution.cushionCurrent` of period `N-1`; the target is unchanged.
3. **Already spent:** period 1 only. For `N > 1`, `alreadySpent = 0`, `currentDate = periodStart`, so `daysElapsed = 0` and `daysRemaining = daysInPeriod`.
4. **Period boundaries:** let `nextStart = periodEnd + 1 day` of the first period and `m` the number of months from `periodStart` to `nextStart`. If `m >= 1` and `periodStart + m months == nextStart`, period `k` is `[periodStart + k*m months, periodStart + (k+1)*m months - 1 day]`. Otherwise every period has the first period's length in days.

### 7.5 Liquidity (computed in `BudgetCalculator`, not in `DistributionEngine`)

```
liquidOnHand = openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent
mustReserve  = upcomingMandatory
available    = liquidOnHand - mustReserve
```

---

## 8. Daily Metrics Formulas (exact)

Computed only in FORECAST mode; each quotient is rounded to `moneyScale` with `roundingMode`.

| Metric | Formula |
|--------|---------|
| `dailyPlan` | `freeRemainder / daysInPeriod` |
| `dailyActual` | `(freeRemainder - alreadySpent) / daysRemaining` |
| `dailyCashflow` | `(available - cushionTopup) / daysRemaining` |
| `burnRate` | `alreadySpent / (daysElapsed + 1)` |

`dailyCashflow` deliberately uses `available` (cash after reserving upcoming mandatory expenses), not `freeRemainder` — it is the conservative, cash-in-hand figure and may be negative before payday.

---

## 9. Validation Rules & Exact Exception Messages

Fail-fast: data class constructors and both `BudgetCalculator` entry points run the same checks (`InputValidator`). The facade re-runs them because deserializers can create instances without calling a constructor. Every failure throws `IllegalArgumentException`.

| Condition | Exact message |
|-----------|---------------|
| Negative monetary value | `"Amount must be non-negative: <name>"` |
| Percentage outside `[0,1]` | `"Percentage must be in [0,1]: <name>"` |
| `maxFillPct <= 0` | `"Percentage must be in (0,1]: maxFillPct"` |
| More than 1000 integer or fractional digits | `"Number exceeds supported precision: <name>"` |
| Empty `criticalityLevels` | `"Criticality levels must be non-empty and sorted by maxFillPct ascending."` |
| Unsorted `criticalityLevels` | `"Criticality levels must be non-empty and sorted by maxFillPct ascending."` |
| Two levels with equal `maxFillPct` (compared by value) | `"Criticality level ranges overlap: <firstLevelName> and <secondLevelName>"` |
| `moneyScale` / `percentageScale` `< 0` | `"Scale must be >= 0: <name>"` |
| `moneyScale` / `percentageScale` `> 1000` | `"Scale must be <= 1000: <name>"` |
| `periodStart > periodEnd` | `"periodStart must not be after periodEnd"` |
| `currentDate` outside period | `"currentDate must be within [periodStart, periodEnd]"` |
| Period longer than `Int.MAX_VALUE` days | `"Period length must not exceed 2147483647 days"` |
| `forecastPeriods < 1` | `"forecastPeriods must be >= 1"` |
| Last forecast period outside the `LocalDate` range | `"Forecast horizon exceeds the supported date range"` |
| `n <= 0` | `"Recurrence parameter must be positive: n"` |
| `dayOfMonth <= 0` | `"Recurrence parameter must be positive: dayOfMonth"` |
| `dayOfMonth > 31` | `"Recurrence parameter must be <= 31: dayOfMonth"` |
| Event `startDate > endDate` | `"startDate must not be after endDate"` |

Field names in messages are the property names (`income`, `cushionCurrent`, `cushionTarget`, `amount`, `topupValue`, ...).

The digit limit rejects values such as `1E+999999999`, whose quantization would allocate gigabytes; it is far above any realistic amount. Cost and result size of `calculateForecast` grow linearly with `forecastPeriods` and the number of events — clients exposing these to untrusted input must bound them.

---

## 10. Golden Rule (unchanged from README)

The engine module must never import Android SDK, locale-specific formatters, logging frameworks, or platform-specific code. No emoji or non-ASCII characters in string literals. No database or network code. All localization, formatting, persistence, manual-adjustment workflows, and UI decisions belong to the client repository.

Code, KDoc, and all Markdown technical documentation for the engine remain **English only**.

---

**LOCKED — v1.1**
No further edits are permitted without a formal amendment process.
