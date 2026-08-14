package fiscalnest.core

import java.math.BigDecimal

/**
 * Validates public engine inputs and configuration before calculation.
 */
internal object InputValidator {
    /**
     * Validates a what-if calculation input.
     *
     * @param input input to validate
     * @throws IllegalArgumentException if the input is invalid
     */
    fun validateWhatIf(input: WhatIfInput) {
        requireMoney(input.income, "income")
        requireMoney(input.mandatory, "mandatory")
        requireMoney(input.optional, "optional")
        requireMoney(input.alreadySpent, "alreadySpent")
        validateCushion(input.cushionState)
        validateConfig(input.config)
    }

    /**
     * Validates a forecast calculation input.
     *
     * @param input input to validate
     * @throws IllegalArgumentException if the input is invalid
     */
    fun validateForecast(input: ForecastInput) {
        validatePeriod(input.periodStart, input.periodEnd, input.currentDate)
        requireMoney(input.alreadySpent, "alreadySpent")
        require(input.forecastPeriods >= 1) {
            "forecastPeriods must be >= 1"
        }
        validateCushion(input.cushionState)
        input.incomeEvents.forEachIndexed { index, event ->
            validateIncomeEvent(event, "incomeEvents[$index]")
        }
        input.expenseEvents.forEachIndexed { index, event ->
            validateExpenseEvent(event, "expenseEvents[$index]")
        }
        validateConfig(input.config)
    }

    /**
     * Validates engine configuration.
     *
     * @param config configuration to validate
     * @throws IllegalArgumentException if the configuration is invalid
     */
    fun validateConfig(config: EngineConfig) {
        require(config.moneyScale >= 0) {
            "Scale must be >= 0: moneyScale"
        }
        require(config.percentageScale >= 0) {
            "Scale must be >= 0: percentageScale"
        }
        require(config.criticalityLevels.isNotEmpty()) {
            "Criticality levels must be non-empty and sorted by maxFillPct ascending."
        }

        config.criticalityLevels.forEachIndexed { index, level ->
            requirePercentage(level.maxFillPct, "criticalityLevels[$index].maxFillPct")
            require(level.maxFillPct.signum() > 0) {
                "Percentage must be in [0,1]: maxFillPct"
            }
            requirePercentage(level.topupValue, "criticalityLevels[$index].topupValue")
            requirePercentage(level.admissibilityPct, "criticalityLevels[$index].admissibilityPct")
            require(level.topupValue.scale() >= 0) {
                "Percentage must be in [0,1]: topupValue"
            }
        }

        config.criticalityLevels.zipWithNext().forEach { (first, second) ->
            require(first.maxFillPct < second.maxFillPct) {
                if (first.maxFillPct == second.maxFillPct) {
                    "Criticality level ranges overlap: ${first.name} and ${second.name}"
                } else {
                    "Criticality levels must be non-empty and sorted by maxFillPct ascending."
                }
            }
        }

        DecimalUtils.requireNonNegative(config.piggyBankTarget, "piggyBankTarget")
        if (config.piggyBankMode == PiggyBankMode.PERCENT_OF_REMAINDER) {
            requirePercentage(config.piggyBankTarget, "piggyBankTarget")
        }
        requirePercentage(config.piggyBankAdmissibilityPct, "piggyBankAdmissibilityPct")
    }

    private fun validateCushion(cushionState: CushionState) {
        requireMoney(cushionState.current, "cushionCurrent")
        requireMoney(cushionState.target, "cushionTarget")
    }

    private fun validatePeriod(
        periodStart: java.time.LocalDate,
        periodEnd: java.time.LocalDate,
        currentDate: java.time.LocalDate
    ) {
        require(periodStart <= periodEnd) {
            "periodStart must not be after periodEnd"
        }
        require(currentDate >= periodStart && currentDate <= periodEnd) {
            "currentDate must be within [periodStart, periodEnd]"
        }
    }

    private fun validateIncomeEvent(event: IncomeEvent, name: String) {
        requireMoney(event.amount, "$name.amount")
        validateEventDates(event.startDate, event.endDate, name)
        validateRecurrence(event.recurrence, name)
    }

    private fun validateExpenseEvent(event: ExpenseEvent, name: String) {
        requireMoney(event.amount, "$name.amount")
        validateEventDates(event.startDate, event.endDate, name)
        validateRecurrence(event.recurrence, name)
    }

    private fun validateEventDates(
        startDate: java.time.LocalDate,
        endDate: java.time.LocalDate?,
        name: String
    ) {
        if (endDate != null) {
            require(startDate <= endDate) {
                "$name.startDate must not be after $name.endDate"
            }
        }
    }

    private fun validateRecurrence(
        recurrence: EventRecurrence,
        name: String
    ) {
        when (recurrence) {
            is EventRecurrence.OneTime -> Unit
            is EventRecurrence.EveryNDays -> {
                require(recurrence.n >= 1) {
                    "Recurrence parameter must be positive: n"
                }
            }
            is EventRecurrence.EveryNMonths -> {
                require(recurrence.n >= 1) {
                    "Recurrence parameter must be positive: n"
                }
                require(recurrence.dayOfMonth >= 1) {
                    "Recurrence parameter must be positive: dayOfMonth"
                }
            }
        }
    }

    private fun requireMoney(value: BigDecimal, name: String) {
        DecimalUtils.requireNonNegative(value, name)
    }

    private fun requirePercentage(value: BigDecimal, name: String) {
        require(value.compareTo(BigDecimal.ZERO) >= 0 && value.compareTo(BigDecimal.ONE) <= 0) {
            "Percentage must be in [0,1]: $name"
        }
    }
}
