/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTimeoutPreemptively
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Duration
import java.time.LocalDate

/**
 * Validation rules and exact exception messages of the public input model.
 */
class InputValidatorTest {
    private val august1: LocalDate = LocalDate.of(2026, 8, 1)
    private val august31: LocalDate = LocalDate.of(2026, 8, 31)

    private fun assertRejected(message: String, block: () -> Unit) {
        val error = assertThrows<IllegalArgumentException> { block() }
        assertEquals(message, error.message)
    }

    private fun forecast(
        periodStart: LocalDate = august1,
        periodEnd: LocalDate = august31,
        currentDate: LocalDate = august1,
        alreadySpent: String = "0",
        forecastPeriods: Int = 1
    ): ForecastInput = ForecastInput(
        incomeEvents = emptyList(),
        expenseEvents = emptyList(),
        periodStart = periodStart,
        periodEnd = periodEnd,
        currentDate = currentDate,
        alreadySpent = dec(alreadySpent),
        forecastPeriods = forecastPeriods,
        config = config(),
        cushionState = cushion("0", "0")
    )

    /**
     * A valid README configuration is accepted.
     */
    @Test
    fun `accepts a valid configuration`() {
        assertDoesNotThrow { config() }
    }

    /**
     * Levels equal in value but not in scale are reported as overlapping, not as unsorted.
     */
    @Test
    fun `reports overlapping levels regardless of decimal scale`() {
        assertRejected("Criticality level ranges overlap: A and B") {
            config(criticalityLevels = listOf(level("A", "0.3"), level("B", "0.30")))
        }
    }

    /**
     * Levels must be sorted by maxFillPct ascending.
     */
    @Test
    fun `rejects unsorted levels`() {
        assertRejected("Criticality levels must be non-empty and sorted by maxFillPct ascending.") {
            config(criticalityLevels = listOf(level("A", "0.7"), level("B", "0.3")))
        }
    }

    /**
     * At least one level is required.
     */
    @Test
    fun `rejects empty levels`() {
        assertRejected("Criticality levels must be non-empty and sorted by maxFillPct ascending.") {
            config(criticalityLevels = emptyList())
        }
    }

    /**
     * A level that can never match is rejected.
     */
    @Test
    fun `rejects zero maxFillPct`() {
        assertRejected("Percentage must be in (0,1]: maxFillPct") { level("A", "0") }
    }

    /**
     * Ratios above one are rejected with the field name.
     */
    @Test
    fun `rejects ratios above one`() {
        assertRejected("Percentage must be in [0,1]: topupValue") { level("A", "0.5", topupValue = "1.01") }
        assertRejected("Percentage must be in [0,1]: piggyBankAdmissibilityPct") {
            config(piggyBankAdmissibilityPct = "1.5")
        }
    }

    /**
     * The piggy bank target is a ratio only in PERCENT_OF_REMAINDER mode.
     */
    @Test
    fun `validates the piggy bank target by mode`() {
        assertRejected("Percentage must be in [0,1]: piggyBankTarget") {
            config(piggyBankMode = PiggyBankMode.PERCENT_OF_REMAINDER, piggyBankTarget = "5000")
        }
        assertDoesNotThrow { config(piggyBankMode = PiggyBankMode.FIXED_AMOUNT, piggyBankTarget = "5000") }
        assertRejected("Amount must be non-negative: piggyBankTarget") { config(piggyBankTarget = "-1") }
    }

    /**
     * Scales must stay within `0..MAX_DIGITS`.
     */
    @Test
    fun `rejects scales outside the supported range`() {
        assertRejected("Scale must be >= 0: moneyScale") { config(moneyScale = -1) }
        assertRejected("Scale must be <= 1000: percentageScale") { config(percentageScale = Int.MAX_VALUE) }
    }

    /**
     * Pathological exponents are rejected quickly instead of exhausting memory during quantization.
     */
    @Test
    fun `rejects decimals with pathological exponents quickly`() {
        assertTimeoutPreemptively(Duration.ofSeconds(5)) {
            assertRejected("Number exceeds supported precision: income") {
                WhatIfInput(BigDecimal("1E+999999999"), dec("0"), dec("0"), cushion("0", "0"), config())
            }
            assertRejected("Number exceeds supported precision: topupValue") {
                level("A", "0.5", topupValue = "1E-999999999")
            }
        }
    }

    /**
     * Negative amounts are rejected with the field name.
     */
    @Test
    fun `rejects negative amounts`() {
        assertRejected("Amount must be non-negative: optional") {
            WhatIfInput(dec("1"), dec("0"), dec("-1"), cushion("0", "0"), config())
        }
        assertRejected("Amount must be non-negative: cushionCurrent") { cushion("-0.01", "0") }
        assertRejected("Amount must be non-negative: amount") {
            IncomeEvent("salary", dec("-1"), EventRecurrence.OneTime, august1, null)
        }
        assertRejected("Amount must be non-negative: alreadySpent") { forecast(alreadySpent = "-5") }
    }

    /**
     * Recurrence parameters must be positive and the day of month must exist in some month.
     */
    @Test
    fun `validates recurrence parameters`() {
        assertRejected("Recurrence parameter must be positive: n") { EventRecurrence.EveryNDays(0) }
        assertRejected("Recurrence parameter must be positive: n") { EventRecurrence.EveryNMonths(0, 1) }
        assertRejected("Recurrence parameter must be positive: dayOfMonth") { EventRecurrence.EveryNMonths(1, 0) }
        assertRejected("Recurrence parameter must be <= 31: dayOfMonth") { EventRecurrence.EveryNMonths(1, 32) }
    }

    /**
     * An event window must not end before it starts.
     */
    @Test
    fun `rejects an event ending before it starts`() {
        assertRejected("startDate must not be after endDate") {
            ExpenseEvent("rent", dec("1"), true, EventRecurrence.EveryNMonths(1, 5), august31, august1)
        }
    }

    /**
     * Period dates and the horizon are validated.
     */
    @Test
    fun `validates forecast dates and horizon`() {
        assertRejected("periodStart must not be after periodEnd") {
            forecast(periodStart = august31, periodEnd = august1, currentDate = august1)
        }
        assertRejected("currentDate must be within [periodStart, periodEnd]") {
            forecast(currentDate = LocalDate.of(2026, 9, 1))
        }
        assertRejected("forecastPeriods must be >= 1") { forecast(forecastPeriods = 0) }
        assertRejected("Period length must not exceed 2147483647 days") {
            forecast(periodStart = LocalDate.MIN, periodEnd = LocalDate.MAX, currentDate = LocalDate.MIN)
        }
    }

    /**
     * A horizon that runs past the last supported date is rejected instead of failing mid-calculation.
     */
    @Test
    fun `rejects a horizon beyond the supported date range`() {
        val lastMonthStart = LocalDate.MAX.withDayOfMonth(1)
        assertDoesNotThrow { forecast(periodStart = lastMonthStart, periodEnd = LocalDate.MAX, currentDate = lastMonthStart) }
        assertRejected("Forecast horizon exceeds the supported date range") {
            forecast(periodStart = lastMonthStart, periodEnd = LocalDate.MAX, currentDate = lastMonthStart, forecastPeriods = 2)
        }
        assertRejected("Forecast horizon exceeds the supported date range") {
            forecast(
                periodStart = LocalDate.of(2026, 1, 1),
                periodEnd = LocalDate.of(2026, 12, 31),
                currentDate = LocalDate.of(2026, 1, 1),
                forecastPeriods = Int.MAX_VALUE
            )
        }
    }
}
