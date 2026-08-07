package budget

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

sealed interface CrisisType {
    data object None : CrisisType
    data object Overspending : CrisisType
    data object Critical : CrisisType
    data object CushionDeficit : CrisisType
}

enum class EventFrequency {
    ONE_TIME,
    WEEKLY,
    MONTHLY,
    QUARTERLY,
    YEARLY
}

data class IncomeEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: BudgetInput,
    val dayOfMonth: Int,
    val frequency: EventFrequency = EventFrequency.MONTHLY,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val category: String? = null,
    val isReliable: Boolean = true,
) {
    init {
        require(dayOfMonth in 1..31) { "dayOfMonth must be between 1 and 31, got $dayOfMonth" }
    }
}

data class ExpenseEvent(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val amount: BudgetInput,
    val dayOfMonth: Int,
    val isMandatory: Boolean = true,
    val frequency: EventFrequency = EventFrequency.MONTHLY,
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val category: String? = null,
) {
    init {
        require(dayOfMonth in 1..31) { "dayOfMonth must be between 1 and 31, got $dayOfMonth" }
    }
}

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
        val CRITICAL = CriticalityLevel("Critical", BigDecimal("30"), BigDecimal("1.00"), BigDecimal("0.50"))
        val HIGH = CriticalityLevel("High", BigDecimal("70"), BigDecimal("0.50"), BigDecimal("0.30"))
        val LOW = CriticalityLevel("Low", BigDecimal("100"), BigDecimal("0.20"), BigDecimal("0.10"))

        private val LEVELS: List<CriticalityLevel> = listOf(CRITICAL, HIGH, LOW).sortedBy { it.maxFillPct }

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
