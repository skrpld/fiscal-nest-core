# Architecture

> Entities, algorithms, business rules, and runtime characteristics. Aligned with `FISCAL_NEST_CORE_LOCKED.md` v1.1, which prevails on any conflict.

---

## 1. Engine Components

### 1.1 Income
Money entering the system. In `FORECAST` mode modeled as scheduled `IncomeEvent`s; in `WHAT_IF` mode as a single aggregate value.

### 1.2 Mandatory Expense
Expenses that **must** be paid. Highest priority. If income cannot cover them, an **Expense Crisis** is triggered.

### 1.3 Optional Expense
Expenses that are planned but can be reduced or skipped by the user. They are subtracted after mandatory expenses. The engine **never** auto-reduces them; it only reports the crisis.

### 1.4 Safety Cushion ("Cushion")
A reserve fund with a `current` balance and a `target` amount. Funded from the **Net Remainder**. If under-funded, a **Cushion Crisis** is triggered independently.

### 1.5 Piggy Bank (Savings Jar)
A savings allocation derived from the remainder, not an external obligation. It is funded **after** the cushion top-up, from what remains of the Net Remainder.

- **Mode:** `PERCENT_OF_REMAINDER` or `FIXED_AMOUNT`.
- **Admissibility % (`admissibilityPct`):** A protective cap on how much of the post-cushion remainder can be allocated to the piggy bank. This prevents draining the free remainder to zero.
  - `FIXED_AMOUNT` example: Target = 5000, post-cushion remainder = 3145, Admissibility = 100% -> Piggy = 3145.
  - `FIXED_AMOUNT` example: Target = 5000, post-cushion remainder = 3145, Admissibility = 80% -> Piggy = 2516.
  - `FIXED_AMOUNT` example: Target = 5000, post-cushion remainder = 15000, Admissibility = 100% -> Piggy = 5000.
  - `PERCENT_OF_REMAINDER` example: Target = 20%, post-cushion remainder = 10000 -> Piggy = 2000.

### 1.6 Engine Facade (`BudgetCalculator`)
A stateless entry-point object that orchestrates the engine. It exposes two public methods:
- `calculateWhatIf(input: WhatIfInput): DistributionResult`
- `calculateForecast(input: ForecastInput): List<ForecastResult>`

The facade delegates to `InputValidator`, `CalendarEngine`, and `DistributionEngine`. It is the **only** public class intended for direct client use.

### 1.7 Numeric Precision (`DecimalUtils`)
A stateless utility object that performs all `BigDecimal` quantization. It accepts an `EngineConfig` to obtain the required `scale` and `roundingMode`. Monetary inputs are quantized to `moneyScale` on entry and every allocation is quantized when it is decided, so the remainder hierarchy adds up exactly. Ratios are compared unrounded and only quantized to `percentageScale` when reported. This guarantees deterministic, repeatable calculations.

### 1.8 Input Validation (`InputValidator`)
The single implementation of every validation rule (see §7). Data class constructors call it, so invalid instances cannot be built, and `BudgetCalculator` calls it again before every calculation, because reflection-based deserializers can create instances without running a constructor. On failure it throws `IllegalArgumentException` with a descriptive message.

---

## 2. Remainder Hierarchy

The engine computes the following chain in strict order:

```
Income
  - Mandatory Expenses
  = Raw Remainder

Raw Remainder
  - Optional Expenses
  = Net Remainder

Net Remainder
  - Cushion Top-up      <- configurable criticality levels
  = Post-Cushion Remainder

Post-Cushion Remainder
  - Piggy Bank          <- % of remainder OR fixed amount with admissibility cap
  = Free Remainder      <- source for daily budget calculations
```

All values are `BigDecimal`. Inputs are quantized to `moneyScale`, each allocation (cushion top-up, piggy bank) is quantized when decided, and all subtractions are exact, so `Net Remainder = Cushion Top-up + Piggy Bank + Free Remainder` holds to the last unit.

---

## 3. Distribution Algorithm

`DistributionEngine.distribute` is a pure function with the signature:

```kotlin
fun distribute(
    income: BigDecimal,
    mandatory: BigDecimal,
    optional: BigDecimal,
    cushionState: CushionState,
    config: EngineConfig
): DistributionResult
```

Strict priority order:

1. **Mandatory Expenses** — fully covered from income. If `Income < Mandatory` -> **Expense Crisis**.
2. **Optional Expenses** — covered from Raw Remainder. If `Income < Mandatory + Optional` -> **Expense Crisis** (unified flag; the engine does not distinguish "can't cover mandatory" vs "covers mandatory but not optional").
3. **Cushion Top-up** — funded from Net Remainder according to the active **Criticality Level** rules.
4. **Piggy Bank** — funded from Post-Cushion Remainder, subject to target and admissibility cap.
5. **Free Remainder** — whatever is left. Source for all daily budget calculations.

> **Important:** The engine **never** auto-reduces expenses, **never** auto-withdraws from the cushion to cover a deficit, and **never** reallocates overfilled cushion excess. These are client decisions.

