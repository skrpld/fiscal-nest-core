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
 * Contains the complete forecast result for one period.
 */
data class ForecastResult(
    val distribution: DistributionResult,
    val dailyMetrics: DailyMetrics,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val openingBalance: BigDecimal,
    val closingBalance: BigDecimal,
    val liquidOnHand: BigDecimal,
    val mustReserve: BigDecimal,
    val available: BigDecimal,
    val receivedIncome: BigDecimal,
    val pendingIncome: BigDecimal,
    val upcomingMandatory: BigDecimal,
    val upcomingOptional: BigDecimal,
    val alreadySpent: BigDecimal,
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int
)

/**
 * Contains daily forecast metrics for a period.
 */
data class DailyMetrics(
    val dailyPlan: BigDecimal,
    val dailyActual: BigDecimal,
    val dailyCashflow: BigDecimal,
    val burnRate: BigDecimal
)
