/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

/**
 * Defines when an event repeats. Every pattern is anchored at the owning event's `startDate` and
 * never produces a date outside the event's `[startDate, endDate]` window.
 *
 * @see IncomeEvent
 * @see ExpenseEvent
 */
sealed class EventRecurrence {
    /**
     * A single occurrence on the event's `startDate`.
     */
    data object OneTime : EventRecurrence()

    /**
     * Occurrences on `startDate`, `startDate + n days`, `startDate + 2n days`, and so on.
     *
     * @property n step in calendar days, `>= 1`
     * @throws IllegalArgumentException if [n] is not positive
     */
    data class EveryNDays(val n: Int) : EventRecurrence() {
        init {
            InputValidator.validateRecurrence(this)
        }
    }

    /**
     * Occurrences on [dayOfMonth] every [n] months. The first occurrence is the first such day on
     * or after the event's `startDate`. A [dayOfMonth] beyond the length of a month falls on that
     * month's last day, so `31` always means "end of month".
     *
     * @property n step in calendar months, `>= 1`
     * @property dayOfMonth day of month, `1..31`
     * @throws IllegalArgumentException if [n] is not positive or [dayOfMonth] is outside `1..31`
     */
    data class EveryNMonths(
        val n: Int,
        val dayOfMonth: Int
    ) : EventRecurrence() {
        init {
            InputValidator.validateRecurrence(this)
        }
    }
}
