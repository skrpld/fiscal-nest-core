# Development Plan

> Step-by-step implementation guide. Each step is a self-contained chat session.

---

## How to Use This Plan

1. Start a **new chat** for each step.
2. Paste the prompt for that step as the first message.
3. **Attach the full spec set** (`README.md`, `ARCHITECTURE.md`, `API.md`, `GLOSSARY.md`, `DEVELOPMENT_PLAN.md`) as context.
4. The AI will implement the step without asking clarifying questions.
5. If a step reveals a gap in the spec, note it and return to Step 1 for an amendment before proceeding.

---

## Code Style Rules (Enforced in Every Step)

- **No inline comments.** Code must be self-explanatory through naming and structure.
- **No TODO / FIXME / HACK markers.**
- **No Russian in code.** All identifiers, KDoc, and documentation are in English only.
- **KDoc is mandatory** for every public class, function, and property. KDoc must explain the purpose, parameters (with units), return values, preconditions, and cross-references.
- **Markdown documentation** is the only place for detailed explanations of business logic, algorithms, and design decisions.

---

## Communication Rules for Discussion Steps

These rules apply only to steps whose deliverable is discussion, analysis, or a decision — not code (currently: **Step 0** and **Step 1**). They do not affect the Code Style Rules above, which remain in force for every code/KDoc/Markdown-spec artifact regardless of step.

- **Language:** the AI communicates with the user in Russian during these steps. Code, KDoc, `FISCAL_NEST_CORE_LOCKED.md`, and any other technical specification file stay English-only, per "No Russian in code" above — this rule governs the conversation only, not the artifacts it produces.
- **Level of involvement:** the user controls product ideas and reviews the final result, but is minimally involved in implementation. During discussion steps, the AI raises only business-logic questions and decisions (what the engine should do, and why) and does not ask the user to evaluate Kotlin/architecture-level implementation detail. Technical decisions with no business-rule impact are made by the AI on its own and simply noted, not put to the user for review.

---

## Step 0 — Project Kick-off

**Goal:** Load context, understand boundaries, and confirm the toolchain.

**Pre-flight Checklist:**
- [ ] All five spec files (`README.md`, `ARCHITECTURE.md`, `API.md`, `GLOSSARY.md`, `DEVELOPMENT_PLAN.md`) are present.
- [ ] Target language is Kotlin (JVM).
- [ ] Build tool is Gradle with Kotlin DSL.
- [ ] Test framework is JUnit 5.

**Files to Attach:** `README.md`, `ARCHITECTURE.md`, `API.md`, `GLOSSARY.md`, `DEVELOPMENT_PLAN.md`

**Prompt:**
```plain
You are starting the Fiscal Nest Core engine implementation.

Read the attached spec files (README.md, ARCHITECTURE.md, API.md, GLOSSARY.md, DEVELOPMENT_PLAN.md).

Follow the "Communication Rules for Discussion Steps" section of DEVELOPMENT_PLAN.md for this session: reply in Russian, and keep discussion at the business-logic level rather than implementation detail.

Do NOT write any code. Only produce a brief summary confirming:
1. The package name.
2. The list of public classes/enums the engine must expose.
3. The list of internal classes.
4. The two entry-point methods and their signatures.
5. Any ambiguities or contradictions you detect across the spec files.

If you find contradictions, list them with file references and suggest resolutions.
```

**Acceptance Criteria:**
- Summary is accurate against the spec.
- Any contradictions are flagged before code is written.

**Deliverable:** A short text summary + contradiction report (if any).

---

## Step 1 — Requirements Analysis & Spec Refinement

**Goal:** Resolve ambiguities, lock the specification, and produce `FISCAL_NEST_CORE_LOCKED.md`.

**Pre-flight Checklist:**
- [ ] Step 0 summary has been reviewed.
- [ ] All contradictions flagged in Step 0 are addressed.

**Files to Attach:** `README.md`, `ARCHITECTURE.md`, `API.md`, `GLOSSARY.md`, `DEVELOPMENT_PLAN.md`

