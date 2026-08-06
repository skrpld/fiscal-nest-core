# API Contract

> Source of truth for public API, configuration, and output data structures.

---

## 1. Configuration API

All behavior is configured through a single `EngineConfig` object passed on every calculation call.

| Field | Type | Description |
|-------|------|-------------|
| `roundingMode` | `RoundingMode` | e.g., `HALF_UP` |
| `moneyScale` | `Int` | Decimal places for money (default: 2). |
| `percentageScale` | `Int` | Decimal places for percentages (default: 1). |
| `criticalityLevels` | `List<CriticalityLevel>` | Ordered list of user-defined levels. Must be sorted by `maxFillPct` ascending. |
| `piggyBankMode` | `PiggyBankMode` | `PERCENT_OF_REMAINDER` or `FIXED_AMOUNT`. |
| `piggyBankTarget` | `BigDecimal` | Target percentage (0.0-1.0) or fixed amount. |
| `piggyBankAdmissibilityPct` | `BigDecimal` | 0.0 - 1.0. Max share of post-cushion remainder allocatable to piggy bank. |

### CriticalityLevel

| Field | Type | Description |
|-------|------|-------------|
| `name` | `String` | Opaque identifier. |
| `maxFillPct` | `BigDecimal` | Upper bound. Level applies when `fillPct < maxFillPct`. |
| `topupMode` | `TopupMode` | `PERCENT_OF_TARGET` or `PERCENT_OF_REMAINDER`. |
| `topupValue` | `BigDecimal` | Percentage to redirect (0.0-1.0). |
| `admissibilityPct` | `BigDecimal` | Max share of netRemainder that can be taken (0.0-1.0). |

---

## 2. Entry Points

```kotlin
object BudgetCalculator {
    fun calculateWhatIf(input: WhatIfInput): DistributionResult
    fun calculateForecast(input: ForecastInput): List<ForecastResult>
}
```

### WhatIfInput

| Field | Type | Description |
|-------|------|-------------|
| `income` | `BigDecimal` | Total income for the period. |
| `mandatory` | `BigDecimal` | Total mandatory expenses. |
| `optional` | `BigDecimal` | Total optional expenses. |
| `cushionState` | `CushionState` | Current and target cushion balance. |
| `alreadySpent` | `BigDecimal` | Amount already spent (for burn rate). |
| `config` | `EngineConfig` | Engine configuration. |

### ForecastInput

| Field | Type | Description |
|-------|------|-------------|
| `incomeEvents` | `List<IncomeEvent>` | Scheduled income events. |
| `expenseEvents` | `List<ExpenseEvent>` | Scheduled expense events. |
| `periodStart` | `LocalDate` | First day of the first period. |
| `currentDate` | `LocalDate` | Current date within the first period. |
| `alreadySpent` | `BigDecimal` | Amount already spent before currentDate. |
| `forecastPeriods` | `Int` | Number of periods to project forward. |
| `config` | `EngineConfig` | Engine configuration. |

---

## 3. Output Contract

The engine returns **pure data structures**. No localized strings, no emojis, no currency symbols, no human-readable messages.

### 3.1 Flags

- `expenseCrisis: Boolean`
- `cushionCrisis: Boolean`
- `cushionOverfilled: Boolean`
- `piggyBankCappedByAdmissibility: Boolean`

### 3.2 Monetary Values

All returned as `BigDecimal` with configurable scale.

- `totalIncome`
- `totalMandatory`
- `totalOptional`
- `rawRemainder`
- `netRemainder`
- `cushionTopup`
- `cushionCurrent` (post-distribution)
- `cushionTarget`
- `cushionFillPct`
- `cushionNeed`
- `piggyBankActual`
- `piggyBankTarget`
- `freeRemainder`
- `expenseDeficit`
- `activeCriticalityLevel: String?` (null if fully funded)

### 3.3 Daily Metrics (Forecast Mode Only)

- `dailyPlan`, `dailyActual`, `dailyCashflow`, `burnRate`
- `daysInPeriod`, `daysElapsed`, `daysRemaining`
- `receivedIncome`, `pendingIncome`
- `upcomingMandatory`, `upcomingOptional`
- `alreadySpent`

### 3.4 Multi-Period Forecast Output

A list of `ForecastResult`, each containing the above fields plus:
- `periodStart`, `periodEnd`
- `openingBalance` (carried from previous period)
- `closingBalance` (Free Remainder at period end)

---

## 4. Daily Metrics Formulas

Computed only in `FORECAST` mode, derived from **Free Remainder**.

| Metric | Formula | Description |
|--------|---------|-------------|
| `dailyPlan` | `Free Remainder / daysInPeriod` | Theoretical even split across the whole period. |
| `dailyActual` | `Free Remainder / daysRemaining` | What is actually left per day from today onward. |
| `dailyCashflow` | `(liquidOnHand - mustReserve - cushionTopup) / daysRemaining` | Conservative daily budget after reserving for future mandatory obligations. |
| `burnRate` | `alreadySpent / (daysElapsed + 1)` | Average daily spending so far. |