### 3.1 Post-Distribution Cushion Balance
The `DistributionResult` reports `cushionCurrent` as the **post-distribution** balance:
```
cushionCurrent(post) = cushionCurrent(input) + cushionTopup
```
This allows the client (and the next forecast period) to know the new cushion state without manual addition.

---

## 4. Crisis Scenarios

Two **independent** Boolean flags. Both may be `true` simultaneously.

### 4.1 Expense Crisis (`expenseCrisis: Boolean`)
**Trigger:** `Income < Mandatory Expenses + Optional Expenses` (Net Remainder is negative).

**Behavior:**
- Cushion top-up = 0
- Piggy Bank = 0
- Free Remainder = Net Remainder (negative)
- `expenseDeficit` reports the shortfall amount (`|Net Remainder|`).
- The engine **does not** withdraw from the cushion to cover the deficit. The client may choose to do so, but that is outside the engine's scope.

### 4.2 Cushion Crisis (`cushionCrisis: Boolean`)
**Trigger:** Cushion fill percentage is below the active **Criticality Level** threshold.

**Behavior:**
- Engine attempts to redirect funds from Net Remainder into the cushion per the active level's rules.
- If Expense Crisis is also active, redirection is suspended (top-up = 0), but the `cushionCrisis` flag remains `true`.
- If the cushion is overfilled (`cushionCurrent > cushionTarget`), top-up is suspended. The excess stays in the cushion; no automatic overflow reallocation occurs.

---

## 5. Cushion Criticality Levels

Fully **configurable via API**. There is no hardcoded limit on the number of levels.

### 5.1 Level Structure
Each level is defined by:
- `name` — opaque string identifier (e.g., `"Critical"`, `"Warning"`).
- `maxFillPct` — upper bound of the fill-percentage range. The level applies when `fillPct < maxFillPct`.
- `topupMode` — how the desired top-up amount is calculated:
  - `PERCENT_OF_TARGET`: `desired = topupValue * cushionTarget`
  - `PERCENT_OF_REMAINDER`: `desired = topupValue * netRemainder`
- `topupValue` — `BigDecimal` percentage (e.g., `0.20` for 20%).
- `admissibilityPct` — maximum share of `netRemainder` that can be taken for the top-up (e.g., `0.80` for 80%). This protects the remainder from being fully drained.

### 5.2 Top-up Calculation
```
if netRemainder <= 0:
    topup = 0
else:
    desired = topupValue * (cushionTarget  if topupMode == PERCENT_OF_TARGET
                            else netRemainder)
    maxFromRemainder = admissibilityPct * netRemainder
    topup = min(desired, maxFromRemainder, cushionNeed, netRemainder)
```
Where `cushionNeed = max(0, cushionTarget - cushionCurrent)`.

Levels are evaluated in **ascending order of `maxFillPct`**. The first matching level wins.

### 5.3 Manual Adjustment
Manual balance changes (e.g., the user withdrew coins from the physical envelope) are applied by passing the updated `cushionCurrent` value into the next calculation call. The engine is stateless.

### 5.4 Overfill
If `cushionCurrent > cushionTarget`, the cushion is overfilled. Top-up is suspended. The excess stays in the cushion. No automatic reallocation to the piggy bank or free remainder occurs.

---

## 6. Calendar Logic (FORECAST Mode)

### 6.1 Period
A contiguous date range defined by `periodStart` and `periodEnd` (inclusive). The first period is supplied by the client via `ForecastInput`. Subsequent periods are generated automatically:

- **Calendar months.** If the first period spans a whole number of months `m` (`periodStart + m months == periodEnd + 1 day`), period `k` is `[periodStart + k*m months, periodStart + (k+1)*m months - 1 day]`. August 1–31 is followed by September 1–30 and October 1–31; a 5th-to-4th pay cycle stays on the 5th; `m = 3` gives quarters.
- **Fixed length.** Otherwise every period has the first period's length in days (e.g. two-week periods).

### 6.2 Event Recurrence
Every pattern is anchored at the event's `startDate` and limited to `[startDate, endDate]`:
- `OneTime` — single occurrence on `startDate`.
- `EveryNDays(n)` — `startDate`, then every `n` calendar days.
- `EveryNMonths(n, dayOfMonth)` — the first `dayOfMonth` on or after `startDate`, then every `n` months. If the day does not exist in a month (e.g. the 31st in February), the date is coerced to the last day of that month.

Occurrences are counted arithmetically, so an event anchored decades in the past costs the same as one starting today.

### 6.3 Snapshot (`PeriodSnapshot`, internal)
For a given `currentDate` inside the period:
- `daysInPeriod` — total days in the period (`ChronoUnit.DAYS.between(periodStart, periodEnd) + 1`).
- `daysElapsed` — days from `periodStart` to `currentDate` (exclusive, i.e. `between(start, current)`).
- `daysRemaining` — days from `currentDate` to `periodEnd` **inclusive**. On the last day, `daysRemaining = 1`.
- `receivedIncome` / `pendingIncome` — income occurrences dated `<= currentDate` / `> currentDate`.
- `paidMandatory` / `upcomingMandatory` — mandatory expense occurrences dated `<= currentDate` / `> currentDate`.
- `paidOptional` / `upcomingOptional` — optional expense occurrences dated `<= currentDate` / `> currentDate`.

