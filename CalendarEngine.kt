package budget

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth

/**
 * Period snapshot: tracks received/pending income and upcoming expenses.
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
    val upcomingOptional: BigDecimal,
)

fun buildSnapshot(
    periodStart: LocalDate,
    currentDate: LocalDate,
    incomeSchedule: List<IncomeEvent>? = null,
    expensesMandatorySchedule: List<ExpenseEvent>? = null,
    expensesOptionalSchedule: List<ExpenseEvent>? = null,
): PeriodSnapshot {
    requireNotNull(periodStart) { "periodStart is required" }
    requireNotNull(currentDate) { "currentDate is required" }

    val lastDay = YearMonth.from(periodStart).lengthOfMonth()
    val periodEnd = periodStart.withDayOfMonth(lastDay)

    require(!currentDate.isBefore(periodStart)) {
        "currentDate ($currentDate) cannot be before periodStart ($periodStart)"
    }
    require(!currentDate.isAfter(periodEnd)) {
        "currentDate ($currentDate) is outside the period ending $periodEnd"
    }

    val daysInPeriod = lastDay
    val daysElapsed = periodStart.until(currentDate).days
    val daysRemaining = periodEnd.until(currentDate).days + 1

    var receivedIncome = BigDecimal.ZERO
    var pendingIncome = BigDecimal.ZERO

    incomeSchedule?.forEach { event ->
        val eventDate = resolveEventDate(event.dayOfMonth, periodStart)
        val amount = DecimalUtils.sumInput(event.amount)
        if (!eventDate.isAfter(currentDate)) {
            receivedIncome += amount
        } else {
            pendingIncome += amount
        }
    }

    var upcomingMandatory = BigDecimal.ZERO
    var upcomingOptional = BigDecimal.ZERO

    expensesMandatorySchedule?.forEach { event ->
        val eventDate = resolveEventDate(event.dayOfMonth, periodStart)
        if (eventDate.isAfter(currentDate)) {
            upcomingMandatory += DecimalUtils.sumInput(event.amount)
        }
    }

    expensesOptionalSchedule?.forEach { event ->
        val eventDate = resolveEventDate(event.dayOfMonth, periodStart)
        if (eventDate.isAfter(currentDate)) {
            upcomingOptional += DecimalUtils.sumInput(event.amount)
        }
    }

    return PeriodSnapshot(
        periodStart = periodStart,
        periodEnd = periodEnd,
        currentDate = currentDate,
        daysInPeriod = daysInPeriod,
        daysElapsed = daysElapsed,
        daysRemaining = daysRemaining,
        receivedIncome = receivedIncome,
        pendingIncome = pendingIncome,
        upcomingMandatory = upcomingMandatory,
        upcomingOptional = upcomingOptional,
    )
}
