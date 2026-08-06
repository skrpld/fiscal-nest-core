# Development Plan

> Step-by-step implementation guide. Each step is a self-contained chat session.

---

## How to Use This Plan

1. Start a **new chat** for each step.
2. Paste the prompt for that step as the first message.
3. Attach `FISCAL_NEST_CORE_LOCKED.md` (or this full spec set) as context.
4. The AI will implement the step without asking clarifying questions.
5. If a step reveals a gap in the spec, note it and return to Step 1 for an amendment before proceeding.

---

## Step 1 — Requirements Lock (COMPLETE)

**Status:** DONE.  
**Deliverable:** `FISCAL_NEST_CORE_LOCKED.md` + this plan.

---

## Step 2 — Data Model & Package Setup

**Goal:** Create the package structure and all public data classes. No logic, just types.

**Prompt:**
> You are implementing Step 2 of the Fiscal Nest Core engine.
>
> **Source of truth:** `README.md` + `docs/ARCHITECTURE.md` + `docs/API.md`.
>
> **Tasks:**
> 1. Create the package `io.github.skrpld.fiscalnest.core`.
> 2. Define the following public data classes / interfaces in Kotlin:
>    - `EngineConfig` (roundingMode, moneyScale, percentageScale, criticalityLevels, piggyBankMode, piggyBankTarget, piggyBankAdmissibilityPct)
>    - `CriticalityLevel` (name, maxFillPct, topupMode: enum, topupValue, admissibilityPct)
>    - `TopupMode` enum: `PERCENT_OF_TARGET`, `PERCENT_OF_REMAINDER`
>    - `PiggyBankMode` enum: `PERCENT_OF_REMAINDER`, `FIXED_AMOUNT`
>    - `IncomeEvent` (id, name, amount: BigDecimal, recurrence, startDate, endDate, category, isReliable)
>    - `ExpenseEvent` (id, name, amount: BigDecimal, isMandatory, recurrence, startDate, endDate, category)
>    - `EventRecurrence` sealed class: `OneTime(date)`, `EveryNDays(n, startDate)`, `EveryNMonths(n, dayOfMonth, startDate)`
>    - `CushionState` (current: BigDecimal, target: BigDecimal)
>    - `DistributionResult` (all fields from API.md section 3.2 + flags from 3.1)
>    - `ForecastResult` (extends DistributionResult with daily metrics from API.md 3.3 + period dates + opening/closing balance)
>    - `PeriodSnapshot` (periodStart, periodEnd, currentDate, daysInPeriod, daysElapsed, daysRemaining, receivedIncome, pendingIncome, upcomingMandatory, upcomingOptional)
>    - `DailyMetrics` (plan, actual, cashflow, burnRate)
>    - `WhatIfInput` (income, mandatory, optional, cushionState, alreadySpent, config)
>    - `ForecastInput` (incomeEvents, expenseEvents, periodStart, currentDate, alreadySpent, forecastPeriods, config)
> 3. Use `BigDecimal` for all monetary fields. Use `Int` for counts. Use `LocalDate` from `java.time`.
> 4. Add validation in `init` blocks where appropriate (e.g., non-negative amounts, percentages in [0, 1]).
> 5. Do NOT write any calculation logic. Only data structures.
> 6. Do NOT add any formatting, messaging, or platform-specific code.
> 7. Save all files under `/mnt/agents/output/fiscal-nest-core/src/main/kotlin/io/github/skrpld/fiscalnest/core/`.
>
> **Deliverable:** A directory of Kotlin files containing only type definitions.

---

## Step 3 — Decimal & Input Layer

**Goal:** Configurable rounding, input validation, and a clean numeric utility layer.

**Prompt:**
> You are implementing Step 3 of the Fiscal Nest Core engine.
>
> **Source of truth:** `docs/API.md` (Configuration section).
>
> **Tasks:**
> 1. Create `DecimalUtils` as an object that accepts an `EngineConfig` instance for scale and rounding mode. It must provide:
>    - `quantizeMoney(value: BigDecimal, config: EngineConfig): BigDecimal`
>    - `quantizePct(value: BigDecimal, config: EngineConfig): BigDecimal`
>    - `sum(values: List<BigDecimal>): BigDecimal` (with non-negative validation)
>    - `requireNonNegative(value: BigDecimal, name: String)`
> 2. The public API accepts `BigDecimal` directly. If the client wants to pass multiple values, it sums them before calling the engine.
> 3. Create `InputValidator` with static methods to validate:
>    - All monetary inputs are non-negative and finite.
>    - `cushionTarget >= 0`, `cushionCurrent >= 0`.
>    - `piggyBankAdmissibilityPct` in [0, 1].
>    - `criticalityLevels` is non-empty and sorted by `maxFillPct` ascending.
>    - No two levels have overlapping ranges.
> 4. Save files under the same package directory.
>
> **Deliverable:** `DecimalUtils.kt`, `InputValidator.kt` — pure, configurable, validated.

