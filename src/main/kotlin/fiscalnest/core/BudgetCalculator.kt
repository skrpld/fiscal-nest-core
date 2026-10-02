/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal

/**
 * The engine facade and the only entry point for calculations. Stateless and thread-safe.
 *
 * Every call validates its input first and throws [IllegalArgumentException] before calculating
 * anything when the input is invalid.
 */
object BudgetCalculator {
    private val calendarEngine = CalendarEngine()
    private val distributionEngine = DistributionEngine()

    /**
     * Distributes aggregate amounts of one period, with no dates involved.
     *
     * @param input aggregate income, expenses, cushion state and configuration
     * @return the allocation along the remainder hierarchy and the crisis flags
     * @throws IllegalArgumentException if the input is invalid
     * @see WhatIfInput
     */
    fun calculateWhatIf(input: WhatIfInput): DistributionResult {
        InputValidator.validateWhatIf(input)
        return distributionEngine.distribute(
            income = input.income,
            mandatory = input.mandatory,
            optional = input.optional,
            cushionState = input.cushionState,
            config = input.config
        )
    }

    /**
     * Projects [ForecastInput.forecastPeriods] consecutive periods from scheduled events.
     *
     * Each period gets a plan over all of its events and a cash view split at its current date.
     * The closing balance and the post-distribution cushion balance of a period carry into the
     * next one. A carried free balance stays free money and is never redistributed to the cushion
     * or the piggy bank; a carried deficit is covered by the next plan before anything else.
     * Unscheduled spending applies to the first period only. Cost and result size grow
     * linearly with the number of periods and events.
     *
     * @param input events, first period, current date, horizon, cushion state and configuration
     * @return one [ForecastResult] per period, in chronological order
     * @throws IllegalArgumentException if the input is invalid
     * @see ForecastInput
     */
    fun calculateForecast(input: ForecastInput): List<ForecastResult> {
        InputValidator.validateForecast(input)
        val config = input.config
        val zero = DecimalUtils.quantizeMoney(BigDecimal.ZERO, config)
        val schedule = PeriodSchedule(input.periodStart, input.periodEnd)
        val results = ArrayList<ForecastResult>()
        var openingBalance = zero
        var cushionState = input.cushionState
        for (index in 0 until input.forecastPeriods) {
            val period = schedule.period(index)
            val isFirst = index == 0
            val snapshot = calendarEngine.buildSnapshot(
                periodStart = period.start,
                periodEnd = period.end,
                currentDate = if (isFirst) input.currentDate else period.start,
                incomeEvents = input.incomeEvents,
                expenseEvents = input.expenseEvents
            )
            val alreadySpent = if (isFirst) DecimalUtils.quantizeMoney(input.alreadySpent, config) else zero
            val cashFlow = cashFlow(snapshot, openingBalance, alreadySpent, config)
            val distribution = distributionEngine.distribute(
                income = openingBalance.min(zero) + cashFlow.receivedIncome + cashFlow.pendingIncome,
                mandatory = cashFlow.paidMandatory + cashFlow.upcomingMandatory,
                optional = cashFlow.paidOptional + cashFlow.upcomingOptional,
                cushionState = cushionState,
                config = config
            )
            val freeBalance = openingBalance.max(zero) + distribution.freeRemainder
            val closingBalance = freeBalance - alreadySpent
            results += ForecastResult(
                periodStart = snapshot.periodStart,
                periodEnd = snapshot.periodEnd,
                currentDate = snapshot.currentDate,
                daysInPeriod = snapshot.daysInPeriod,
                daysElapsed = snapshot.daysElapsed,
                daysRemaining = snapshot.daysRemaining,
                openingBalance = openingBalance,
                freeBalance = freeBalance,
                closingBalance = closingBalance,
                distribution = distribution,
                cashFlow = cashFlow,
                dailyMetrics = dailyMetrics(snapshot, freeBalance, distribution, cashFlow, config)
            )
            openingBalance = closingBalance
            cushionState = CushionState(distribution.cushionCurrent, cushionState.target)
        }
        return results
    }

    private fun cashFlow(
        snapshot: PeriodSnapshot,
        openingBalance: BigDecimal,
        alreadySpent: BigDecimal,
        config: EngineConfig
    ): CashFlow {
        val receivedIncome = DecimalUtils.quantizeMoney(snapshot.receivedIncome, config)
        val paidMandatory = DecimalUtils.quantizeMoney(snapshot.paidMandatory, config)
        val paidOptional = DecimalUtils.quantizeMoney(snapshot.paidOptional, config)
        val upcomingMandatory = DecimalUtils.quantizeMoney(snapshot.upcomingMandatory, config)
        val liquidOnHand = openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent
        return CashFlow(
            receivedIncome = receivedIncome,
            pendingIncome = DecimalUtils.quantizeMoney(snapshot.pendingIncome, config),
            paidMandatory = paidMandatory,
            upcomingMandatory = upcomingMandatory,
            paidOptional = paidOptional,
            upcomingOptional = DecimalUtils.quantizeMoney(snapshot.upcomingOptional, config),
            alreadySpent = alreadySpent,
            liquidOnHand = liquidOnHand,
            mustReserve = upcomingMandatory,
            available = liquidOnHand - upcomingMandatory
        )
    }

    private fun dailyMetrics(
        snapshot: PeriodSnapshot,
        freeBalance: BigDecimal,
        distribution: DistributionResult,
        cashFlow: CashFlow,
        config: EngineConfig
    ): DailyMetrics = DailyMetrics(
        dailyPlan = DecimalUtils.perDay(freeBalance, snapshot.daysInPeriod, config),
        dailyActual = DecimalUtils.perDay(freeBalance - cashFlow.alreadySpent, snapshot.daysRemaining, config),
        dailyCashflow = DecimalUtils.perDay(cashFlow.available - distribution.cushionTopup, snapshot.daysRemaining, config),
        burnRate = DecimalUtils.perDay(cashFlow.alreadySpent, snapshot.daysElapsed + 1, config)
    )
}
