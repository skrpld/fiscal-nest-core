# FISCAL_NEST_CORE_LOCKED.md

> **Status: LOCKED — v1.0**
> This document is the single source of truth for all implementation steps (Step 2 onward).
> No further edits without a formal amendment process (return to Step 1 with a documented reason).
> Supersedes README.md / ARCHITECTURE.md / API.md / GLOSSARY.md wherever they conflict with this document.

---

## 0. Amendments from Step 0 Review

The following decisions were made during Step 0 discussion and are now binding:

| # | Topic | Resolution |
|---|-------|------------|
| 1 | Package name | Simplified to **`fiscalnest.core`** (no reverse-domain prefix). |
| 2 | Percentage scale | **All** percentage-like fields use the `0.0–1.0` scale, with no exceptions. This includes `maxFillPct` and `cushionFillPct`, which the original docs expressed as `0–100`. The `0–100` "human" scale is a client-side display concern only; the engine never stores or returns it. |
| 3 | `mandatory` / `optional` in FORECAST mode | `DistributionEngine.distribute` receives the **full period sums** of mandatory/optional expenses (both already-elapsed and upcoming), not just `upcoming*`. `PeriodSnapshot.upcomingMandatory` / `upcomingOptional` are a separate, narrower figure used only for liquidity (`available`). |
| 4 | `CushionState` | Formally specified as a public data class (previously only implied). |
| 5 | `cushionFillPct` vs `cushionCurrent` timing | Explicitly documented: `cushionFillPct` is measured **before** distribution; `cushionCurrent` in the result is the balance **after** distribution. |
| 6 | `WhatIfInput.alreadySpent` | Kept in the API for symmetry with `ForecastInput`, even though it is ignored by the engine in `WHAT_IF` mode. Rationale documented explicitly so it isn't mistaken for dead weight. |

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

`BudgetCalculator` is the **only** class/object intended for direct client use besides the data classes themselves.

### 2.2 Configuration

```kotlin
data class EngineConfig(
    val roundingMode: RoundingMode,
    val moneyScale: Int,                       // >= 0
    val percentageScale: Int,                  // >= 0
    val criticalityLevels: List<CriticalityLevel>, // non-empty, sorted by maxFillPct ascending, non-overlapping
    val piggyBankMode: PiggyBankMode,
    val piggyBankTarget: BigDecimal,           // >= 0; percentage (0.0-1.0) if PERCENT_OF_REMAINDER, fixed amount if FIXED_AMOUNT
    val piggyBankAdmissibilityPct: BigDecimal  // 0.0-1.0
)

data class CriticalityLevel(
    val name: String,
    val maxFillPct: BigDecimal,     // 0.0-1.0. Level applies when fillPct < maxFillPct. Must be > 0.
    val topupMode: TopupMode,
    val topupValue: BigDecimal,     // 0.0-1.0
    val admissibilityPct: BigDecimal // 0.0-1.0. Max share of netRemainder takeable for this level's top-up.
)

enum class TopupMode { PERCENT_OF_TARGET, PERCENT_OF_REMAINDER }
enum class PiggyBankMode { PERCENT_OF_REMAINDER, FIXED_AMOUNT }
```

> **Percentage scale rule (binding, no exceptions):** every field in this document described as a percentage — `maxFillPct`, `topupValue`, `admissibilityPct`, `piggyBankAdmissibilityPct`, `piggyBankTarget` (in `PERCENT_OF_REMAINDER` mode), and `cushionFillPct` in the output — is expressed on the `0.0–1.0` scale. There is no `0–100` representation anywhere in the public API. Converting to a `0–100` display value is entirely a client concern.

### 2.3 Event Model