---

## Step 4 — Calendar & Recurrence Engine

**Goal:** Build period snapshots with correct recurrence resolution.

**Prompt:**
> You are implementing Step 4 of the Fiscal Nest Core engine.
>
> **Source of truth:** `docs/ARCHITECTURE.md` (section 6 — Calendar Logic).
>
> **Tasks:**
> 1. Create `CalendarEngine` with a single public method:
>    ```kotlin
>    fun buildSnapshot(
>        periodStart: LocalDate,
>        periodEnd: LocalDate,
>        currentDate: LocalDate,
>        incomeEvents: List<IncomeEvent>,
>        expenseEvents: List<ExpenseEvent>,
>        alreadySpent: BigDecimal
>    ): PeriodSnapshot
>    ```
> 2. Implement recurrence resolution for all three `EventRecurrence` types:
>    - `OneTime`: include if date is within [periodStart, periodEnd].
>    - `EveryNDays`: generate dates from `startDate` stepping by `n` days, include those within the period.
>    - `EveryNMonths`: generate dates from `startDate` stepping by `n` months on the specified `dayOfMonth` (coerce to last day of month if needed), include those within the period.
> 3. For each resolved event date, compare with `currentDate`:
>    - Income: `receivedIncome` if date <= currentDate, else `pendingIncome`.
>    - Mandatory expense: `upcomingMandatory` if date > currentDate.
>    - Optional expense: `upcomingOptional` if date > currentDate.
> 4. Compute `daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1`.
> 5. Compute `daysElapsed = ChronoUnit.DAYS.between(periodStart, currentDate)`.
> 6. Compute `daysRemaining = ChronoUnit.DAYS.between(currentDate, periodEnd) + 1` (inclusive; last day = 1).
> 7. Validate that `currentDate` is within [periodStart, periodEnd].
> 8. Do NOT compute daily metrics here. Only the snapshot.
>
> **Deliverable:** `CalendarEngine.kt` — handles all recurrence patterns, correct day counts.

---

## Step 5 — Distribution Engine Core

**Goal:** Implement the full distribution algorithm with configurable criticality levels and piggy bank.

**Prompt:**
> You are implementing Step 5 of the Fiscal Nest Core engine.
>
> **Source of truth:** `docs/ARCHITECTURE.md` (sections 2-5).
>
> **Tasks:**
> 1. Create `DistributionEngine` with a public method:
>    ```kotlin
>    fun distribute(
>        income: BigDecimal,
>        mandatory: BigDecimal,
>        optional: BigDecimal,
>        cushionState: CushionState,
>        config: EngineConfig,
>        available: BigDecimal? = null  // null in WHAT_IF mode; netRemainder in FORECAST mode
>    ): DistributionResult
>    ```
> 2. Implement the Remainder Hierarchy exactly as defined in ARCHITECTURE.md section 2:
>    - `rawRemainder = income - mandatory`
>    - `netRemainder = rawRemainder - optional`
>    - If `netRemainder < 0`: set `expenseCrisis = true`, `expenseDeficit = |netRemainder|`, cushionTopup = 0, piggy = 0, freeRemainder = netRemainder. Return immediately.
> 3. If no Expense Crisis:
>    - Compute `fillPct = cushionCurrent / cushionTarget * 100` (handle target = 0 as 100%).
>    - Compute `cushionNeed = max(0, target - current)`.
>    - Select active criticality level: first level where `fillPct < maxFillPct`. If none, cushion is fully funded.
>    - If active level exists:
>      - `desired = level.topupValue * (cushionTarget if PERCENT_OF_TARGET else netRemainder)`
>      - `maxFromRemainder = level.admissibilityPct * netRemainder`
>      - `topup = min(desired, maxFromRemainder, cushionNeed, netRemainder)`
>    - If no active level: `topup = 0`.
>    - `postCushionRemainder = netRemainder - topup`
>    - Compute piggy bank:
>      - If `piggyBankMode == PERCENT_OF_REMAINDER`: `piggyTarget = piggyBankTarget * postCushionRemainder`
>      - If `piggyBankMode == FIXED_AMOUNT`: `piggyTarget = piggyBankTarget`
>      - `piggyActual = min(piggyTarget, piggyBankAdmissibilityPct * postCushionRemainder, postCushionRemainder)`
>      - `piggyBankCappedByAdmissibility = (piggyActual < piggyTarget)`
>    - `freeRemainder = postCushionRemainder - piggyActual`
>    - `cushionCrisis = (activeLevel != null)`
>    - `cushionOverfilled = (cushionCurrent > cushionTarget)`
> 4. Return a fully populated `DistributionResult`.
> 5. Use `DecimalUtils` for all quantizations.
> 6. Do NOT reference any formatter, message, or string output.
>
> **Deliverable:** `DistributionEngine.kt` — deterministic, fully configurable, zero hardcoded values.

