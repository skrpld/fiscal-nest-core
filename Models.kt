package budget

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

data class IncomeEvent(
    val name: String,
    val amount: BudgetInput,
    val dayOfMonth: Int,
)

data class ExpenseEvent(
    val name: String,
    val amount: BudgetInput,
    val dayOfMonth: Int,
    val isMandatory: Boolean = true,
)

/**
 * Criticality level of the safety cushion.
 */
data class CriticalityLevel(
    val name: String,
    val maxFillPct: BigDecimal,
    val piggyTakePct: BigDecimal,
    val remainingTakePct: BigDecimal,
) {
    init {
        require(piggyTakePct in BigDecimal.ZERO..BigDecimal.ONE) {
            "piggyTakePct must be between 0 and 1, got $piggyTakePct"
        }
        require(remainingTakePct in BigDecimal.ZERO..BigDecimal.ONE) {
            "remainingTakePct must be between 0 and 1, got $remainingTakePct"
        }
    }

    companion object {
        private val LEVELS: List<CriticalityLevel> = listOf(
            CriticalityLevel("Critical", BigDecimal("30"), BigDecimal("1.00"), BigDecimal("0.50")),
            CriticalityLevel("High",    BigDecimal("70"), BigDecimal("0.50"), BigDecimal("0.30")),
            CriticalityLevel("Low",     BigDecimal("100"), BigDecimal("0.20"), BigDecimal("0.10")),
        ).sortedBy { it.maxFillPct }

        fun select(fillPct: BigDecimal, cushionNeed: BigDecimal): CriticalityLevel? {
            if (cushionNeed <= BigDecimal.ZERO) return null
            return LEVELS.firstOrNull { fillPct < it.maxFillPct }
        }
    }
}

fun resolveEventDate(dayOfMonth: Int, periodStart: LocalDate): LocalDate {
    val lastDay = YearMonth.from(periodStart).lengthOfMonth()
    val safeDay = dayOfMonth.coerceIn(1, lastDay)
    return periodStart.withDayOfMonth(safeDay)
}