```kotlin
sealed class EventRecurrence {
    data class OneTime(val date: LocalDate) : EventRecurrence()
    data class EveryNDays(val n: Int, val startDate: LocalDate) : EventRecurrence()          // n >= 1
    data class EveryNMonths(val n: Int, val dayOfMonth: Int, val startDate: LocalDate) : EventRecurrence() // n >= 1, dayOfMonth >= 1
}

data class IncomeEvent(
    val id: String,
    val name: String,
    val amount: BigDecimal,          // >= 0
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?,         // null = unbounded
    val category: String?,
    val isReliable: Boolean          // opaque to the engine; stored, never acted on
)

data class ExpenseEvent(
    val id: String,
    val name: String,
    val amount: BigDecimal,          // >= 0
    val isMandatory: Boolean,
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val category: String?
)
```

`EveryNMonths`: if `dayOfMonth` exceeds the target month's length, the date is coerced to the last day of that month.

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
    val alreadySpent: BigDecimal,     // >= 0. Ignored by the engine in WHAT_IF mode — see §2.4.1.
    val config: EngineConfig
)

data class ForecastInput(
    val incomeEvents: List<IncomeEvent>,
    val expenseEvents: List<ExpenseEvent>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,         // >= periodStart
    val currentDate: LocalDate,       // within [periodStart, periodEnd]
    val alreadySpent: BigDecimal,     // >= 0. Applies to period 1 only.
    val forecastPeriods: Int,         // >= 1
    val config: EngineConfig,
    val cushionState: CushionState    // initial state for period 1; carried forward automatically
)
```

#### 2.4.1 Why `WhatIfInput.alreadySpent` exists but is ignored

`WHAT_IF` mode is a time-agnostic snapshot — there is no `daysElapsed`/`daysRemaining` to compute a burn rate against, so the engine has nothing to do with this value. It is kept in the input shape purely so client code can pass the **same field set** into both modes without special-casing `WhatIfInput` construction. This is a deliberate API-symmetry choice, not an oversight.

### 2.5 Results

```kotlin
data class DistributionResult(
    val expenseCrisis: Boolean,                    // true if netRemainder < 0
    val cushionCrisis: Boolean,                     // true if an active criticality level was matched
    val cushionOverfilled: Boolean,                 // true if cushionCurrent(post) > cushionTarget
    val piggyBankCappedByAdmissibility: Boolean,    // true if piggy bank was reduced by the admissibility cap
    val totalIncome: BigDecimal,
    val totalMandatory: BigDecimal,
    val totalOptional: BigDecimal,
    val rawRemainder: BigDecimal,                   // income - mandatory
    val netRemainder: BigDecimal,                   // rawRemainder - optional
    val cushionTopup: BigDecimal,
    val cushionCurrent: BigDecimal,                 // POST-DISTRIBUTION: input.current + cushionTopup — see §2.5.1
    val cushionTarget: BigDecimal,                  // copied from input
    val cushionFillPct: BigDecimal,                 // PRE-DISTRIBUTION: input.current / target, scale 0.0-1.0 — see §2.5.1
    val cushionNeed: BigDecimal,                    // max(0, target - input.current)
    val piggyBankActual: BigDecimal,
    val piggyBankTarget: BigDecimal,                // computed target amount from mode + value
    val freeRemainder: BigDecimal,                  // postCushionRemainder - piggyBankActual
    val expenseDeficit: BigDecimal,                 // |netRemainder| if expenseCrisis, else 0
    val activeCriticalityLevel: String?              // name of active level, or null if fully funded
)

data class ForecastResult(
    val distribution: DistributionResult,   // composition, NOT inheritance — see §4
    val dailyMetrics: DailyMetrics,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val openingBalance: BigDecimal,         // freeRemainder carried from previous period; 0 for period 1
    val closingBalance: BigDecimal,         // = distribution.freeRemainder
    val liquidOnHand: BigDecimal,           // receivedIncome + openingBalance - alreadySpent
    val mustReserve: BigDecimal,            // = upcomingMandatory
    val available: BigDecimal,              // liquidOnHand - mustReserve
    val receivedIncome: BigDecimal,
    val pendingIncome: BigDecimal,
    val upcomingMandatory: BigDecimal,
    val upcomingOptional: BigDecimal,
    val alreadySpent: BigDecimal,           // period 1 only; 0 for N > 1
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int
)

