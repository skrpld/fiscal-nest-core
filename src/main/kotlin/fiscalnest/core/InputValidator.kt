/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.DateTimeException
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Single source of truth for input validation.
 *
 * Public data classes run these checks from their `init` blocks, so an invalid instance cannot be
 * built through a constructor or `copy`. [BudgetCalculator] runs the same checks again before every
 * calculation, because deserializers that bypass constructors can still produce invalid instances.
 * Every failure throws [IllegalArgumentException] with a plain-English message.
 */
internal object InputValidator {
    /**
     * Largest accepted number of integer digits and of fractional digits in any decimal input, and
     * the largest accepted `moneyScale` / `percentageScale`.
     *
     * The limit rejects pathological values such as `1E+999999999` or `1E-999999999`, whose
     * quantization would allocate gigabytes of memory, while staying far above any realistic
     * monetary amount or ratio.
     */
    const val MAX_DIGITS: Int = 1000

    /**
     * Validates a what-if calculation input, including its cushion state and configuration.
     *
     * @param input input to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    fun validateWhatIf(input: WhatIfInput) {
        requireAmount(input.income, "income")
        requireAmount(input.mandatory, "mandatory")
        requireAmount(input.optional, "optional")
        validateCushionState(input.cushionState)
        validateConfig(input.config)
    }

    /**
     * Validates a forecast input, including its events, cushion state and configuration.
     *
     * @param input input to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    fun validateForecast(input: ForecastInput) {
        validatePeriod(input.periodStart, input.periodEnd, input.currentDate)
        requireAmount(input.alreadySpent, "alreadySpent")
        require(input.forecastPeriods >= 1) {
            "forecastPeriods must be >= 1"
        }
        require(horizonFits(input.periodStart, input.periodEnd, input.forecastPeriods)) {
            "Forecast horizon exceeds the supported date range"
        }
        input.incomeEvents.forEach(::validateIncomeEvent)
        input.expenseEvents.forEach(::validateExpenseEvent)
        validateCushionState(input.cushionState)
        validateConfig(input.config)
    }

    /**
     * Validates the engine configuration, including every criticality level.
     *
     * @param config configuration to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    fun validateConfig(config: EngineConfig) {
        requireScale(config.moneyScale, "moneyScale")
        requireScale(config.percentageScale, "percentageScale")
        require(config.criticalityLevels.isNotEmpty()) {
            "Criticality levels must be non-empty and sorted by maxFillPct ascending."
        }
        config.criticalityLevels.forEach(::validateCriticalityLevel)
        config.criticalityLevels.zipWithNext().forEach { (first, second) ->
            val order = first.maxFillPct.compareTo(second.maxFillPct)
            require(order != 0) {
                "Criticality level ranges overlap: ${first.name} and ${second.name}"
            }
            require(order < 0) {
                "Criticality levels must be non-empty and sorted by maxFillPct ascending."
            }
        }
        requireAmount(config.piggyBankTarget, "piggyBankTarget")
        if (config.piggyBankMode == PiggyBankMode.PERCENT_OF_REMAINDER) {
            requirePercentage(config.piggyBankTarget, "piggyBankTarget")
        }
        requirePercentage(config.piggyBankAdmissibilityPct, "piggyBankAdmissibilityPct")
    }

    /**
     * Validates one criticality level.
     *
     * @param level level to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    fun validateCriticalityLevel(level: CriticalityLevel) {
        requirePercentage(level.maxFillPct, "maxFillPct")
        require(level.maxFillPct.signum() > 0) {
            "Percentage must be in (0,1]: maxFillPct"
        }
        requirePercentage(level.topupValue, "topupValue")
        requirePercentage(level.admissibilityPct, "admissibilityPct")
    }

    /**
     * Validates cushion balances.
     *
     * @param state cushion state to validate
     * @throws IllegalArgumentException if a balance is negative or out of the supported range
     */
    fun validateCushionState(state: CushionState) {
        requireAmount(state.current, "cushionCurrent")
        requireAmount(state.target, "cushionTarget")
    }

    /**
     * Validates an income event and its recurrence.
     *
     * @param event event to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    fun validateIncomeEvent(event: IncomeEvent) {
        requireAmount(event.amount, "amount")
        validateEventDates(event.startDate, event.endDate)
        validateRecurrence(event.recurrence)
    }

    /**
     * Validates an expense event and its recurrence.
     *
     * @param event event to validate
     * @throws IllegalArgumentException if any field is invalid
     */
    fun validateExpenseEvent(event: ExpenseEvent) {
        requireAmount(event.amount, "amount")
        validateEventDates(event.startDate, event.endDate)
        validateRecurrence(event.recurrence)
    }

    /**
     * Validates recurrence parameters.
     *
     * @param recurrence recurrence to validate
     * @throws IllegalArgumentException if `n` is not positive or `dayOfMonth` is outside `1..31`
     */
    fun validateRecurrence(recurrence: EventRecurrence) {
        when (recurrence) {
            EventRecurrence.OneTime -> Unit
            is EventRecurrence.EveryNDays -> requirePositive(recurrence.n, "n")
            is EventRecurrence.EveryNMonths -> {
                requirePositive(recurrence.n, "n")
                requirePositive(recurrence.dayOfMonth, "dayOfMonth")
                require(recurrence.dayOfMonth <= 31) {
                    "Recurrence parameter must be <= 31: dayOfMonth"
                }
            }
        }
    }

    private fun validatePeriod(periodStart: LocalDate, periodEnd: LocalDate, currentDate: LocalDate) {
        require(periodStart <= periodEnd) {
            "periodStart must not be after periodEnd"
        }
        require(currentDate >= periodStart && currentDate <= periodEnd) {
            "currentDate must be within [periodStart, periodEnd]"
        }
        require(ChronoUnit.DAYS.between(periodStart, periodEnd) < Int.MAX_VALUE) {
            "Period length must not exceed ${Int.MAX_VALUE} days"
        }
    }

    private fun horizonFits(periodStart: LocalDate, periodEnd: LocalDate, forecastPeriods: Int): Boolean =
        try {
            PeriodSchedule(periodStart, periodEnd).period(forecastPeriods - 1)
            true
        } catch (_: DateTimeException) {
            false
        } catch (_: ArithmeticException) {
            false
        }

    private fun validateEventDates(startDate: LocalDate, endDate: LocalDate?) {
        require(endDate == null || startDate <= endDate) {
            "startDate must not be after endDate"
        }
    }

    private fun requirePositive(value: Int, name: String) {
        require(value >= 1) {
            "Recurrence parameter must be positive: $name"
        }
    }

    private fun requireScale(scale: Int, name: String) {
        require(scale >= 0) {
            "Scale must be >= 0: $name"
        }
        require(scale <= MAX_DIGITS) {
            "Scale must be <= $MAX_DIGITS: $name"
        }
    }

    private fun requireAmount(value: BigDecimal, name: String) {
        require(value.signum() >= 0) {
            "Amount must be non-negative: $name"
        }
        requireSupportedDigits(value, name)
    }

    private fun requirePercentage(value: BigDecimal, name: String) {
        require(value.signum() >= 0 && value <= BigDecimal.ONE) {
            "Percentage must be in [0,1]: $name"
        }
        requireSupportedDigits(value, name)
    }

    private fun requireSupportedDigits(value: BigDecimal, name: String) {
        val integerDigits = value.precision().toLong() - value.scale()
        require(value.scale() <= MAX_DIGITS && integerDigits <= MAX_DIGITS) {
            "Number exceeds supported precision: $name"
        }
    }
}
