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

/**
 * Contains the resolved calendar and liquidity snapshot for a forecast period.
 */
data class PeriodSnapshot(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val receivedIncome: BigDecimal,
    val pendingIncome: BigDecimal,
    val upcomingMandatory: BigDecimal,
    val upcomingOptional: BigDecimal
) {
    init {
        require(periodStart <= periodEnd) {
            "periodStart must not be after periodEnd"
        }
        require(currentDate >= periodStart && currentDate <= periodEnd) {
            "currentDate must be within [periodStart, periodEnd]"
        }
        require(daysInPeriod >= 1) { "daysInPeriod must be >= 1" }
        require(daysElapsed >= 0) { "daysElapsed must be >= 0" }
        require(daysRemaining >= 1) { "daysRemaining must be >= 1" }
        require(receivedIncome.signum() >= 0) {
            "Amount must be non-negative: receivedIncome"
        }
        require(pendingIncome.signum() >= 0) {
            "Amount must be non-negative: pendingIncome"
        }
        require(upcomingMandatory.signum() >= 0) {
            "Amount must be non-negative: upcomingMandatory"
        }
        require(upcomingOptional.signum() >= 0) {
            "Amount must be non-negative: upcomingOptional"
        }
    }
}
