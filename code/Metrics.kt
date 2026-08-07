package budget

import java.math.BigDecimal
import java.math.RoundingMode
import budget.DecimalUtils.quantizeMoney

data class DailyMetrics(
    val plan: Double,
    val actual: Double,
    val cashflow: Double,
    val burnRate: Double,
) {
    companion object {
        fun compute(
            snapshot: PeriodSnapshot,
            distResult: DistributionResult,
            alreadySpent: BigDecimal,
            monthlyBaseRemaining: BigDecimal,
        ): DailyMetrics {
            val di = BigDecimal(snapshot.daysInPeriod)
            val dr = BigDecimal(snapshot.daysRemaining)

            val dailyPlan = if (di > BigDecimal.ZERO) {
                quantizeMoney(monthlyBaseRemaining.divide(di, 10, RoundingMode.HALF_UP)).toDouble()
            } else 0.0

            val dailyActual = if (dr > BigDecimal.ZERO) {
                quantizeMoney(distResult.remaining.divide(dr, 10, RoundingMode.HALF_UP)).toDouble()
            } else 0.0

            val liquid = snapshot.receivedIncome - alreadySpent
            val mustReserve = snapshot.upcomingMandatory
            val cashflowNumerator = liquid - mustReserve - distResult.topup
            val dailyCashflow = if (dr > BigDecimal.ZERO) {
                quantizeMoney(cashflowNumerator.divide(dr, 10, RoundingMode.HALF_UP)).toDouble()
            } else 0.0

            val burnDivisor = BigDecimal(snapshot.daysElapsed + 1)
            val burn = quantizeMoney(alreadySpent.divide(burnDivisor, 10, RoundingMode.HALF_UP)).toDouble()

            return DailyMetrics(
                plan = dailyPlan,
                actual = dailyActual,
                cashflow = dailyCashflow,
                burnRate = burn,
            )
        }
    }
}
