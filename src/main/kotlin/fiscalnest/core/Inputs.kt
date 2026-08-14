package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Represents the current and target cushion balances.
 */
data class CushionState(
    val current: BigDecimal,
    val target: BigDecimal
) {
    init {
        require(current.signum() >= 0) {
            "Amount must be non-negative: current"
        }
        require(target.signum() >= 0) {
            "Amount must be non-negative: target"
        }
    }
}

/**
 * Defines the inputs for a time-agnostic what-if distribution.
 */
data class WhatIfInput(
    val income: BigDecimal,
    val mandatory: BigDecimal,
    val optional: BigDecimal,
    val cushionState: CushionState,
    val alreadySpent: BigDecimal,
    val config: EngineConfig
) {
    init {
        require(income.signum() >= 0) { "Amount must be non-negative: income" }
        require(mandatory.signum() >= 0) { "Amount must be non-negative: mandatory" }
        require(optional.signum() >= 0) { "Amount must be non-negative: optional" }
        require(alreadySpent.signum() >= 0) {
            "Amount must be non-negative: alreadySpent"
        }
    }
}

/**
 * Defines the inputs for a multi-period forecast.
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
        require(periodStart <= periodEnd) {
            "periodStart must not be after periodEnd"
        }
        require(currentDate >= periodStart && currentDate <= periodEnd) {
            "currentDate must be within [periodStart, periodEnd]"
        }
        require(alreadySpent.signum() >= 0) {
            "Amount must be non-negative: alreadySpent"
        }
        require(forecastPeriods >= 1) {
            "forecastPeriods must be >= 1"
        }
    }
}