**Prompt:**
```plain
You are performing Step 1 of the Fiscal Nest Core engine.

Follow the "Communication Rules for Discussion Steps" section of DEVELOPMENT_PLAN.md for this session: reply in Russian, and keep discussion at the business-logic level rather than implementation detail. The locked spec document itself stays in English per the Code Style Rules.

**Task:** Produce a single locked specification document named `FISCAL_NEST_CORE_LOCKED.md` that consolidates and resolves any ambiguities from the attached spec files.

**Requirements for the locked spec:**
1. List every public data class, enum, and sealed class with exact fields and types.
2. List every internal class with exact public method signatures.
3. Resolve any composition-vs-inheritance questions in favor of composition (e.g., ForecastResult wraps DistributionResult).
4. Explicitly state the carry-forward rules for Forecast mode:
   - openingBalance = previous closingBalance (freeRemainder).
   - cushionState.current is carried forward post-distribution.
   - alreadySpent applies only to period 1.
5. Explicitly state that DistributionEngine.distribute does NOT take an `available` parameter; liquidity is handled by BudgetCalculator.
6. Include the exact formulas for DailyMetrics.
7. Include the exact validation rules and exception messages.
8. Mark the document as "LOCKED — v1.0". No further edits without a formal amendment process.
```

**Acceptance Criteria:**
- `FISCAL_NEST_CORE_LOCKED.md` exists and is self-contained.
- No contradictions remain between it and the source docs.
- It is marked as locked.

**Deliverable:** `FISCAL_NEST_CORE_LOCKED.md` — the single source of truth for all subsequent steps.

---

## Step 2 — Data Model & Package Setup

**Goal:** Create the package structure and all public data classes. No logic, just types.

**Pre-flight Checklist:**
- [ ] `FISCAL_NEST_CORE_LOCKED.md` is complete and reviewed.
- [ ] Package name is confirmed: `fiscalnest.core`.

**Files to Attach:** `FISCAL_NEST_CORE_LOCKED.md`

**Prompt:**
```plain
You are implementing Step 2 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md`.

**Tasks:**
1. Create the package `fiscalnest.core`.
2. Define the following public data classes / interfaces / enums in Kotlin:
   - `EngineConfig` (roundingMode, moneyScale, percentageScale, criticalityLevels, piggyBankMode, piggyBankTarget, piggyBankAdmissibilityPct)
   - `CriticalityLevel` (name, maxFillPct, topupMode, topupValue, admissibilityPct)
   - `TopupMode` enum: `PERCENT_OF_TARGET`, `PERCENT_OF_REMAINDER`
   - `PiggyBankMode` enum: `PERCENT_OF_REMAINDER`, `FIXED_AMOUNT`
   - `EventRecurrence` sealed class: `OneTime(date)`, `EveryNDays(n, startDate)`, `EveryNMonths(n, dayOfMonth, startDate)`
   - `IncomeEvent` (id, name, amount, recurrence, startDate, endDate, category, isReliable)
   - `ExpenseEvent` (id, name, amount, isMandatory, recurrence, startDate, endDate, category)
   - `CushionState` (current: BigDecimal, target: BigDecimal)
   - `WhatIfInput` (income, mandatory, optional, cushionState, alreadySpent, config)
   - `ForecastInput` (incomeEvents, expenseEvents, periodStart, periodEnd, currentDate, alreadySpent, forecastPeriods, config, cushionState)
   - `DistributionResult` (all monetary and boolean fields per locked spec; cushionCurrent must be POST-DISTRIBUTION)
   - `ForecastResult` (distribution: DistributionResult, dailyMetrics: DailyMetrics, periodStart, periodEnd, openingBalance, closingBalance, liquidOnHand, mustReserve, available, receivedIncome, pendingIncome, upcomingMandatory, upcomingOptional, alreadySpent, daysInPeriod, daysElapsed, daysRemaining)
   - `DailyMetrics` (dailyPlan, dailyActual, dailyCashflow, burnRate)
   - `PeriodSnapshot` (periodStart, periodEnd, currentDate, daysInPeriod, daysElapsed, daysRemaining, receivedIncome, pendingIncome, upcomingMandatory, upcomingOptional)
