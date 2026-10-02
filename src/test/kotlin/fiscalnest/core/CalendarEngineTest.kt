/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDate

/**
 * Recurrence resolution, day counting and period chaining of [CalendarEngine] and [PeriodSchedule].
 */
class CalendarEngineTest {
    private val engine = CalendarEngine()
    private val august = Period(date(2026, 8, 1), date(2026, 8, 31))

    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    private fun count(range: Period, recurrence: EventRecurrence, startDate: LocalDate, endDate: LocalDate? = null): Long =
        engine.countOccurrences(range, recurrence, startDate, endDate)

    /**
     * A one-time event counts only when its start date is inside the range.
     */
    @Test
    fun `counts one-time events inside the range only`() {
        assertEquals(1, count(august, EventRecurrence.OneTime, date(2026, 8, 1)))
        assertEquals(1, count(august, EventRecurrence.OneTime, date(2026, 8, 31)))
        assertEquals(0, count(august, EventRecurrence.OneTime, date(2026, 7, 31)))
        assertEquals(0, count(august, EventRecurrence.OneTime, date(2026, 9, 1)))
    }

    /**
     * Every-N-days events step from their start date.
     */
    @Test
    fun `counts every-n-days events from the anchor`() {
        assertEquals(2, count(august, EventRecurrence.EveryNDays(14), date(2026, 1, 1)))
        assertEquals(31, count(august, EventRecurrence.EveryNDays(1), date(2026, 8, 1)))
        assertEquals(1, count(august, EventRecurrence.EveryNDays(30), date(2026, 8, 2)))
    }

    /**
     * Counting does not iterate from the anchor, so events anchored far in the past are cheap.
     */
    @Test
    fun `counts events anchored far in the past in constant time`() {
        assertTimeoutPreemptively(Duration.ofSeconds(2)) {
            assertEquals(31, count(august, EventRecurrence.EveryNDays(1), LocalDate.MIN))
            assertEquals(1, count(august, EventRecurrence.EveryNMonths(1, 15), LocalDate.MIN))
        }
    }

    /**
     * A day of month beyond the month length falls on the last day, including leap years.
     */
    @Test
    fun `coerces the day of month to the end of short months`() {
        val monthly31 = EventRecurrence.EveryNMonths(1, 31)
        val anchor = date(2026, 1, 31)
        assertEquals(1, count(Period(date(2028, 2, 29), date(2028, 2, 29)), monthly31, anchor))
        assertEquals(0, count(Period(date(2028, 2, 1), date(2028, 2, 28)), monthly31, anchor))
        assertEquals(1, count(Period(date(2027, 2, 28), date(2027, 2, 28)), monthly31, anchor))
        assertEquals(1, count(Period(date(2026, 4, 30), date(2026, 4, 30)), monthly31, anchor))
    }

    /**
     * The first monthly occurrence is the first matching day on or after the start date.
     */
    @Test
    fun `starts monthly events on the first matching day after the start date`() {
        val quarterlyOnThe5th = EventRecurrence.EveryNMonths(3, 5)
        val start = date(2026, 1, 20)
        assertEquals(0, count(Period(date(2026, 1, 1), date(2026, 1, 31)), quarterlyOnThe5th, start))
        assertEquals(1, count(Period(date(2026, 2, 5), date(2026, 2, 5)), quarterlyOnThe5th, start))
        assertEquals(0, count(Period(date(2026, 4, 1), date(2026, 4, 30)), quarterlyOnThe5th, start))
        assertEquals(1, count(Period(date(2026, 5, 1), date(2026, 5, 31)), quarterlyOnThe5th, start))
        assertEquals(4, count(Period(date(2026, 1, 1), date(2026, 12, 31)), quarterlyOnThe5th, start))
    }

    /**
     * Occurrences after the event end date are ignored.
     */
    @Test
    fun `respects the event end date`() {
        val monthly = EventRecurrence.EveryNMonths(1, 5)
        assertEquals(0, count(august, monthly, date(2026, 1, 5), date(2026, 7, 31)))
        assertEquals(1, count(august, monthly, date(2026, 1, 5), date(2026, 8, 5)))
        assertEquals(0, count(august, monthly, date(2026, 1, 5), date(2026, 8, 4)))
        assertEquals(2, count(august, EventRecurrence.EveryNDays(7), date(2026, 8, 3), date(2026, 8, 16)))
    }

