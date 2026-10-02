/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Describes an income event and its recurrence.
 */
data class IncomeEvent(
    val id: String,
    val amount: BigDecimal,
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?
) {
    init {
        require(amount.signum() >= 0) {
            "Amount must be non-negative: amount"
        }
    }
}