3. Use `BigDecimal` for all monetary fields. Use `Int` for counts. Use `LocalDate` from `java.time`.
4. Add validation in `init` blocks where appropriate (e.g., non-negative amounts, percentages in [0, 1]).
5. Do NOT write any calculation logic. Only data structures.
6. Do NOT add any formatting, messaging, or platform-specific code.
7. Do NOT add inline comments. Use KDoc for all public declarations.
```

**Acceptance Criteria:**
- All public types compile (syntactically correct Kotlin).
- `ForecastResult` uses composition (contains `DistributionResult`, does not inherit).
- `ForecastInput` includes `periodEnd`.
- `DistributionResult.cushionCurrent` is documented as post-distribution.
- No logic, no imports outside `java.math`, `java.time`, and Kotlin stdlib.
- No inline comments; KDoc is present on every public declaration.

**Deliverable:** A directory of Kotlin files containing only type definitions.

---

## Step 3 — Decimal & Input Layer

**Goal:** Configurable rounding, input validation, and a clean numeric utility layer.

**Pre-flight Checklist:**
- [ ] Step 2 data classes are complete and compile.
- [ ] `EngineConfig` fields are known.

**Files to Attach:** `FISCAL_NEST_CORE_LOCKED.md`

**Prompt:**
```plain
You are implementing Step 3 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md` (Configuration + Validation sections).

**Tasks:**
1. Create `DecimalUtils` as an internal object that accepts an `EngineConfig` instance for scale and rounding mode. It must provide:
   - `quantizeMoney(value: BigDecimal, config: EngineConfig): BigDecimal`
   - `quantizePct(value: BigDecimal, config: EngineConfig): BigDecimal`
   - `sum(values: List<BigDecimal>): BigDecimal` (with non-negative validation)
   - `requireNonNegative(value: BigDecimal, name: String)`
2. The public API accepts `BigDecimal` directly. If the client wants to pass multiple values, it sums them before calling the engine.
3. Create `InputValidator` as an internal object with static methods to validate:
   - All monetary inputs are non-negative and finite.
   - `cushionTarget >= 0`, `cushionCurrent >= 0`.
   - `piggyBankAdmissibilityPct` in [0, 1].
   - `criticalityLevels` is non-empty and sorted by `maxFillPct` ascending.
   - No two levels have overlapping ranges.
   - `periodStart <= periodEnd`.
   - `currentDate` within [periodStart, periodEnd].
   - `forecastPeriods >= 1`.
   - Recurrence parameters (`n`, `dayOfMonth`) are positive.
4. On failure throw `IllegalArgumentException` with a descriptive English message.
5. Do NOT add inline comments. Use KDoc for all public/internal declarations.
```

**Acceptance Criteria:**
- `DecimalUtils` and `InputValidator` are marked `internal`.
- All validation rules from the locked spec are implemented.
- Exception messages are plain English, no emojis.
- No inline comments; KDoc is present on every declaration.

**Deliverable:** `DecimalUtils.kt`, `InputValidator.kt` — pure, configurable, validated.

---

## Step 4 — Calendar & Recurrence Engine

**Goal:** Build period snapshots with correct recurrence resolution.

**Pre-flight Checklist:**
- [ ] Step 3 utilities are complete.
- [ ] `EventRecurrence` sealed class is defined.

**Files to Attach:** `FISCAL_NEST_CORE_LOCKED.md`

**Prompt:**
```plain
You are implementing Step 4 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md` (Calendar Logic section).

**Tasks:**
1. Create `CalendarEngine` as an internal class with a single public method:
   kotlin:
   fun buildSnapshot(
       periodStart: LocalDate,
       periodEnd: LocalDate,
       currentDate: LocalDate,
       incomeEvents: List<IncomeEvent>,
       expenseEvents: List<ExpenseEvent>,
       alreadySpent: BigDecimal
   ): PeriodSnapshot
   
2. Implement recurrence resolution for all three `EventRecurrence` types:
   - `OneTime`: include if date is within [periodStart, periodEnd] and >= event.startDate and <= event.endDate (if endDate != null).
   - `EveryNDays`: generate dates from `startDate` stepping by `n` days, include those within the period and within [startDate, endDate].
   - `EveryNMonths`: generate dates from `startDate` stepping by `n` months on the specified `dayOfMonth` (coerce to last day of month if needed), include those within the period and within [startDate, endDate].
3. For each resolved event date, compare with `currentDate`:
   - Income: `receivedIncome` if date <= currentDate, else `pendingIncome`.
   - Mandatory expense: `upcomingMandatory` if date > currentDate.
   - Optional expense: `upcomingOptional` if date > currentDate.
