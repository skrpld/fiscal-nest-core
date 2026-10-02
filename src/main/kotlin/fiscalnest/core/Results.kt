/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Contains the monetary allocation and crisis state for a distribution.
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