### 6.4 Plan and Cash: two views of one period
Every forecast period is calculated twice, and each view treats income and expenses **symmetrically**:

| View | Field | Income | Expenses | Answers |
|------|-------|--------|----------|---------|
| **Plan** | `ForecastResult.distribution` | all income of the period + opening balance | all expenses of the period | How should this period's money be allocated? Same rules as WHAT_IF. |
| **Cash** | `ForecastResult.cashFlow` | received by `currentDate` | paid by `currentDate`; upcoming mandatory reserved | What is safe to spend right now? |

A salary that arrives after the rent is therefore not an expense crisis in the plan, while the cash view still shows the gap until payday (`available` may be negative).

```
liquidOnHand = openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent
mustReserve  = upcomingMandatory
available    = liquidOnHand - mustReserve
```

- `alreadySpent` is **unscheduled** spending (not covered by expense events), taken from `ForecastInput` for the **first period only**. Later periods are fully in the future, so it is `0`.
- Liquidity is computed by `BudgetCalculator`; it is **not** passed into `DistributionEngine`.

### 6.5 Multi-Period Forecast
The engine accepts a forecast horizon (`forecastPeriods`). It builds the chain of periods, carries state between them, and returns one result per period.

**Carry-forward rules:**
1. **Opening Balance:** Period 1 has `openingBalance = 0`. Period N (N > 1) has `openingBalance = closingBalance` of period N-1, where `closingBalance = freeRemainder - alreadySpent`. A negative value carries a deficit into the next plan.
2. **Cushion State:** After each period: `nextCushionCurrent = cushionCurrent(post-distribution) = cushionCurrent(input) + cushionTopup`.
3. **Already Spent:** Applies only to period 1. For N > 1, `alreadySpent = 0` and `currentDate = periodStart`, therefore `daysElapsed = 0` and `daysRemaining = daysInPeriod`.
4. **Income for Distribution:** `openingBalance + receivedIncome + pendingIncome` of the period.

> Future periods assume no unscheduled spending, so unspent free remainder accumulates from period to period. The projection shows the most that can be carried forward, not a spending forecast.

**Forecast Result Composition:**
`ForecastResult` does **not** inherit from `DistributionResult`. It **contains** a `DistributionResult` (plan), a `CashFlow` (cash view), daily metrics, period boundaries and balance carry-forward fields.

---

## 7. Error Handling & Validation

The engine uses **fail-fast** validation. All public entry points validate inputs before any calculation begins.

### 7.1 Validation Rules
- Monetary amounts must be non-negative. Any decimal input may have at most 1000 integer and 1000 fractional digits (protects against values like `1E+999999999` that would exhaust memory when quantized).
- Ratios (`topupValue`, `admissibilityPct`, `piggyBankTarget` in percent mode, `piggyBankAdmissibilityPct`) must be in `[0, 1]`; `maxFillPct` in `(0, 1]`.
- `moneyScale` and `percentageScale` must be in `0..1000`.
- `criticalityLevels` must be non-empty, sorted by `maxFillPct` ascending, without two equal `maxFillPct` values (compared by value, so `0.3` equals `0.30`).
- `periodStart` must not be after `periodEnd`; the period must not be longer than `Int.MAX_VALUE` days.
- `currentDate` must be within `[periodStart, periodEnd]`.
- `forecastPeriods` must be `>= 1`, and the last period must stay within the `LocalDate` range.
- Recurrence: `n >= 1`; `dayOfMonth` in `1..31`.
- Events: `startDate` must not be after `endDate`.

### 7.2 Exception Policy
- `IllegalArgumentException` is thrown for every validation failure.
- No checked exceptions are used.
- The engine does not catch runtime exceptions from `BigDecimal` or the JDK; it lets them propagate (they indicate programmer error or JVM failure, not business-rule violation).

---

## 8. Thread Safety & Statelessness

### 8.1 Stateless Design
Every engine class is stateless. All data is passed through parameters; no mutable fields are retained between calls. `EngineConfig` and all input/result classes are immutable data classes (or equivalent).

### 8.2 Thread Safety Guarantee
Because the engine holds no mutable state, all public methods are inherently thread-safe. Multiple threads may call `BudgetCalculator.calculateWhatIf` or `calculateForecast` concurrently with different inputs without synchronization.

### 8.3 No Side Effects
The engine does not perform I/O, logging, or platform-specific operations. It is a pure CPU-and-memory computation.

---

## 9. Client Separation (Golden Rule)

The engine module must never contain:
- Android SDK imports.
- Locale-specific formatters (`java.text.NumberFormat`, `java.util.Locale`).
- Logging frameworks (SLF4J, Log4J, android.util.Log, etc.).
- String-based crisis type mapping or human-readable message generation.
- Emoji or non-ASCII characters in string literals.
- Database or network code.

All localization, formatting, persistence, and UI decisions belong in the **client repository**.