4. Compute `daysInPeriod = ChronoUnit.DAYS.between(periodStart, periodEnd) + 1`.
5. Compute `daysElapsed = ChronoUnit.DAYS.between(periodStart, currentDate)`.
6. Compute `daysRemaining = ChronoUnit.DAYS.between(currentDate, periodEnd) + 1` (inclusive; last day = 1).
7. Validate that `currentDate` is within [periodStart, periodEnd] (delegate to InputValidator or inline).
8. Do NOT compute daily metrics here. Only the snapshot.
9. Do NOT add inline comments. Use KDoc for all declarations.
```

**Acceptance Criteria:**
- All three recurrence patterns resolve correctly.
- Event boundaries (first/last day of period) are handled.
- `daysRemaining` on last day equals 1.
- Leap year February is handled correctly for `EveryNMonths`.
- No inline comments; KDoc is present on every declaration.

**Deliverable:** `CalendarEngine.kt` — handles all recurrence patterns, correct day counts.

---

## Step 5 — Distribution Engine Core

**Goal:** Implement the full distribution algorithm with configurable criticality levels and piggy bank.

**Pre-flight Checklist:**
- [ ] Step 4 calendar engine is complete.
- [ ] `DistributionResult` fields are known.

**Files to Attach:** `FISCAL_NEST_CORE_LOCKED.md`

**Prompt:**
```plain
You are implementing Step 5 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md` (Remainder Hierarchy + Distribution Algorithm + Crisis Scenarios + Criticality Levels).

**Tasks:**
1. Create `DistributionEngine` as an internal class with a public method:
   kotlin:
   fun distribute(
       income: BigDecimal,
       mandatory: BigDecimal,
       optional: BigDecimal,
       cushionState: CushionState,
       config: EngineConfig
   ): DistributionResult

   Note: There is NO `available` parameter. Liquidity is handled by BudgetCalculator.
2. Implement the Remainder Hierarchy exactly as defined:
   - `rawRemainder = income - mandatory`
   - `netRemainder = rawRemainder - optional`
   - If `netRemainder < 0`: set `expenseCrisis = true`, `expenseDeficit = |netRemainder|`, cushionTopup = 0, piggy = 0, freeRemainder = netRemainder. Return immediately.
3. If no Expense Crisis:
   - Compute `fillPct = cushionCurrent / cushionTarget * 100` (handle target = 0 as 100%).
   - Compute `cushionNeed = max(0, target - current)`.
   - Select active criticality level: first level where `fillPct < maxFillPct`. If none, cushion is fully funded.
   - If active level exists:
     - `desired = level.topupValue * (cushionTarget if PERCENT_OF_TARGET else netRemainder)`
     - `maxFromRemainder = level.admissibilityPct * netRemainder`
     - `topup = min(desired, maxFromRemainder, cushionNeed, netRemainder)`
   - If no active level: `topup = 0`.
   - `postCushionRemainder = netRemainder - topup`
   - Compute piggy bank:
     - If `piggyBankMode == PERCENT_OF_REMAINDER`: `piggyTarget = piggyBankTarget * postCushionRemainder`
     - If `piggyBankMode == FIXED_AMOUNT`: `piggyTarget = piggyBankTarget`
     - `piggyActual = min(piggyTarget, piggyBankAdmissibilityPct * postCushionRemainder, postCushionRemainder)`
     - `piggyBankCappedByAdmissibility = (piggyActual < piggyTarget)`
   - `freeRemainder = postCushionRemainder - piggyActual`
   - `cushionCrisis = (activeLevel != null)`
   - `cushionOverfilled = (cushionCurrent > cushionTarget)`
4. `cushionCurrent` in the returned `DistributionResult` must be the POST-DISTRIBUTION balance: `inputCushionCurrent + cushionTopup`.
5. Return a fully populated `DistributionResult`.
6. Use `DecimalUtils` for all quantizations.
7. Do NOT reference any formatter, message, or string output.
8. Do NOT add inline comments. Use KDoc for all declarations.
```

**Acceptance Criteria:**
- Remainder hierarchy is computed in strict order.
- Expense crisis short-circuits correctly.
- Cushion top-up respects admissibility, need, and netRemainder caps.
- Piggy bank respects mode, target, admissibility, and post-cushion remainder.
- `cushionCurrent` in result is post-distribution.
- No hardcoded values; everything comes from `EngineConfig`.
- No inline comments; KDoc is present on every declaration.