    /**
     * Amounts are split at the current date: on or before it is received or paid, after it is pending or upcoming.
     */
    @Test
    fun `splits amounts at the current date`() {
        val snapshot = engine.buildSnapshot(
            periodStart = august.start,
            periodEnd = august.end,
            currentDate = date(2026, 8, 7),
            incomeEvents = listOf(
                IncomeEvent("advance", dec("20000"), EventRecurrence.OneTime, date(2026, 8, 1), null),
                IncomeEvent("salary", dec("30000"), EventRecurrence.EveryNMonths(1, 15), date(2026, 1, 15), null)
            ),
            expenseEvents = listOf(
                ExpenseEvent("rent", dec("20000"), true, EventRecurrence.EveryNMonths(1, 5), date(2026, 1, 5), null),
                ExpenseEvent("loan", dec("5000"), true, EventRecurrence.EveryNMonths(1, 20), date(2026, 1, 20), null),
                ExpenseEvent("gym", dec("1000"), false, EventRecurrence.OneTime, date(2026, 8, 7), null),
                ExpenseEvent("cinema", dec("700"), false, EventRecurrence.OneTime, date(2026, 8, 25), null)
            )
        )
        assertEquals(31, snapshot.daysInPeriod)
        assertEquals(6, snapshot.daysElapsed)
        assertEquals(25, snapshot.daysRemaining)
        assertDecimal("20000", snapshot.receivedIncome)
        assertDecimal("30000", snapshot.pendingIncome)
        assertDecimal("20000", snapshot.paidMandatory)
        assertDecimal("5000", snapshot.upcomingMandatory)
        assertDecimal("1000", snapshot.paidOptional)
        assertDecimal("700", snapshot.upcomingOptional)
    }

    /**
     * On the last day of the period one day remains and nothing is upcoming.
     */
    @Test
    fun `counts one remaining day on the last day`() {
        val snapshot = engine.buildSnapshot(
            periodStart = august.start,
            periodEnd = august.end,
            currentDate = august.end,
            incomeEvents = listOf(IncomeEvent("bonus", dec("100"), EventRecurrence.OneTime, august.end, null)),
            expenseEvents = emptyList()
        )
        assertEquals(1, snapshot.daysRemaining)
        assertEquals(30, snapshot.daysElapsed)
        assertDecimal("100", snapshot.receivedIncome)
        assertDecimal("0", snapshot.pendingIncome)
    }

    /**
     * Month-aligned periods stay aligned to calendar months.
     */
    @Test
    fun `chains calendar months`() {
        val schedule = PeriodSchedule(august.start, august.end)
        assertEquals(august, schedule.period(0))
        assertEquals(Period(date(2026, 9, 1), date(2026, 9, 30)), schedule.period(1))
        assertEquals(Period(date(2026, 10, 1), date(2026, 10, 31)), schedule.period(2))
        assertEquals(
            Period(date(2028, 2, 1), date(2028, 2, 29)),
            PeriodSchedule(date(2028, 1, 1), date(2028, 1, 31)).period(1)
        )
    }

    /**
     * Pay cycles and multi-month periods keep their day of month.
     */
    @Test
    fun `chains pay cycles and quarters by months`() {
        val payCycle = PeriodSchedule(date(2026, 8, 5), date(2026, 9, 4))
        assertEquals(Period(date(2026, 9, 5), date(2026, 10, 4)), payCycle.period(1))
        assertEquals(Period(date(2027, 2, 5), date(2027, 3, 4)), payCycle.period(6))
        val quarter = PeriodSchedule(date(2026, 1, 1), date(2026, 3, 31))
        assertEquals(Period(date(2026, 4, 1), date(2026, 6, 30)), quarter.period(1))
        val endOfMonth = PeriodSchedule(date(2026, 1, 31), date(2026, 2, 27))
        assertEquals(Period(date(2026, 2, 28), date(2026, 3, 30)), endOfMonth.period(1))
        assertEquals(Period(date(2026, 3, 31), date(2026, 4, 29)), endOfMonth.period(2))
    }

    /**
     * Periods that are not whole months keep their length in days.
     */
    @Test
    fun `chains fixed-length periods by days`() {
        val fortnight = PeriodSchedule(date(2026, 8, 1), date(2026, 8, 14))
        assertEquals(Period(date(2026, 8, 15), date(2026, 8, 28)), fortnight.period(1))
        assertEquals(Period(date(2026, 8, 29), date(2026, 9, 11)), fortnight.period(2))
    }
}