**Часть 2 — Шаги 6-10:**

---

## Step 6 — Metrics & Calculator API

**Goal:** Wire everything together into the public calculator API.

**Prompt:**
> You are implementing Step 6 of the Fiscal Nest Core engine.
>
> **Source of truth:** `docs/API.md` (Entry Points + Daily Metrics) + `docs/ARCHITECTURE.md` (section 6.5).
>
> **Tasks:**
> 1. Create `BudgetCalculator` as a stateless object with two public entry points:
>    ```kotlin
>    fun calculateWhatIf(input: WhatIfInput): DistributionResult
>    fun calculateForecast(input: ForecastInput): List<ForecastResult>
>    ```
> 2. `calculateWhatIf`:
>    - Validate inputs via `InputValidator`.
>    - Call `DistributionEngine.distribute` with `available = null`.
>    - Return the `DistributionResult`.
> 3. `calculateForecast`:
>    - Validate inputs.
>    - Build a chain of periods. For each period:
>      - Call `CalendarEngine.buildSnapshot`.
>      - Compute `available = snapshot.receivedIncome - alreadySpent - snapshot.upcomingMandatory`.
>      - Call `DistributionEngine.distribute` with the period's totals and `available`.
>      - Compute `DailyMetrics` from the distribution result and snapshot:
>        - `dailyPlan = freeRemainder / daysInPeriod`
>        - `dailyActual = freeRemainder / daysRemaining`
>        - `dailyCashflow = (liquidOnHand - mustReserve - cushionTopup) / daysRemaining`
>        - `burnRate = alreadySpent / (daysElapsed + 1)`
>      - Build a `ForecastResult` combining distribution and daily metrics.
>      - Carry the `freeRemainder` forward as the opening balance of the next period (add it to the next period's income).
>    - Return the list of `ForecastResult`.
> 4. Do NOT import or use any formatter classes.
> 5. Do NOT produce any `String` output.
>
> **Deliverable:** `BudgetCalculator.kt`, `DailyMetrics.kt` — clean API, pure data output.

---

## Step 7 — Cleanup & Client Separation

**Goal:** Remove all client-layer code and verify engine purity.

**Prompt:**
> You are implementing Step 7 of the Fiscal Nest Core engine.
>
> **Source of truth:** `README.md` (Golden Rule).
>
> **Tasks:**
> 1. Delete the following files from the engine module if they exist:
>    - `Formatter.kt`
>    - `RussianFormatter.kt`
>    - `TimeMessageFormatter.kt`
> 2. Remove any `String`-based crisis type mapping (e.g., `mapCrisisType` function).
> 3. Remove the old `BudgetMessages` data class.
> 4. Ensure result classes contain **no `String` fields** except `activeCriticalityLevel` and opaque names.
> 5. Run a grep/search across all engine files for:
>    - `import android`
>    - `import java.text`
>    - `import java.util.Locale`
>    - Any logging framework imports
>    - Any emoji or non-ASCII characters in string literals
> 6. If any are found, remove or refactor them.
> 7. Create a stub file `README-CLIENT.md` in the root explaining:
>    - Where formatters, localization, and UI belong (in the client repo).
>    - That the engine returns pure data and flags.
>    - Sample pseudo-code for rendering a `DistributionResult` into a user-facing message.
>
> **Deliverable:** Clean engine module + `README-CLIENT.md` stub.

---

## Step 8 — KDoc & Documentation

**Goal:** Every public API element has comprehensive KDoc in plain English.

**Prompt:**
> You are implementing Step 8 of the Fiscal Nest Core engine.
>
> **Source of truth:** All docs in this repository.
>
> **Tasks:**
> 1. Add KDoc to **every** `public` class, interface, function, and property.
> 2. KDoc must explain in plain English:
>    - What the element does.
>    - What each parameter means (with units where applicable, e.g., "percentage in 0.0-1.0 range").
>    - What the return value represents.
>    - Any preconditions or invariants.
>    - Cross-references to related classes (e.g., `@see DistributionResult`).
> 3. Review `internal` vs `public` visibility. Anything not part of the public API should be `internal`.
> 4. Ensure no KDoc contains Russian, emojis, or implementation details that belong in code comments rather than API docs.
> 5. Update the main `README.md` with any corrections discovered during KDoc writing.
>
> **Deliverable:** Fully KDoc-documented codebase with correct visibility modifiers.

---

## Step 9 — Final Audit & Compliance

**Goal:** Verify every line of code against the locked specification.

**Prompt:**
> You are implementing Step 9 of the Fiscal Nest Core engine.
>
> **Source of truth:** All docs in this repository.
>
> **Tasks:**
> 1. Read every `.kt` file in the engine module.
> 2. For each file, produce an audit report line item:
>    - File name
>    - Compliance status: PASS / FAIL / PARTIAL
>    - List of any deviations from the spec, with line references
> 3. Specifically verify:
>    - No hardcoded `moneyScale` or `percentageScale`.
>    - No hardcoded criticality levels.
>    - No `String` fields in result classes (except opaque names).
>    - No formatter imports or usage.
>    - Correct Remainder Hierarchy computation.
>    - Correct crisis flag logic (independent Booleans).
>    - Correct piggy bank admissibility logic.
>    - Correct criticality level top-up formula.
>    - Correct calendar day counting (inclusive `daysRemaining`).
>    - Correct recurrence resolution for all three types.
> 4. If any FAIL items exist, propose concrete fixes (file + line + replacement code).
> 5. Write sample usage code in a new file `SAMPLE_USAGE.kt` demonstrating:
>    - A `WHAT_IF` call with custom criticality levels.
>    - A `FORECAST` call with recurring income and expenses.
> 6. Produce a final sign-off statement: "Engine is compliant with spec v1.0" or "Engine requires fixes: [list]".
>
> **Deliverable:** `AUDIT_REPORT.md` + `SAMPLE_USAGE.kt` + sign-off.

---

## Step 10 — Unit Test Scaffolding

**Goal:** JUnit 5 test suite covering all business rules.

**Prompt:**
> You are implementing Step 10 of the Fiscal Nest Core engine.
>
> **Source of truth:** `docs/ARCHITECTURE.md` (sections 4-7) + `docs/API.md` (Daily Metrics).
>
> **Tasks:**
> 1. Set up JUnit 5 in the project (add dependencies to a `build.gradle.kts` stub if needed).
> 2. Write tests for `DistributionEngine` covering:
>    - Normal distribution (no crisis, fully funded cushion).
>    - Expense Crisis (income < mandatory + optional).
>    - Cushion Crisis at each criticality level.
>    - Cushion Crisis + Expense Crisis simultaneously.
>    - Overfilled cushion (top-up = 0).
>    - Piggy Bank `PERCENT_OF_REMAINDER` mode.
>    - Piggy Bank `FIXED_AMOUNT` mode with admissibility capping.
>    - Zero income, zero expenses, zero cushion target edge cases.
> 3. Write tests for `CalendarEngine` covering:
>    - `OneTime`, `EveryNDays`, `EveryNMonths` recurrence.
>    - Event on period boundary (first/last day).
>    - `daysRemaining` on last day = 1.
>    - Leap year February.
> 4. Write tests for `BudgetCalculator` covering:
>    - `WHAT_IF` entry point.
>    - `FORECAST` entry point with 3-period chain.
>    - Carry-forward of free remainder between periods.
> 5. All tests must use `BigDecimal` with `compareTo`, never `==` for doubles.
> 6. Aim for 100% branch coverage of `DistributionEngine` and `CalendarEngine`.
>
> **Deliverable:** `src/test/kotlin/...` with green tests and a `TEST_REPORT.md` summary.