**Deliverable:** `DistributionEngine.kt` — deterministic, fully configurable, zero hardcoded values.

---

## Step 6 — Metrics & Calculator API

**Goal:** Wire everything together into the public calculator API.

**Pre-flight Checklist:**
- [ ] Step 5 distribution engine is complete.
- [ ] `ForecastResult` uses composition (contains `DistributionResult`).

**Files to Attach:** `FISCAL_NEST_CORE_LOCKED.md`

**Prompt:**
```plain
You are implementing Step 6 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md` (Entry Points + Daily Metrics + Multi-Period Forecast).

**Tasks:**
1. Create `BudgetCalculator` as a public stateless object with two public entry points:
   kotlin:
   fun calculateWhatIf(input: WhatIfInput): DistributionResult
   fun calculateForecast(input: ForecastInput): List<ForecastResult>

2. `calculateWhatIf`:
   - Validate inputs via `InputValidator`.
   - Call `DistributionEngine.distribute` with `income`, `mandatory`, `optional`, `cushionState`, `config`.
   - Return the `DistributionResult`.
3. `calculateForecast`:
   - Validate inputs.
   - Determine period length from `periodStart` and `periodEnd` of the first period.
   - Build a chain of `forecastPeriods` consecutive periods.
   - For each period:
     - `openingBalance` = `0` for period 1; else `closingBalance` of previous period (which equals previous `distribution.freeRemainder`).
     - `periodIncome = snapshot.receivedIncome + openingBalance`.
     - Call `CalendarEngine.buildSnapshot` for the period.
     - Call `DistributionEngine.distribute` with `income = periodIncome`, `mandatory = snapshot.upcomingMandatory`, `optional = snapshot.upcomingOptional`, `cushionState` (carried forward), `config`.
     - Compute liquidity:
       - `liquidOnHand = snapshot.receivedIncome + openingBalance - alreadySpent` (alreadySpent is from ForecastInput for period 1, `0` for N > 1).
       - `mustReserve = snapshot.upcomingMandatory`.
       - `available = liquidOnHand - mustReserve`.
     - Compute `DailyMetrics`:
       - `dailyPlan = distribution.freeRemainder / snapshot.daysInPeriod`
       - `dailyActual = distribution.freeRemainder / snapshot.daysRemaining`
       - `dailyCashflow = (available - distribution.cushionTopup) / snapshot.daysRemaining`
       - `burnRate = alreadySpent / (snapshot.daysElapsed + 1)` (alreadySpent as above).
     - Build a `ForecastResult` containing:
       - `distribution` (the DistributionResult)
       - `dailyMetrics`
       - `periodStart`, `periodEnd`
       - `openingBalance`, `closingBalance = distribution.freeRemainder`
       - `liquidOnHand`, `mustReserve`, `available`
       - `receivedIncome`, `pendingIncome`, `upcomingMandatory`, `upcomingOptional`, `alreadySpent`
       - `daysInPeriod`, `daysElapsed`, `daysRemaining`
     - Carry the post-distribution `cushionState` forward: next period's `cushionState.current = distribution.cushionCurrent`.
   - Return the list of `ForecastResult`.
4. Do NOT import or use any formatter classes.
5. Do NOT produce any `String` output.
6. Do NOT add inline comments. Use KDoc for all public declarations.
```

**Acceptance Criteria:**
- `calculateWhatIf` returns a pure `DistributionResult`.
- `calculateForecast` returns a list of `ForecastResult` with correct period chaining.
- `openingBalance` of period N > 1 equals `freeRemainder` of period N-1.
- `cushionState` is carried forward between periods.
- `alreadySpent` is applied only to period 1.
- `dailyCashflow` uses `available`, not `freeRemainder`.
- No inline comments; KDoc is present on every public declaration.

**Deliverable:** `BudgetCalculator.kt`, `DailyMetrics.kt` — clean API, pure data output.

---

## Step 7 — Cleanup & Client Separation

**Goal:** Remove any accidental client-layer code and verify engine purity.

**Pre-flight Checklist:**
- [ ] Steps 2-6 are complete.
- [ ] All `.kt` files in the engine module have been written.

**Files to Attach:** All `.kt` files from `src/main/kotlin/fiscalnest/core/`, `README.md`

**Prompt:**
```plain
You are implementing Step 7 of the Fiscal Nest Core engine.

