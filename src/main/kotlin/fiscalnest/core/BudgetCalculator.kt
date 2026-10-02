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
            ).quantized(config)
            val alreadySpent = if (isFirst) DecimalUtils.quantizeMoney(input.alreadySpent, config) else zero
            val distribution = distributionEngine.distribute(
                income = openingBalance.min(zero) + snapshot.receivedIncome + snapshot.pendingIncome,
                mandatory = snapshot.paidMandatory + snapshot.upcomingMandatory,
                optional = snapshot.paidOptional + snapshot.upcomingOptional,
                cushionState = cushionState,
                config = config
            )
            val cashFlow = cashFlow(snapshot, openingBalance, alreadySpent, distribution, config)
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
                dailyMetrics = dailyMetrics(snapshot, freeBalance, cashFlow, config)
            )
            openingBalance = closingBalance
            cushionState = CushionState(distribution.cushionCurrent, cushionState.target)
        }
        return results
    }

    private fun PeriodSnapshot.quantized(config: EngineConfig): PeriodSnapshot = copy(
        receivedIncome = DecimalUtils.quantizeMoney(receivedIncome, config),
        pendingIncome = DecimalUtils.quantizeMoney(pendingIncome, config),
        paidMandatory = DecimalUtils.quantizeMoney(paidMandatory, config),
        upcomingMandatory = DecimalUtils.quantizeMoney(upcomingMandatory, config),
        paidOptional = DecimalUtils.quantizeMoney(paidOptional, config),
        upcomingOptional = DecimalUtils.quantizeMoney(upcomingOptional, config)
    )

    private fun cashFlow(
        snapshot: PeriodSnapshot,
        openingBalance: BigDecimal,
        alreadySpent: BigDecimal,
        distribution: DistributionResult,
        config: EngineConfig
    ): CashFlow {
        val liquidOnHand = openingBalance + snapshot.receivedIncome - snapshot.paidMandatory -
            snapshot.paidOptional - alreadySpent
        val reserves = mapOf(
            CashReserve.CUSHION_TOPUP to distribution.cushionTopup,
            CashReserve.PIGGY_BANK to distribution.piggyBankActual,
            CashReserve.UPCOMING_OPTIONAL to snapshot.upcomingOptional
        )
        val mustReserve = config.cashReserves.fold(snapshot.upcomingMandatory) { total, reserve ->
            total + reserves.getValue(reserve)
        }
        return CashFlow(
            receivedIncome = snapshot.receivedIncome,
            pendingIncome = snapshot.pendingIncome,
            paidMandatory = snapshot.paidMandatory,
            upcomingMandatory = snapshot.upcomingMandatory,
            paidOptional = snapshot.paidOptional,
            upcomingOptional = snapshot.upcomingOptional,
            alreadySpent = alreadySpent,
            liquidOnHand = liquidOnHand,
            mustReserve = mustReserve,
            available = liquidOnHand - mustReserve
        )
    }

    private fun dailyMetrics(
        snapshot: PeriodSnapshot,
        freeBalance: BigDecimal,
        cashFlow: CashFlow,
        config: EngineConfig
    ): DailyMetrics = DailyMetrics(
        dailyPlan = DecimalUtils.perDay(freeBalance, snapshot.daysInPeriod, config),
        dailyActual = DecimalUtils.perDay(freeBalance - cashFlow.alreadySpent, snapshot.daysRemaining, config),
        dailyCashflow = DecimalUtils.perDay(cashFlow.available, snapshot.daysRemaining, config),
        burnRate = DecimalUtils.perDay(cashFlow.alreadySpent, snapshot.daysElapsed + 1, config)
    )
}
