/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

/**
 * Defines how an income or expense event recurs.
 */
sealed class EventRecurrence {
    /**
     * Represents an event occurring once, on the event start date.
     */
    data object OneTime : EventRecurrence()

    /**
     * Represents an event recurring every given number of days.
     */
    data class EveryNDays(val n: Int) : EventRecurrence() {
        init {
            require(n >= 1) { "Recurrence parameter must be positive: n" }
        }
    }

    /**
     * Represents an event recurring every given number of months on a day of month.
     */
    data class EveryNMonths(
        val n: Int,
        val dayOfMonth: Int
    ) : EventRecurrence() {
        init {
            require(n >= 1) { "Recurrence parameter must be positive: n" }
            require(dayOfMonth >= 1) {
                "Recurrence parameter must be positive: dayOfMonth"
            }
        }
    }
}
