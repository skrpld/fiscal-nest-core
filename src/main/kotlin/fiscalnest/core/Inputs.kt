/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Safety cushion balances.
 *
 * @property current non-negative balance before the calculation
 * @property target non-negative balance the cushion should reach
 * @throws IllegalArgumentException if a balance is negative
 */
data class CushionState(
    val current: BigDecimal,
    val target: BigDecimal
) {
    init {
        InputValidator.validateCushionState(this)
    }
}

/**
 * Input of the time-agnostic what-if mode: aggregate amounts for one period, no dates.
 *
 * @property income non-negative total income
 * @property mandatory non-negative total of mandatory expenses
 * @property optional non-negative total of optional expenses
 * @property cushionState cushion balances before the distribution
 * @property config engine configuration
 * @throws IllegalArgumentException if any amount is negative
 * @see BudgetCalculator.calculateWhatIf
 */
data class WhatIfInput(
    val income: BigDecimal,
    val mandatory: BigDecimal,
    val optional: BigDecimal,
    val cushionState: CushionState,
    val config: EngineConfig
) {
    init {
        InputValidator.validateWhatIf(this)
    }
}

/**
 * Input of the calendar-aware forecast mode.
 *
 * @property incomeEvents scheduled income events
 * @property expenseEvents scheduled mandatory and optional expense events
 * @property periodStart first day of the first period, inclusive
 * @property periodEnd last day of the first period, inclusive; not before [periodStart]
 * @property currentDate today, within `[periodStart, periodEnd]`
 * @property alreadySpent non-negative unscheduled spending in the first period up to and including
 * [currentDate]; scheduled expense events must not be included, they are counted by date
 * @property forecastPeriods number of consecutive periods to project, `>= 1`
 * @property config engine configuration
 * @property cushionState cushion balances at the start of the first period
 * @throws IllegalArgumentException if the dates, amounts or horizon are invalid
 * @see BudgetCalculator.calculateForecast
 */
data class ForecastInput(
    val incomeEvents: List<IncomeEvent>,
    val expenseEvents: List<ExpenseEvent>,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,
    val alreadySpent: BigDecimal,
    val forecastPeriods: Int,
    val config: EngineConfig,
    val cushionState: CushionState
) {
    init {
        InputValidator.validateForecast(this)
    }
}
