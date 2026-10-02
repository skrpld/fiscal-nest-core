/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.time.LocalDate

/**
 * Describes an expense event and its recurrence.
 */
data class ExpenseEvent(
    val id: String,
    val name: String,
    val amount: BigDecimal,
    val isMandatory: Boolean,
    val recurrence: EventRecurrence,
    val startDate: LocalDate,
    val endDate: LocalDate?,
    val category: String?
) {
    init {
        require(amount.signum() >= 0) {
            "Amount must be non-negative: amount"
        }
    }
}
