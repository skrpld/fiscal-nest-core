package fiscalnest.core

import java.time.LocalDate

/**
 * Defines how an income or expense event recurs.
 */
sealed class EventRecurrence {
    /**
     * Represents an event occurring on one date.
     */
    data class OneTime(val date: LocalDate) : EventRecurrence()

    /**
     * Represents an event recurring every given number of days.
     */
    data class EveryNDays(val n: Int, val startDate: LocalDate) : EventRecurrence() {
        init {
            require(n >= 1) { "Recurrence parameter must be positive: n" }
        }
    }

    /**
     * Represents an event recurring every given number of months on a day of month.
     */
    data class EveryNMonths(
        val n: Int,
        val dayOfMonth: Int,
        val startDate: LocalDate
    ) : EventRecurrence() {
        init {
            require(n >= 1) { "Recurrence parameter must be positive: n" }
            require(dayOfMonth >= 1) {
                "Recurrence parameter must be positive: dayOfMonth"
            }
        }
    }
}
