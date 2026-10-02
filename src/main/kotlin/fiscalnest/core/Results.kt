/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Allocation of one period's money along the remainder hierarchy, plus independent crisis flags.
 *
 * Monetary values have [EngineConfig.moneyScale] decimals and always satisfy
 * `netRemainder == cushionTopup + piggyBankActual + freeRemainder`. Ratios use the `0.0..1.0`
 * scale with [EngineConfig.percentageScale] decimals.
 *
 * @property expenseCrisis `true` when income does not cover mandatory plus optional expenses
 * (`netRemainder < 0`); the cushion top-up and the piggy bank are then `0`
 * @property cushionCrisis `true` when a criticality level matched the cushion fill ratio; reported
 * even during an expense crisis, when no top-up can be made
 * @property cushionOverfilled `true` when the cushion balance exceeds its target
 * @property piggyBankCappedByAdmissibility `true` when the piggy bank received less than
 * [piggyBankTarget] because of the admissibility cap or a too small remainder; always `false`
 * during an expense crisis
 * @property totalIncome income the distribution started from
 * @property totalMandatory mandatory expenses
 * @property totalOptional optional expenses
 * @property rawRemainder `totalIncome - totalMandatory`
 * @property netRemainder `rawRemainder - totalOptional`
 * @property cushionTopup amount moved to the cushion
 * @property cushionCurrent cushion balance after the distribution: input balance plus [cushionTopup]
 * @property cushionTarget cushion target
 * @property cushionFillPct cushion fill ratio before the distribution, `1` when the target is `0`
 * @property cushionNeed `max(0, cushionTarget - balance before the distribution)`
 * @property piggyBankActual amount moved to the piggy bank
 * @property piggyBankTarget piggy bank amount the configuration asked for
 * @property freeRemainder money left for day-to-day spending; negative during an expense crisis
 * @property expenseDeficit `-netRemainder` during an expense crisis, otherwise `0`
 * @property activeCriticalityLevel [CriticalityLevel.name] of the matched level, or `null`
 * @see BudgetCalculator.calculateWhatIf
 * @see ForecastResult.distribution
 */
data class DistributionResult(
    val expenseCrisis: Boolean,
    val cushionCrisis: Boolean,
    val cushionOverfilled: Boolean,
    val piggyBankCappedByAdmissibility: Boolean,
    val totalIncome: BigDecimal,
    val totalMandatory: BigDecimal,
    val totalOptional: BigDecimal,
    val rawRemainder: BigDecimal,
    val netRemainder: BigDecimal,
    val cushionTopup: BigDecimal,
    val cushionCurrent: BigDecimal,
    val cushionTarget: BigDecimal,
    val cushionFillPct: BigDecimal,
    val cushionNeed: BigDecimal,
    val piggyBankActual: BigDecimal,
    val piggyBankTarget: BigDecimal,
    val freeRemainder: BigDecimal,
    val expenseDeficit: BigDecimal,
    val activeCriticalityLevel: String?
)

/**
 * Forecast of one period. It combines two independent views of the same events:
 *
 * - the period plan, [distribution]: every event of the period, past and future, is counted, so
 *   income and expenses are compared on the same whole-period basis, exactly like what-if mode;
 * - the cash view, [cashFlow]: only events dated on or before [currentDate] have happened, for
 *   income and expenses alike.
 *
 * @property periodStart first day of the period
 * @property periodEnd last day of the period
 * @property currentDate date the cash view is taken at: the input's current date for the first
 * period, [periodStart] for later ones
 * @property daysInPeriod days in `[periodStart, periodEnd]`
 * @property daysElapsed days in `[periodStart, currentDate)`
 * @property daysRemaining days in `[currentDate, periodEnd]`; `1` on the last day
 * @property openingBalance [closingBalance] of the previous period, `0` for the first period;
 * negative when a deficit is carried in
 * @property freeBalance free money of the period: a positive [openingBalance] plus
 * `distribution.freeRemainder`. Carried free money stays free; it never reaches the cushion or
 * the piggy bank
 * @property closingBalance `freeBalance - cashFlow.alreadySpent`: free money left at the end of
 * the period if nothing else unscheduled is spent; becomes the next opening balance
 * @property distribution period plan built from all income, mandatory and optional expenses of
 * the period; a negative [openingBalance] is deducted from its income so a deficit is covered
 * before the cushion and the piggy bank
 * @property cashFlow money that has actually moved as of [currentDate]
 * @property dailyMetrics daily budget figures derived from the plan and the cash view
 * @see BudgetCalculator.calculateForecast
 */
data class ForecastResult(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val openingBalance: BigDecimal,
    val freeBalance: BigDecimal,
    val closingBalance: BigDecimal,
    val distribution: DistributionResult,
    val cashFlow: CashFlow,
    val dailyMetrics: DailyMetrics
)

/**
 * Cash view of a forecast period as of [ForecastResult.currentDate]. Income and expenses are both
 * split by date: on or before the current date they have happened, after it they are still ahead.
 *
 * @property receivedIncome income dated on or before the current date
 * @property pendingIncome income dated after the current date
 * @property paidMandatory mandatory expenses dated on or before the current date
 * @property upcomingMandatory mandatory expenses dated after the current date
 * @property paidOptional optional expenses dated on or before the current date
 * @property upcomingOptional optional expenses dated after the current date
 * @property alreadySpent unscheduled spending so far; only the first period has any
 * @property liquidOnHand
 * `openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent`
 * @property mustReserve [upcomingMandatory] plus every amount selected in
 * [EngineConfig.cashReserves]: the planned cushion top-up, the planned piggy bank amount, and
 * [upcomingOptional]
 * @property available `liquidOnHand - mustReserve`: cash that can be spent now; negative when the
 * reserves exceed the cash on hand, for example before payday
 */
data class CashFlow(
    val receivedIncome: BigDecimal,
    val pendingIncome: BigDecimal,
    val paidMandatory: BigDecimal,
    val upcomingMandatory: BigDecimal,
    val paidOptional: BigDecimal,
    val upcomingOptional: BigDecimal,
    val alreadySpent: BigDecimal,
    val liquidOnHand: BigDecimal,
    val mustReserve: BigDecimal,
    val available: BigDecimal
)

/**
 * Daily budget figures of a forecast period, rounded to [EngineConfig.moneyScale].
 *
 * @property dailyPlan `freeBalance / daysInPeriod`: even split of the period's free money
 * @property dailyActual `(freeBalance - alreadySpent) / daysRemaining`: free money still unspent
 * per remaining day
 * @property dailyCashflow `available / daysRemaining`: per-day amount from cash on hand after the
 * reserves selected in [EngineConfig.cashReserves]
 * @property burnRate `alreadySpent / (daysElapsed + 1)`: average unscheduled spending per day so
 * far, today included
 */
data class DailyMetrics(
    val dailyPlan: BigDecimal,
    val dailyActual: BigDecimal,
    val dailyCashflow: BigDecimal,
    val burnRate: BigDecimal
)
