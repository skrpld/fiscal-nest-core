/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * A contiguous date range, both ends inclusive.
 *
 * @property start first day
 * @property end last day
 */
internal data class Period(val start: LocalDate, val end: LocalDate)

/**
 * Calendar figures of one forecast period as of [currentDate].
 *
 * Event occurrences dated on or before [currentDate] are received or paid; later ones are pending
 * or upcoming. Amounts are exact sums of event amounts; callers quantize them.
 *
 * @property periodStart first day of the period
 * @property periodEnd last day of the period
 * @property currentDate date the split between past and future is made at
 * @property daysInPeriod days in `[periodStart, periodEnd]`
 * @property daysElapsed days in `[periodStart, currentDate)`
 * @property daysRemaining days in `[currentDate, periodEnd]`; `1` on the last day
 * @property receivedIncome income dated in `[periodStart, currentDate]`
 * @property pendingIncome income dated in `(currentDate, periodEnd]`
 * @property paidMandatory mandatory expenses dated in `[periodStart, currentDate]`
 * @property upcomingMandatory mandatory expenses dated in `(currentDate, periodEnd]`
 * @property paidOptional optional expenses dated in `[periodStart, currentDate]`
 * @property upcomingOptional optional expenses dated in `(currentDate, periodEnd]`
 */
internal data class PeriodSnapshot(
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val receivedIncome: BigDecimal,
    val pendingIncome: BigDecimal,
    val paidMandatory: BigDecimal,
    val upcomingMandatory: BigDecimal,
    val paidOptional: BigDecimal,
    val upcomingOptional: BigDecimal
)

/**
 * The chain of consecutive forecast periods that starts with a given first period.
 *
 * When the first period spans whole calendar months (the day after its end is its start plus `N`
 * months), every period spans `N` months counted from the first start, so month boundaries stay
 * aligned: August 1-31 is followed by September 1-30, the 5th-to-4th cycle stays on the 5th.
 * Otherwise every period has the first period's length in days.
 *
 * @param firstStart first day of the first period
 * @param firstEnd last day of the first period
 */
internal class PeriodSchedule(private val firstStart: LocalDate, private val firstEnd: LocalDate) {
    /**
     * Returns the period at a zero-based position in the chain.
     *
     * @param index position of the period, `0` for the first one
     * @return the period's inclusive date range
     * @throws java.time.DateTimeException if the period lies outside the supported date range
     * @throws ArithmeticException if the offset of the period overflows
     */
    fun period(index: Int): Period {
        if (index == 0) {
            return Period(firstStart, firstEnd)
        }
        val nextStart = firstEnd.plusDays(1)
        val months = monthIndex(nextStart) - monthIndex(firstStart)
        if (months >= 1 && firstStart.plusMonths(months) == nextStart) {
            return Period(
                firstStart.plusMonths(Math.multiplyExact(months, index.toLong())),
                firstStart.plusMonths(Math.multiplyExact(months, index + 1L)).minusDays(1)
            )
        }
        val length = ChronoUnit.DAYS.between(firstStart, nextStart)
        val start = firstStart.plusDays(Math.multiplyExact(length, index.toLong()))
        return Period(start, start.plusDays(length - 1))
    }
}

/**
 * Resolves event recurrences inside a forecast period.
 *
 * Occurrences are counted arithmetically, so the cost does not depend on how far in the past an
 * event starts or how long the period is.
 */
internal class CalendarEngine {
    /**
     * Builds the calendar snapshot of one period.
     *
     * @param periodStart first day of the period
     * @param periodEnd last day of the period, not before [periodStart]
     * @param currentDate date within `[periodStart, periodEnd]` that separates past from future
     * @param incomeEvents scheduled income
     * @param expenseEvents scheduled mandatory and optional expenses
     * @return day counts and amounts split at [currentDate]
     */
    fun buildSnapshot(
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currentDate: LocalDate,
        incomeEvents: List<IncomeEvent>,
        expenseEvents: List<ExpenseEvent>
    ): PeriodSnapshot {
        val past = Period(periodStart, currentDate)
        val future = if (currentDate < periodEnd) Period(currentDate.plusDays(1), periodEnd) else null
        val mandatory = expenseEvents.filter { it.isMandatory }
        val optional = expenseEvents.filterNot { it.isMandatory }
        return PeriodSnapshot(
            periodStart = periodStart,
            periodEnd = periodEnd,
            currentDate = currentDate,
            daysInPeriod = Math.toIntExact(ChronoUnit.DAYS.between(periodStart, periodEnd) + 1),
            daysElapsed = Math.toIntExact(ChronoUnit.DAYS.between(periodStart, currentDate)),
            daysRemaining = Math.toIntExact(ChronoUnit.DAYS.between(currentDate, periodEnd) + 1),
            receivedIncome = incomeEvents.sumOf { amountIn(past, it.amount, it.recurrence, it.startDate, it.endDate) },
            pendingIncome = incomeEvents.sumOf { amountIn(future, it.amount, it.recurrence, it.startDate, it.endDate) },
            paidMandatory = mandatory.sumOf { amountIn(past, it.amount, it.recurrence, it.startDate, it.endDate) },
            upcomingMandatory = mandatory.sumOf { amountIn(future, it.amount, it.recurrence, it.startDate, it.endDate) },
            paidOptional = optional.sumOf { amountIn(past, it.amount, it.recurrence, it.startDate, it.endDate) },
            upcomingOptional = optional.sumOf { amountIn(future, it.amount, it.recurrence, it.startDate, it.endDate) }
        )
    }