**Source of truth:** `README.md` (Golden Rule) + `FISCAL_NEST_CORE_LOCKED.md`.

**Tasks:**
1. Review every `.kt` file in the engine module.
2. Remove or refactor any of the following if found:
   - `import android.*`
   - `import java.text.*`
   - `import java.util.Locale`
   - Any logging framework imports (SLF4J, Log4J, etc.)
   - Any emoji or non-ASCII characters in string literals (exception messages must be plain ASCII English)
   - Any `String`-based crisis type mapping or human-readable message generation
   - Any `println` or `System.out` usage
   - Any inline comments (keep only KDoc)
3. Ensure result classes contain **no `String` fields** except `activeCriticalityLevel` and opaque names (`id`, `name`, `category`).
4. Ensure `internal` visibility is used for `DistributionEngine`, `CalendarEngine`, `DecimalUtils`, `InputValidator`.
5. Ensure `BudgetCalculator` is the only `public` class/object intended for direct client use (data classes are also public by design).
6. Create a stub file `README-CLIENT.md` in the root explaining:
   - Where formatters, localization, and UI belong (in the client repo).
   - That the engine returns pure data and flags.
   - Sample pseudo-code for rendering a `DistributionResult` into a user-facing message.
   - Sample pseudo-code for rendering a `ForecastResult` list into a timeline UI.
```

**Acceptance Criteria:**
- Zero platform-specific imports.
- Zero non-ASCII string literals in engine code.
- Zero inline comments; only KDoc remains.
- Correct visibility modifiers (`public` for API, `internal` for implementation).
- `README-CLIENT.md` is present and helpful.

**Deliverable:** Clean engine module + `README-CLIENT.md` stub.

---

## Step 8 — KDoc & Documentation

**Goal:** Every public API element has comprehensive KDoc in plain English.

**Pre-flight Checklist:**
- [ ] Step 7 cleanup is complete.
- [ ] All public classes, methods, and properties are stable.

**Files to Attach:** All `.kt` files from `src/main/kotlin/fiscalnest/core/`

**Prompt:**
```plain
You are implementing Step 8 of the Fiscal Nest Core engine.

**Source of truth:** All docs in this repository + `FISCAL_NEST_CORE_LOCKED.md`.

**Tasks:**
1. Review and, if necessary, rewrite KDoc on **every** `public` class, interface, function, and property.
2. KDoc must explain in plain English:
   - What the element does.
   - What each parameter means (with units where applicable, e.g., "percentage in 0.0-1.0 range").
   - What the return value represents.
   - Any preconditions or invariants.
   - Cross-references to related classes (e.g., `@see DistributionResult`).
3. Review `internal` vs `public` visibility. Anything not part of the public API should be `internal`.
4. Ensure no KDoc contains Russian, emojis, or implementation details that belong in code comments rather than API docs.
5. Ensure there are NO inline comments anywhere in the code. KDoc only.
6. Update the main `README.md` with any corrections discovered during KDoc writing.
```

**Acceptance Criteria:**
- Every public element has KDoc.
- KDoc is in plain English, no emojis, no Russian.
- Visibility modifiers are correct.
- Zero inline comments remain in the codebase.

**Deliverable:** Fully KDoc-documented codebase with correct visibility modifiers.

---

## Step 9 — Final Audit & Compliance

**Goal:** Verify every line of code against the locked specification.

**Pre-flight Checklist:**
- [ ] Steps 1-8 are complete.
- [ ] All `.kt` files are finalized.

**Files to Attach:** All `.kt` files from `src/main/kotlin/fiscalnest/core/`, `FISCAL_NEST_CORE_LOCKED.md`

**Prompt:**
```plain
You are implementing Step 9 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md` + all docs.

**Tasks:**
1. Read every `.kt` file in the engine module.
2. For each file, produce an audit report line item:
   - File name
   - Compliance status: PASS / FAIL / PARTIAL
   - List of any deviations from the spec, with line references
3. Specifically verify:
   - No hardcoded `moneyScale` or `percentageScale`.
   - No hardcoded criticality levels.
   - No `String` fields in result classes (except opaque names and activeCriticalityLevel).
   - No formatter imports or usage.
   - No inline comments (KDoc only).
   - Correct Remainder Hierarchy computation.
   - Correct crisis flag logic (independent Booleans).
   - Correct piggy bank admissibility logic.
   - Correct criticality level top-up formula.
   - Correct calendar day counting (inclusive `daysRemaining`).
   - Correct recurrence resolution for all three types.
   - `DistributionEngine.distribute` does NOT take an `available` parameter.
   - `ForecastResult` uses composition, not inheritance.
   - Carry-forward logic updates both `openingBalance` and `cushionState`.
   - `alreadySpent` is only used in period 1.
4. If any FAIL items exist, propose concrete fixes (file + line + replacement code).
5. Write sample usage code in a new file `SAMPLE_USAGE.kt` demonstrating:
   - A `WHAT_IF` call with custom criticality levels.
   - A `FORECAST` call with recurring income and expenses.
   - Reading `dailyMetrics` and `distribution` from `ForecastResult`.
6. Produce a final sign-off statement: "Engine is compliant with spec v1.0" or "Engine requires fixes: [list]".
```

