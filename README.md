# Fiscal Nest Core

[![CI](https://github.com/skrpld/fiscal-nest-core/actions/workflows/ci.yml/badge.svg)](https://github.com/skrpld/fiscal-nest-core/actions/workflows/ci.yml)

> **Repository:** `github.com/skrpld/fiscal-nest-core`  
> **Package:** `fiscalnest.core`  
> **License:** Apache-2.0 — see [LICENSE](LICENSE) and [NOTICE](NOTICE)  
> **Language:** English only — code, APIs, KDoc, docs  
> **Scope:** Stateless Kotlin business-logic engine. No UI, no DB, no formatting.

---

## What is this?

Fiscal Nest Core is a **standalone, embeddable Kotlin engine** that calculates how income should be distributed across mandatory expenses, optional expenses, a safety cushion, and a piggy bank.

It can be plugged into:
- Android apps (`fiscal-nest-mobile`)
- Server-side services (JVM / Ktor / Spring)
- CLI tools
- Any JVM 17+ runtime

The engine is **pure business logic and stateless**. It does not render messages, store state, or assume a presentation layer. All localization, currency formatting, emoji usage, database persistence, display data (event names, categories) and manual-adjustment workflows are the responsibility of the **client implementation**.

> **Golden Rule:** The engine must never import Android SDK, HTTP clients, locale-specific formatters, logging frameworks, or any platform-specific code. All behavior is configured through the public API.

---

## Two Modes

| Mode | Codename | Description |
|------|----------|-------------|
| **Light** | `WHAT_IF` | Snapshot calculation. Aggregate amounts in, distribution out. No dates. |
| **Heavy** | `FORECAST` | Calendar-aware multi-period projection from dated, recurring events. Each period gets a **plan** and a **cash view**, daily metrics, and carry-forward into the next period. |

In `FORECAST` mode income and expenses are always treated the same way:

| View | Field | Counts | Answers |
|------|-------|--------|---------|
| **Plan** | `distribution` | every income and expense of the period, whatever its date | How should this period's money be allocated? (same rules as `WHAT_IF`) |
| **Cash** | `cashFlow` | income received and expenses paid by `currentDate`; upcoming mandatory expenses plus the reserves you choose are set aside | What is safe to spend right now? |

A salary that arrives after the rent is therefore not an expense crisis in the plan, while the cash view still shows the gap until payday.

Free money left at the end of a period carries into the next one and **stays free**: it is never redistributed to the cushion or the piggy bank. A carried deficit, on the other hand, is covered by the next plan first.

All percentage-like values are **ratios on the `0.0–1.0` scale** (`0.25` = 25%). Showing `0–100` is up to the client.

---

## Quick Start

The snippets below show how a client application calls the engine. The engine itself never prints, logs, or formats output.

### Configuration

```kotlin
import fiscalnest.core.*
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate

val config = EngineConfig(
    roundingMode = RoundingMode.HALF_UP,
    moneyScale = 2,
    percentageScale = 4,
    criticalityLevels = listOf(
        CriticalityLevel(
            name = "Critical",
            maxFillPct = BigDecimal("0.30"),
            topupMode = TopupMode.PERCENT_OF_TARGET,
            topupValue = BigDecimal("0.20"),
            admissibilityPct = BigDecimal("0.80")
        ),
        CriticalityLevel(
            name = "Warning",
            maxFillPct = BigDecimal("0.70"),
            topupMode = TopupMode.PERCENT_OF_REMAINDER,
            topupValue = BigDecimal("0.10"),
            admissibilityPct = BigDecimal("0.50")
        )
    ),
    piggyBankMode = PiggyBankMode.FIXED_AMOUNT,
    piggyBankTarget = BigDecimal("5000"),
    piggyBankAdmissibilityPct = BigDecimal("0.80"),
    cashReserves = setOf(CashReserve.CUSHION_TOPUP)
)
```

`cashReserves` decides what the cash view sets aside besides upcoming mandatory expenses: `CUSHION_TOPUP`, `PIGGY_BANK`, `UPCOMING_OPTIONAL`, any combination, or nothing.

### WHAT_IF — Snapshot Calculation

```kotlin
val result = BudgetCalculator.calculateWhatIf(
    WhatIfInput(
        income = BigDecimal("50000"),
        mandatory = BigDecimal("20000"),
        optional = BigDecimal("10000"),
        cushionState = CushionState(current = BigDecimal("5000"), target = BigDecimal("20000")),
        config = config
    )
)

result.expenseCrisis       // false
result.cushionCrisis       // true, level "Critical": fill ratio 0.25 < 0.30
result.cushionFillPct      // 0.2500 (before distribution)
result.cushionTopup        // 4000.00 = min(20% of target, 80% of 20000, need 15000)
result.cushionCurrent      // 9000.00 (after distribution)
result.piggyBankActual     // 5000.00 = min(5000, 80% of 16000)
result.freeRemainder       // 11000.00
```

### FORECAST — Multi-Period Projection

```kotlin
val forecast = BudgetCalculator.calculateForecast(
    ForecastInput(
        incomeEvents = listOf(
            IncomeEvent(
                id = "salary",
                amount = BigDecimal("50000"),
                recurrence = EventRecurrence.EveryNMonths(n = 1, dayOfMonth = 1),
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null
            )
        ),
        expenseEvents = listOf(
            ExpenseEvent(
                id = "rent",
                amount = BigDecimal("20000"),
                isMandatory = true,
                recurrence = EventRecurrence.EveryNMonths(n = 1, dayOfMonth = 5),
                startDate = LocalDate.of(2026, 1, 5),
                endDate = null
            )
        ),
        periodStart = LocalDate.of(2026, 8, 1),
        periodEnd = LocalDate.of(2026, 8, 31),
        currentDate = LocalDate.of(2026, 8, 7),
        alreadySpent = BigDecimal("3500"),
        forecastPeriods = 3,
        config = config,
        cushionState = CushionState(current = BigDecimal("5000"), target = BigDecimal("20000"))
    )
)

val august = forecast[0]
august.distribution.freeRemainder  // 21000.00  plan: 50000 - 20000 - 4000 cushion - 5000 piggy
august.cashFlow.paidMandatory      // 20000.00  rent of Aug 5 is already paid
august.cashFlow.mustReserve        // 4000.00   cushion top-up, as configured
august.cashFlow.available          // 22500.00  50000 - 20000 - 3500 already spent - 4000
august.closingBalance              // 17500.00  21000 - 3500, carried into September
august.dailyMetrics.dailyActual    // 700.00    17500 / 25 remaining days
august.dailyMetrics.dailyCashflow  // 900.00    22500 / 25

val september = forecast[1]
september.periodStart              // 2026-09-01 (periods follow calendar months)
september.periodEnd                // 2026-09-30
september.openingBalance           // 17500.00
september.distribution.totalIncome // 50000.00  carried money is not redistributed
september.freeBalance              // 39500.00  17500 carried + 22000 new free remainder
```

### Recurrence

| Pattern | Occurs |
|---------|--------|
| `EventRecurrence.OneTime` | once, on the event's `startDate` |
| `EventRecurrence.EveryNDays(n)` | `startDate`, then every `n` days |
| `EventRecurrence.EveryNMonths(n, dayOfMonth)` | the first `dayOfMonth` on or after `startDate`, then every `n` months; `31` means "end of month" |

Every occurrence stays within the event's `[startDate, endDate]`.

---

## Architecture in One Diagram

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
  = Free Remainder      <- source for daily budget
```

The allocations always add up exactly: `Net Remainder = Cushion Top-up + Piggy Bank + Free Remainder`.

---

## Documentation

| Document | What's inside |
|----------|---------------|
| [FISCAL_NEST_CORE_LOCKED.md](FISCAL_NEST_CORE_LOCKED.md) | **Single source of truth** (spec v1.2): exact types, algorithm, formulas, validation messages, amendment log |
| [ARCHITECTURE.md](ARCHITECTURE.md) | Entities, Remainder Hierarchy, Crisis Scenarios, Criticality Levels, Calendar Logic, Plan vs Cash, Thread Safety, Error Handling |
| [API.md](API.md) | Public API contract, Configuration, Output data structures, Daily Metrics, Exception Reference |
| [GLOSSARY.md](GLOSSARY.md) | Definitions of all business terms |
| [DEVELOPMENT_PLAN.md](DEVELOPMENT_PLAN.md) | Step-by-step implementation guide and progress |

---

## Project Structure

```
fiscal-nest-core/
  src/main/kotlin/fiscalnest/core/
    BudgetCalculator.kt      public facade
    EngineConfig.kt          EngineConfig, CriticalityLevel
    Modes.kt                 TopupMode, PiggyBankMode, CashReserve
    EventRecurrence.kt       OneTime, EveryNDays, EveryNMonths
    IncomeEvent.kt
    ExpenseEvent.kt
    Inputs.kt                CushionState, WhatIfInput, ForecastInput
    Results.kt               DistributionResult, ForecastResult, CashFlow, DailyMetrics
    DistributionEngine.kt    internal: remainder hierarchy
    CalendarEngine.kt        internal: recurrences, PeriodSnapshot, PeriodSchedule
    DecimalUtils.kt          internal: quantization
    InputValidator.kt        internal: all validation rules

  src/test/kotlin/fiscalnest/core/
    DistributionEngineTest.kt
    CalendarEngineTest.kt
    BudgetCalculatorTest.kt
    InputValidatorTest.kt

  .github/workflows/ci.yml   build and test on JDK 17 and 21
  build.gradle.kts, settings.gradle.kts, gradlew
  FISCAL_NEST_CORE_LOCKED.md, ARCHITECTURE.md, API.md, GLOSSARY.md, DEVELOPMENT_PLAN.md
  README.md
  LICENSE
  NOTICE
```

---

## Building & Testing

The project is a standard Gradle Kotlin/JVM module (Kotlin 2.4, JVM 17 bytecode, JUnit Jupiter). Building requires JDK 17 or newer.

```bash
./gradlew build                 # compile and run all tests
./gradlew test                  # tests only
./gradlew publishToMavenLocal   # install into ~/.m2 for client integration
```

CI runs `./gradlew build` on JDK 17 and 21 for every push to `main` and every pull request.

### Using it from a client

The library is not published to Maven Central yet. After `publishToMavenLocal`:

```kotlin
repositories {
    mavenLocal()
}

dependencies {
    implementation("io.github.skrpld:fiscal-nest-core:0.1.0-SNAPSHOT")
}
```

---

## Thread Safety

The engine is **stateless and thread-safe**. All public methods are pure functions with no mutable static state. You may safely share a single `EngineConfig` instance across threads and call `BudgetCalculator` concurrently from multiple coroutines or threads.

---

## Input Safety

- Every input is validated before any calculation; failures throw `IllegalArgumentException` with a stable message (see `FISCAL_NEST_CORE_LOCKED.md` §9).
- Constructors validate, and `BudgetCalculator` validates again, so instances created by reflection-based deserializers (Gson, Jackson) are checked too.
- Decimals with more than 1000 integer or fractional digits (e.g. `1E+999999999`) are rejected: quantizing them would exhaust memory.
- Recurrences are counted arithmetically, so an event anchored far in the past does not slow the engine down.
- The cost and size of a forecast grow linearly with `forecastPeriods` and the number of events. If a server passes user input through, bound those two values.

---

## Status

**Spec v1.2 is LOCKED** (`FISCAL_NEST_CORE_LOCKED.md`). The engine is implemented and covered by unit tests; documentation and audit steps 7–9 of the development plan are still open. All code must comply with the locked spec — any deviation is treated as a bug.

---

## License

Copyright 2026 skrpld

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this project except in compliance with the License. You may obtain a copy of the License in [LICENSE](LICENSE) or at <https://www.apache.org/licenses/LICENSE-2.0>.

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License.

Redistributions must keep the [NOTICE](NOTICE) file, as required by Section 4(d) of the License.

---

*Fiscal Nest Core — built by skrpld. Take it, embed it, build on top of it.*
