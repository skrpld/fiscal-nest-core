/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * A scheduled income used in forecast mode.
 *
 * @property id opaque identifier the client uses to correlate the event with its own data
 * @property amount non-negative amount of each occurrence
 * @property recurrence repetition pattern anchored at [startDate]
 * @property startDate first possible occurrence, inclusive
 * @property endDate last possible occurrence, inclusive; `null` means unbounded
 * @throws IllegalArgumentException if [amount] is negative or [startDate] is after [endDate]
 * @see ForecastInput.incomeEvents
 */
data class IncomeEvent(
    val id: String,
    val amount: BigDecimal,
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?
) {
    init {
        InputValidator.validateIncomeEvent(this)
    }
}
