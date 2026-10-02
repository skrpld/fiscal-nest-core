/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A scheduled expense used in forecast mode. An occurrence dated on or before the current date is
 * treated as paid; a later one as upcoming.
 *
 * @property id opaque identifier the client uses to correlate the event with its own data
 * @property amount non-negative amount of each occurrence
 * @property isMandatory `true` for a mandatory expense, `false` for an optional one
 * @property recurrence repetition pattern anchored at [startDate]
 * @property startDate first possible occurrence, inclusive
 * @property endDate last possible occurrence, inclusive; `null` means unbounded
 * @throws IllegalArgumentException if [amount] is negative or [startDate] is after [endDate]
 * @see ForecastInput.expenseEvents
 */
data class ExpenseEvent(
    val id: String,
    val amount: BigDecimal,
    val isMandatory: Boolean,
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?
) {
    init {
        InputValidator.validateExpenseEvent(this)
    }
}
