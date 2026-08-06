# Architecture

> Source of truth for entities, algorithms, and business rules.

---

## 1. Entities

### 1.1 Income
Money entering the system. In `FORECAST` mode modeled as scheduled events; in `WHAT_IF` mode as a single aggregate value.

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
  - Cushion Top-up
  = Post-Cushion Remainder

Post-Cushion Remainder
  - Piggy Bank
  = Free Remainder

Free Remainder
  -> Source for daily budget calculations
```

---

## 3. Distribution Algorithm

Strict priority order:

1. **Mandatory Expenses** — fully covered from income. If `Income < Mandatory` -> **Expense Crisis**.
2. **Optional Expenses** — covered from Raw Remainder. If `Income < Mandatory + Optional` -> **Expense Crisis** (unified flag; the engine does not distinguish "can't cover mandatory" vs "covers mandatory but not optional").
3. **Cushion Top-up** — funded from Net Remainder according to the active **Criticality Level** rules.
4. **Piggy Bank** — funded from Post-Cushion Remainder, subject to target and admissibility cap.
5. **Free Remainder** — whatever is left. Source for all daily budget calculations.

> **Important:** The engine **never** auto-reduces expenses, **never** auto-withdraws from the cushion to cover a deficit, and **never** reallocates overfilled cushion excess. These are client decisions.

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
A contiguous date range defined by `periodStart` and `periodEnd` (not locked to calendar months).

### 6.2 Event Recurrence
Events support three recurrence patterns:
- `ONE_TIME` — single occurrence on a specific date.
- `EVERY_N_DAYS` — repeats every N calendar days from `startDate`.
- `EVERY_N_MONTHS` — repeats every N months on a specific day-of-month.

### 6.3 Snapshot
For a given `currentDate` inside the period:
- `daysInPeriod` — total days in the period.
- `daysElapsed` — days from `periodStart` to `currentDate` (exclusive).
- `daysRemaining` — days from `currentDate` to `periodEnd` **inclusive**. On the last day, `daysRemaining = 1`.
- `receivedIncome` — sum of income events with date <= `currentDate`.
- `pendingIncome` — sum of income events with date > `currentDate`.
- `upcomingMandatory` — sum of mandatory expense events with date > `currentDate`.
- `upcomingOptional` — sum of optional expense events with date > `currentDate`.

### 6.4 Liquidity
```
liquidOnHand = receivedIncome - alreadySpent
mustReserve  = upcomingMandatory
available    = liquidOnHand - mustReserve
```
In `FORECAST` mode, `available` is passed to the distribution engine as the effective Net Remainder.

### 6.5 Multi-Period Forecast
The engine accepts a forecast horizon (`forecastPeriods`). It builds a chain of consecutive periods, carries the ending Free Remainder of period N forward as the opening balance of period N+1, and returns a list of period results. The client decides how to interpret and display this chain.
