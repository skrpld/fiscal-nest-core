package budget.formatters

import budget.PeriodSnapshot
import budget.DailyMetrics
import budget.DecimalUtils.quantizeMoney
import java.math.BigDecimal

class TimeMessageFormatter {
    fun format(snapshot: PeriodSnapshot, daily: DailyMetrics): String {
        val parts = mutableListOf<String>()

        val dayNum = snapshot.daysElapsed + 1
        val totalDays = snapshot.daysInPeriod
        val displayDay = if (dayNum > totalDays) totalDays else dayNum

        parts.add("📅 Day $displayDay of $totalDays. $${snapshot.daysRemaining} days remaining.")

        if (snapshot.pendingIncome > BigDecimal.ZERO) {
            parts.add("Pending income: ${quantizeMoney(snapshot.pendingIncome)}.")
        }
        if (snapshot.upcomingMandatory > BigDecimal.ZERO) {
            parts.add("Upcoming mandatory expenses: ${quantizeMoney(snapshot.upcomingMandatory)}.")
        }
        if (snapshot.upcomingOptional > BigDecimal.ZERO) {
            parts.add("Upcoming optional expenses: ${quantizeMoney(snapshot.upcomingOptional)}.")
        }

        parts.add(
            "Daily: plan ${quantizeMoney(daily.plan)} | " +
            "actual ${quantizeMoney(daily.actual)} | " +
            "conservative ${quantizeMoney(daily.cashflow)}"
        )
        parts.add("Current spending rate: ${quantizeMoney(daily.burnRate)}/day.")

        return parts.joinToString(" ")
    }
}