data class DailyMetrics(
    val dailyPlan: BigDecimal,      // freeRemainder / daysInPeriod
    val dailyActual: BigDecimal,    // freeRemainder / daysRemaining
    val dailyCashflow: BigDecimal,  // (available - cushionTopup) / daysRemaining
    val burnRate: BigDecimal        // alreadySpent / (daysElapsed + 1)
)

data class PeriodSnapshot(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val receivedIncome: BigDecimal,
    val pendingIncome: BigDecimal,
    val upcomingMandatory: BigDecimal,
    val upcomingOptional: BigDecimal
)
```

#### 2.5.1 `cushionFillPct` vs `cushionCurrent` — timing (binding)

These two fields in the same `DistributionResult` describe **different moments**:

- `cushionFillPct` — the fill ratio measured **before** this distribution ran, computed from the `cushionState.current` that was passed in.
- `cushionCurrent` — the cushion balance **after** this distribution ran (`input.current + cushionTopup`).

Do not compute `cushionFillPct` from the post-distribution `cushionCurrent`; it must reflect the starting state.

---

## 3. Internal API

Marked `internal` in Kotlin. Clients must never depend on these.

```kotlin
internal object DecimalUtils {
    fun quantizeMoney(value: BigDecimal, config: EngineConfig): BigDecimal
    fun quantizePct(value: BigDecimal, config: EngineConfig): BigDecimal
    fun sum(values: List<BigDecimal>): BigDecimal
    fun requireNonNegative(value: BigDecimal, name: String)
}

internal object InputValidator {
    fun validateWhatIf(input: WhatIfInput)
    fun validateForecast(input: ForecastInput)
    fun validateConfig(config: EngineConfig)
}

