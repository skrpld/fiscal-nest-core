# Fiscal Nest Core

> **Repository:** `github.com/skrpld/fiscal-nest-core`  
> **Package:** `io.github.skrpld.fiscalnest.core`  
> **License:** BSD-3-Clause (repository-level)  
> **Language:** English only — code, APIs, KDoc, docs  
> **Scope:** Stateless Kotlin business-logic engine. No UI, no DB, no formatting.

---

## What is this?

Fiscal Nest Core is a **standalone, embeddable Kotlin engine** that calculates how income should be distributed across mandatory expenses, optional expenses, a safety cushion, and a piggy bank.

It can be plugged into:
- Android apps (`fiscal-nest-mobile`)
- Server-side services (JVM / Ktor / Spring)
- CLI tools
- Any JVM-compatible runtime

The engine is **pure business logic and stateless**. It does not render messages, store state, or assume a presentation layer. All localization, currency formatting, emoji usage, database persistence, and manual-adjustment workflows are the responsibility of the **client implementation**.

> **Golden Rule:** The engine must never import Android SDK, HTTP clients, locale-specific formatters, logging frameworks, or any platform-specific code. All behavior is configured through the public API.

---

## Two Modes

| Mode | Codename | Description |
|------|----------|-------------|
| **Light** | `WHAT_IF` | Snapshot calculation. Aggregate amounts in, distribution out. No dates required. |
| **Heavy** | `FORECAST` | Calendar-aware multi-period projection. Dated events with recurrence rules, daily metrics, carry-forward between periods. |

---

## Quick Start

The snippets below are **illustrative examples**. They show how a client application calls the engine. The engine itself never prints, logs, or formats output.

### WHAT_IF — Snapshot Calculation

```kotlin
import io.github.skrpld.fiscalnest.core.*
import java.math.BigDecimal
import java.math.RoundingMode

// 1. Configure the engine
val config = EngineConfig(
    roundingMode = RoundingMode.HALF_UP,
    moneyScale = 2,
    percentageScale = 1,
    criticalityLevels = listOf(
        CriticalityLevel(
            name = "Critical",
            maxFillPct = BigDecimal("30"),
            topupMode = TopupMode.PERCENT_OF_TARGET,
            topupValue = BigDecimal("0.20"),
            admissibilityPct = BigDecimal("0.80")
        ),
        CriticalityLevel(
            name = "Warning",
            maxFillPct = BigDecimal("70"),
            topupMode = TopupMode.PERCENT_OF_REMAINDER,
            topupValue = BigDecimal("0.10"),
            admissibilityPct = BigDecimal("0.50")
        )
    ),
    piggyBankMode = PiggyBankMode.FIXED_AMOUNT,
    piggyBankTarget = BigDecimal("5000"),
    piggyBankAdmissibilityPct = BigDecimal("0.80")
)

// 2. Light mode — snapshot calculation
val result = BudgetCalculator.calculateWhatIf(
    WhatIfInput(
        income = BigDecimal("50000"),
        mandatory = BigDecimal("20000"),
        optional = BigDecimal("10000"),
        cushionState = CushionState(
            current = BigDecimal("5000"),
            target = BigDecimal("20000")
        ),
        alreadySpent = BigDecimal.ZERO,
        config = config
    )
)

// 3. Inspect pure data output
println(result.expenseCrisis)      // false
println(result.cushionCrisis)      // true
println(result.cushionTopup)       // BigDecimal("4000.00")
println(result.piggyBankActual)    // BigDecimal("2400.00")
println(result.freeRemainder)      // BigDecimal("13600.00")
println(result.cushionCurrent)     // BigDecimal("9000.00") — post-distribution
```

### FORECAST — Multi-Period Projection

```kotlin
import java.time.LocalDate

val forecast = BudgetCalculator.calculateForecast(
    ForecastInput(
        incomeEvents = listOf(
            IncomeEvent(
                id = "salary",
                name = "Salary",
                amount = BigDecimal("50000"),
                recurrence = EventRecurrence.EveryNMonths(1, 1, LocalDate.of(2026, 1, 1)),
                startDate = LocalDate.of(2026, 1, 1),
                endDate = null,
                category = "work",
                isReliable = true
            )
        ),
        expenseEvents = listOf(
            ExpenseEvent(
                id = "rent",
                name = "Rent",
                amount = BigDecimal("20000"),
                isMandatory = true,
                recurrence = EventRecurrence.EveryNMonths(1, 5, LocalDate.of(2026, 1, 5)),
                startDate = LocalDate.of(2026, 1, 5),
                endDate = null,
                category = "housing"
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

// Inspect the first period
val period1 = forecast[0]
println(period1.periodStart)               // 2026-08-01
println(period1.openingBalance)            // 0
println(period1.distribution.freeRemainder) // free remainder after distribution
println(period1.dailyMetrics.dailyCashflow) // conservative daily budget
println(period1.closingBalance)            // carried to next period
```

---

## Documentation

| Document | What's inside |
|----------|---------------|
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | Entities, Remainder Hierarchy, Distribution Algorithm, Crisis Scenarios, Criticality Levels, Calendar Logic, Thread Safety, Error Handling |
| [docs/API.md](docs/API.md) | Public API contract, Configuration, Output data structures, Daily Metrics, Exception Reference, Visibility & Module Boundaries |
| [docs/GLOSSARY.md](docs/GLOSSARY.md) | Definitions of all business terms |
| [docs/DEVELOPMENT_PLAN.md](docs/DEVELOPMENT_PLAN.md) | Step-by-step implementation guide with self-contained prompts for each chat session |

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

---

## Project Structure

```
fiscal-nest-core/
  src/main/kotlin/skrpld/fiscalnest/core/
    // Public API — data classes & enums
    EngineConfig.kt
    CriticalityLevel.kt
    TopupMode.kt
    PiggyBankMode.kt
    IncomeEvent.kt
    ExpenseEvent.kt
    EventRecurrence.kt
    CushionState.kt
    WhatIfInput.kt
    ForecastInput.kt
    DistributionResult.kt
    ForecastResult.kt
    PeriodSnapshot.kt
    DailyMetrics.kt
    BudgetCalculator.kt          // Public facade

    // Internal implementation
    DistributionEngine.kt        // internal
    CalendarEngine.kt            // internal
    DecimalUtils.kt              // internal
    InputValidator.kt            // internal

  src/test/kotlin/skrpld/fiscalnest/core/
    DistributionEngineTest.kt
    CalendarEngineTest.kt
    BudgetCalculatorTest.kt

  docs/
    ARCHITECTURE.md
    API.md
    GLOSSARY.md
    DEVELOPMENT_PLAN.md
  README.md
  LICENSE
```

---

## Building & Testing

The project is a standard Gradle Kotlin/JVM module.

```bash
# Build
./gradlew build

# Run tests
./gradlew test

# Publish to local Maven (for client integration)
./gradlew publishToMavenLocal
```

### Gradle Dependencies (client)

```kotlin
dependencies {
    implementation("io.github.skrpld:fiscal-nest-core:1.0.0")
}
```

---

## Thread Safety

The engine is **stateless and thread-safe**. All public methods are pure functions with no mutable static state. You may safely share a single `EngineConfig` instance across threads and call `BudgetCalculator` concurrently from multiple coroutines or threads.

---

## Status

**Spec v1.0 is LOCKED.**  
All code must comply with the documents above. Any deviation is treated as a bug.

---

*Fiscal Nest Core — built by skrpld. Take it, embed it, build on top of it.*