    /**
     * Counts the occurrences of an event inside a date range.
     *
     * @param range inclusive date range to count in
     * @param recurrence repetition pattern of the event
     * @param startDate event start, which anchors the recurrence
     * @param endDate event end, inclusive; `null` means unbounded
     * @return number of occurrences dated within both [range] and `[startDate, endDate]`
     */
    fun countOccurrences(range: Period, recurrence: EventRecurrence, startDate: LocalDate, endDate: LocalDate?): Long {
        val lower = maxOf(range.start, startDate)
        val upper = if (endDate == null || endDate > range.end) range.end else endDate
        if (lower > upper) {
            return 0
        }
        return when (recurrence) {
            EventRecurrence.OneTime -> if (lower == startDate) 1 else 0
            is EventRecurrence.EveryNDays -> countEveryNDays(recurrence.n.toLong(), startDate, lower, upper)
            is EventRecurrence.EveryNMonths -> countEveryNMonths(recurrence, startDate, lower, upper)
        }
    }

    private fun amountIn(
        range: Period?,
        amount: BigDecimal,
        recurrence: EventRecurrence,
        startDate: LocalDate,
        endDate: LocalDate?
    ): BigDecimal {
        if (range == null) {
            return BigDecimal.ZERO
        }
        return amount.multiply(BigDecimal.valueOf(countOccurrences(range, recurrence, startDate, endDate)))
    }

    private fun countEveryNDays(n: Long, startDate: LocalDate, lower: LocalDate, upper: LocalDate): Long {
        val firstStep = ceilDiv(ChronoUnit.DAYS.between(startDate, lower), n)
        val lastStep = Math.floorDiv(ChronoUnit.DAYS.between(startDate, upper), n)
        return maxOf(0, lastStep - firstStep + 1)
    }

    private fun countEveryNMonths(
        recurrence: EventRecurrence.EveryNMonths,
        startDate: LocalDate,
        lower: LocalDate,
        upper: LocalDate
    ): Long {
        val n = recurrence.n.toLong()
        val startMonth = monthIndex(startDate)
        val anchor = if (occurrenceIn(startMonth, recurrence.dayOfMonth) >= startDate) startMonth else startMonth + 1
        val lowerMonth = monthIndex(lower)
        var firstStep = maxOf(0, ceilDiv(lowerMonth - anchor, n))
        if (anchor + firstStep * n == lowerMonth && occurrenceIn(lowerMonth, recurrence.dayOfMonth) < lower) {
            firstStep++
        }
        val upperMonth = monthIndex(upper)
        if (upperMonth < anchor) {
            return 0
        }
        var lastStep = Math.floorDiv(upperMonth - anchor, n)
        if (anchor + lastStep * n == upperMonth && occurrenceIn(upperMonth, recurrence.dayOfMonth) > upper) {
            lastStep--
        }
        return maxOf(0, lastStep - firstStep + 1)
    }

    private fun occurrenceIn(monthIndex: Long, dayOfMonth: Int): LocalDate {
        val month = YearMonth.of(Math.toIntExact(Math.floorDiv(monthIndex, 12L)), Math.floorMod(monthIndex, 12L).toInt() + 1)
        return month.atDay(minOf(dayOfMonth, month.lengthOfMonth()))
    }

    private fun ceilDiv(dividend: Long, divisor: Long): Long = -Math.floorDiv(-dividend, divisor)
}

private fun monthIndex(date: LocalDate): Long = date.year * 12L + date.monthValue - 1