internal class CalendarEngine {
    fun buildSnapshot(
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currentDate: LocalDate,
        incomeEvents: List<IncomeEvent>,
        expenseEvents: List<ExpenseEvent>,
        alreadySpent: BigDecimal
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

> **Binding constraint:** `DistributionEngine.distribute` does **not** take an `available` parameter. `available` (liquidity) is derived and consumed exclusively inside `BudgetCalculator`, after `CalendarEngine` produces a snapshot and before `DailyMetrics` are computed. `DistributionEngine` has no concept of liquidity — only the Remainder Hierarchy.

---

## 4. Composition Rule

`ForecastResult` **wraps** `DistributionResult` as a field (`val distribution: DistributionResult`). It does **not** inherit from it. This applies to any future result type that needs to combine with another: composition over inheritance, always.

---

## 5. Distribution Algorithm (exact)

Strict order. Pure function, no I/O.

```
rawRemainder = income - mandatory
netRemainder = rawRemainder - optional

if netRemainder < 0:
    expenseCrisis = true
    expenseDeficit = |netRemainder|
    cushionTopup = 0
    piggyBankActual = 0
    freeRemainder = netRemainder
    cushionCurrent(result) = cushionState.current   // unchanged, no topup applied
    cushionCrisis = (fillPct still evaluated below, independent of expenseCrisis)
    -> return

fillPct = if cushionTarget == 0: 1.0 (i.e. 100%) else cushionState.current / cushionTarget
cushionNeed = max(0, cushionTarget - cushionState.current)

activeLevel = first level in criticalityLevels (ascending maxFillPct) where fillPct < level.maxFillPct
cushionCrisis = (activeLevel != null)

if activeLevel == null or cushionState.current > cushionTarget (overfilled):
    cushionTopup = 0
else:
    desired = activeLevel.topupValue * (cushionTarget if activeLevel.topupMode == PERCENT_OF_TARGET else netRemainder)
    maxFromRemainder = activeLevel.admissibilityPct * netRemainder
    cushionTopup = min(desired, maxFromRemainder, cushionNeed, netRemainder)

cushionOverfilled = (cushionState.current > cushionTarget)
postCushionRemainder = netRemainder - cushionTopup

piggyTarget = piggyBankTarget * postCushionRemainder   if piggyBankMode == PERCENT_OF_REMAINDER
            = piggyBankTarget                            if piggyBankMode == FIXED_AMOUNT

piggyBankActual = min(piggyTarget, piggyBankAdmissibilityPct * postCushionRemainder, postCushionRemainder)
piggyBankCappedByAdmissibility = (piggyBankActual < piggyTarget)

freeRemainder = postCushionRemainder - piggyBankActual
cushionCurrent(result) = cushionState.current + cushionTopup   // post-distribution
```

All monetary intermediates are exact `BigDecimal`; quantization via `DecimalUtils` is applied only when a value is written into a result object.

---

## 6. Crisis Semantics

- `expenseCrisis` and `cushionCrisis` are **independent** booleans; both may be `true` simultaneously.
- On `expenseCrisis`: cushion top-up and piggy bank are both forced to `0`; `freeRemainder` is negative and equals `netRemainder`; the engine never touches the cushion balance to cover the deficit.
- On `cushionCrisis` with `expenseCrisis` also active: `cushionCrisis` still reports `true` (a criticality level was matched), but the actual top-up is suspended by the `expenseCrisis` early return — the two facts (crisis detected vs. redirection possible) are reported separately, no silent merging.
- Overfilled cushion (`cushionState.current > cushionTarget`): top-up suspended, excess stays in the cushion, no reallocation to piggy bank or free remainder.

---

## 7. Calendar Logic (FORECAST mode)

### 7.1 Snapshot fields (`CalendarEngine.buildSnapshot`)

- `daysInPeriod = DAYS.between(periodStart, periodEnd) + 1`
- `daysElapsed = DAYS.between(periodStart, currentDate)`
- `daysRemaining = DAYS.between(currentDate, periodEnd) + 1` (inclusive; on the last day this equals `1`)
- `receivedIncome` = sum of income events resolved to a date `<= currentDate`
- `pendingIncome` = sum of income events resolved to a date `> currentDate`
- `upcomingMandatory` = sum of mandatory expense events resolved to a date `> currentDate`
- `upcomingOptional` = sum of optional expense events resolved to a date `> currentDate`

### 7.2 What goes into `DistributionEngine.distribute` for a period (resolution of ambiguity #2)

`mandatory` and `optional` passed into `distribute` are the **full period totals** — every mandatory/optional expense event resolved within `[periodStart, periodEnd]`, regardless of whether its date is before or after `currentDate`. This is distinct from `PeriodSnapshot.upcomingMandatory` / `upcomingOptional`, which only count events **after** `currentDate` and exist solely to compute `mustReserve` / `available` for liquidity — they are never passed into `DistributionEngine`.

Rationale: distribution operates at the level of "how should this whole period's money be allocated," independent of what's already happened; liquidity (`available`) is the separate, narrower question of "what's safe to spend right now given what's still coming."

### 7.3 Recurrence resolution

- `OneTime(date)`: included if `date` is within `[periodStart, periodEnd]` and within `[startDate, endDate ?: date]`.
- `EveryNDays(n, startDate)`: dates generated by stepping `n` days from `startDate`; included if within the period and within `[startDate, endDate]`.
- `EveryNMonths(n, dayOfMonth, startDate)`: dates generated by stepping `n` months from `startDate`, landing on `dayOfMonth` (coerced to the last day of the month if the month is shorter); included if within the period and within `[startDate, endDate]`.

### 7.4 Multi-period carry-forward (binding)

1. **Opening balance:** period 1 → `openingBalance = 0`. Period `N > 1` → `openingBalance = closingBalance` of period `N-1` = `distribution.freeRemainder` of period `N-1`.
2. **Cushion state:** carried forward post-distribution. Period `N > 1` uses `cushionState.current = distribution.cushionCurrent` from period `N-1` (target is unchanged across periods).
3. **Already spent / burn rate:** applies to period 1 only. For `N > 1`, `alreadySpent = 0`, `currentDate = periodStart`, so `daysElapsed = 0` and `daysRemaining = daysInPeriod`.
4. **Income into distribution:** for period `N`, `income = snapshot.receivedIncome + openingBalance`.
5. **Next period boundaries:** `nextPeriodStart = previousPeriodEnd + 1 day`; `nextPeriodEnd = nextPeriodStart + (periodEnd - periodStart)` (same length as period 1).

### 7.5 Liquidity (computed in `BudgetCalculator`, not in `DistributionEngine`)

```
liquidOnHand = receivedIncome + openingBalance - alreadySpent
mustReserve  = upcomingMandatory
available    = liquidOnHand - mustReserve
```

---

## 8. Daily Metrics Formulas (exact)

Computed only in FORECAST mode.

| Metric | Formula |
|--------|---------|
| `dailyPlan` | `freeRemainder / daysInPeriod` |
| `dailyActual` | `freeRemainder / daysRemaining` |
| `dailyCashflow` | `(available - cushionTopup) / daysRemaining` |
| `burnRate` | `alreadySpent / (daysElapsed + 1)` |

`dailyCashflow` deliberately uses `available` (liquidity after reserving upcoming mandatory expenses), not `freeRemainder` — it is the conservative, cash-in-hand figure.

---

## 9. Validation Rules & Exact Exception Messages

Fail-fast: all validation checks run before any calculation. Every validation failure throws `IllegalArgumentException`. No checked exceptions are used.

The following messages are exact. `<name>` and `<parameter>` are replaced by the validated field or recurrence parameter name.

| Condition | Exact message |
|-----------|---------------|
| Negative monetary value | `"Amount must be non-negative: <name>"` |
| Percentage outside `[0,1]` | `"Percentage must be in [0,1]: <name>"` |
| Empty `criticalityLevels` | `"Criticality levels must be non-empty and sorted by maxFillPct ascending."` |
| Unsorted `criticalityLevels` | `"Criticality levels must be non-empty and sorted by maxFillPct ascending."` |
| Overlapping criticality ranges | `"Criticality level ranges overlap: <firstLevelName> and <secondLevelName>"` |
| `periodStart > periodEnd` | `"periodStart must not be after periodEnd"` |
| `currentDate` outside period | `"currentDate must be within [periodStart, periodEnd]"` |
| `forecastPeriods < 1` | `"forecastPeriods must be >= 1"` |
| `n <= 0` in `EveryNDays` | `"Recurrence parameter must be positive: n"` |
| `n <= 0` in `EveryNMonths` | `"Recurrence parameter must be positive: n"` |
| `dayOfMonth <= 0` in `EveryNMonths` | `"Recurrence parameter must be positive: dayOfMonth"` |
| `moneyScale < 0` | `"Scale must be >= 0: moneyScale"` |
| `percentageScale < 0` | `"Scale must be >= 0: percentageScale"` |

Additional validation rules:
- All monetary inputs must be non-negative and finite.
- `cushionState.current >= 0`.
- `cushionState.target >= 0`.
- `moneyScale >= 0`.
- `percentageScale >= 0`.
- Every percentage field in the public configuration model is validated in `[0, 1]`.
- `CriticalityLevel.maxFillPct` must be greater than `0`.
- `criticalityLevels` must be non-empty, sorted by `maxFillPct` ascending, and non-overlapping.

---

## 10. Golden Rule (unchanged from README)

The engine module must never import Android SDK, locale-specific formatters, logging frameworks, or platform-specific code. No emoji or non-ASCII characters in string literals. No database or network code. All localization, formatting, persistence, manual-adjustment workflows, and UI decisions belong to the client repository.

Code, KDoc, and all Markdown technical documentation for the engine remain **English only** — this is unaffected by the discussion-language rule in `DEVELOPMENT_PLAN.md` §"Communication Rules for Discussion Steps", which governs only how the AI and the user talk to each other during planning/discussion sessions.

---

**LOCKED — v1.0**  
No further edits are permitted without a formal amendment process.  

*End of locked specification. Proceed to Step 2.*