**Acceptance Criteria:**
- Every engine file is audited.
- Audit report is honest: FAILs are not hidden.
- `SAMPLE_USAGE.kt` compiles against the public API.
- No inline comments in any file.

**Deliverable:** `AUDIT_REPORT.md` + `SAMPLE_USAGE.kt` + sign-off.

---

## Step 10 — Unit Test Scaffolding

**Goal:** JUnit 5 test suite covering all business rules.

**Pre-flight Checklist:**
- [ ] Step 9 audit is PASS (or all FAIL items are fixed).
- [ ] All public APIs are stable.

**Files to Attach:** `FISCAL_NEST_CORE_LOCKED.md`, all `.kt` files from `src/main/kotlin/fiscalnest/core/`

**Prompt:**
```plain
You are implementing Step 10 of the Fiscal Nest Core engine.

**Source of truth:** `FISCAL_NEST_CORE_LOCKED.md` (sections on Distribution, Calendar, Daily Metrics) + `ARCHITECTURE.md`.

**Tasks:**
1. Set up JUnit 5 in the project. Create a `build.gradle.kts` stub with:
   - Kotlin JVM plugin
   - JUnit 5 (junit-jupiter)
   - A test task configured to use JUnit Platform
2. Write tests for `DistributionEngine` covering:
   - Normal distribution (no crisis, fully funded cushion).
   - Expense Crisis (income < mandatory + optional).
   - Cushion Crisis at each criticality level.
   - Cushion Crisis + Expense Crisis simultaneously.
   - Overfilled cushion (top-up = 0).
   - Piggy Bank `PERCENT_OF_REMAINDER` mode.
   - Piggy Bank `FIXED_AMOUNT` mode with admissibility capping.
   - Zero income, zero expenses, zero cushion target edge cases.
   - Post-distribution `cushionCurrent` correctness.
3. Write tests for `CalendarEngine` covering:
   - `OneTime`, `EveryNDays`, `EveryNMonths` recurrence.
   - Event on period boundary (first/last day).
   - `daysRemaining` on last day = 1.
   - Leap year February.
   - Events with `endDate` bounds.
4. Write tests for `BudgetCalculator` covering:
   - `WHAT_IF` entry point.
   - `FORECAST` entry point with 3-period chain.
   - Carry-forward of free remainder between periods.
   - Carry-forward of cushionState between periods.
   - `alreadySpent` applied only to period 1.
   - `dailyCashflow` uses `available`, not `freeRemainder`.
5. All tests must use `BigDecimal` with `compareTo`, never `==` for doubles or float comparisons.
6. Aim for 100% branch coverage of `DistributionEngine` and `CalendarEngine`.
7. Do NOT add inline comments in test code. Use KDoc for test classes and methods.
8. Save tests under `src/test/kotlin/fiscalnest/core/`.
9. Produce `TEST_REPORT.md` summarizing:
   - Total test count.
   - Coverage targets.
   - Any skipped or pending tests and why.
```

**Acceptance Criteria:**
- All tests compile and pass.
- `BigDecimal` comparisons use `compareTo`.
- `DistributionEngine` and `CalendarEngine` branches are covered.
- `BudgetCalculator` tests verify carry-forward logic.
- No inline comments; KDoc is present on test classes and methods.

**Deliverable:** `src/test/kotlin/...` with green tests and a `TEST_REPORT.md` summary.
