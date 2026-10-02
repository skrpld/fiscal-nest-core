/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate

/**
 * Public entry points of [BudgetCalculator]: what-if delegation, forecast chaining, plan and cash views.
 */
class BudgetCalculatorTest {
    private fun date(year: Int, month: Int, day: Int): LocalDate = LocalDate.of(year, month, day)

    private val salaryOnThe1st = IncomeEvent("salary", dec("50000"), EventRecurrence.EveryNMonths(1, 1), date(2026, 1, 1), null)
    private val rentOnThe5th = ExpenseEvent("rent", dec("20000"), true, EventRecurrence.EveryNMonths(1, 5), date(2026, 1, 5), null)

    private fun forecast(
        incomeEvents: List<IncomeEvent> = listOf(salaryOnThe1st),
        expenseEvents: List<ExpenseEvent> = listOf(rentOnThe5th),
        currentDate: LocalDate = date(2026, 8, 7),
        alreadySpent: String = "3500",
        forecastPeriods: Int = 3
    ): List<ForecastResult> = BudgetCalculator.calculateForecast(
        ForecastInput(
            incomeEvents = incomeEvents,
            expenseEvents = expenseEvents,
            periodStart = date(2026, 8, 1),
            periodEnd = date(2026, 8, 31),
            currentDate = currentDate,
            alreadySpent = dec(alreadySpent),
            forecastPeriods = forecastPeriods,
            config = config(),
            cushionState = cushion("5000", "20000")
        )
    )

    /**
     * What-if mode returns the distribution of the aggregate amounts.
     */
    @Test
    fun `calculates the README what-if example`() {
        val result = BudgetCalculator.calculateWhatIf(
            WhatIfInput(dec("50000"), dec("20000"), dec("10000"), cushion("5000", "20000"), config())
        )
        assertDecimal("4000", result.cushionTopup)
        assertDecimal("5000", result.piggyBankActual)
        assertDecimal("11000", result.freeRemainder)
        assertDecimal("9000", result.cushionCurrent)
    }

    /**
     * The first README forecast period: paid rent leaves the cash view, the plan counts the whole period.
     */
    @Test
    fun `calculates the first forecast period`() {
        val first = forecast().first()
        assertEquals(date(2026, 8, 7), first.currentDate)
        assertEquals(31, first.daysInPeriod)
        assertEquals(6, first.daysElapsed)
        assertEquals(25, first.daysRemaining)
        assertDecimal("0", first.openingBalance)

        assertDecimal("50000", first.distribution.totalIncome)
        assertDecimal("20000", first.distribution.totalMandatory)
        assertDecimal("4000", first.distribution.cushionTopup)
        assertDecimal("21000", first.distribution.freeRemainder)

        assertDecimal("50000", first.cashFlow.receivedIncome)
        assertDecimal("20000", first.cashFlow.paidMandatory)
        assertDecimal("0", first.cashFlow.upcomingMandatory)
        assertDecimal("3500", first.cashFlow.alreadySpent)
        assertDecimal("26500", first.cashFlow.liquidOnHand)
        assertDecimal("26500", first.cashFlow.available)
        assertDecimal("17500", first.closingBalance)

        assertDecimal("677.42", first.dailyMetrics.dailyPlan)
        assertDecimal("700", first.dailyMetrics.dailyActual)
        assertDecimal("900", first.dailyMetrics.dailyCashflow)
        assertDecimal("500", first.dailyMetrics.burnRate)
    }

    /**
     * Later periods follow calendar months and carry the closing balance and the cushion forward.
     */
    @Test
    fun `chains periods with carry-forward`() {
        val (first, second, third) = forecast()

        assertEquals(date(2026, 9, 1), second.periodStart)
        assertEquals(date(2026, 9, 30), second.periodEnd)
        assertEquals(second.periodStart, second.currentDate)
        assertEquals(0, second.daysElapsed)
        assertEquals(30, second.daysRemaining)
        assertDecimal(first.closingBalance.toPlainString(), second.openingBalance)
        assertDecimal("0", second.cashFlow.alreadySpent)
        assertDecimal("67500", second.distribution.totalIncome)
        assertDecimal("0.45", second.distribution.cushionFillPct)
        assertEquals("Warning", second.distribution.activeCriticalityLevel)
        assertDecimal("4750", second.distribution.cushionTopup)
        assertDecimal("13750", second.distribution.cushionCurrent)
        assertDecimal("37750", second.closingBalance)
        assertDecimal("20000", second.cashFlow.mustReserve)
        assertDecimal("47500", second.cashFlow.available)
        assertDecimal("1425", second.dailyMetrics.dailyCashflow)
        assertDecimal("0", second.dailyMetrics.burnRate)

        assertEquals(date(2026, 10, 1), third.periodStart)
        assertEquals(date(2026, 10, 31), third.periodEnd)
        assertDecimal("37750", third.openingBalance)
        assertDecimal("6250", third.distribution.cushionTopup)
        assertDecimal("20000", third.distribution.cushionCurrent)
        assertDecimal("56500", third.closingBalance)
    }

    /**
     * Salary due later in the period is part of the plan, so rent due earlier is no expense crisis;
     * the cash view still shows the gap until payday.
     */
    @Test
    fun `does not report a crisis when income arrives after expenses`() {
        val salaryOnThe10th = IncomeEvent("salary", dec("50000"), EventRecurrence.EveryNMonths(1, 10), date(2026, 1, 10), null)
        val results = forecast(incomeEvents = listOf(salaryOnThe10th), currentDate = date(2026, 8, 3), alreadySpent = "0", forecastPeriods = 2)
        results.forEach { assertFalse(it.distribution.expenseCrisis) }

        val first = results.first()
        assertDecimal("50000", first.distribution.totalIncome)
        assertDecimal("0", first.cashFlow.receivedIncome)
        assertDecimal("50000", first.cashFlow.pendingIncome)
        assertDecimal("20000", first.cashFlow.mustReserve)
        assertDecimal("-20000", first.cashFlow.available)
        assertTrue(first.dailyMetrics.dailyCashflow.signum() < 0)
    }

    /**
     * A deficit is carried into the next period as a negative opening balance.
     */
    @Test
    fun `carries a deficit forward`() {
        val smallSalary = IncomeEvent("salary", dec("10000"), EventRecurrence.EveryNMonths(1, 1), date(2026, 1, 1), null)
        val bigRent = ExpenseEvent("rent", dec("15000"), true, EventRecurrence.EveryNMonths(1, 5), date(2026, 1, 5), null)
        val (first, second) = forecast(listOf(smallSalary), listOf(bigRent), alreadySpent = "0", forecastPeriods = 2)
        assertTrue(first.distribution.expenseCrisis)
        assertDecimal("-5000", first.closingBalance)
        assertDecimal("-5000", second.openingBalance)
        assertDecimal("5000", second.distribution.totalIncome)
        assertDecimal("10000", second.distribution.expenseDeficit)
    }

    /**
     * The facade revalidates inputs whose constructor was bypassed, as reflection-based deserializers do.
     */
    @Test
    fun `revalidates inputs created without their constructor`() {
        val input = allocateWithoutConstructor(WhatIfInput::class.java)
        mapOf(
            "income" to dec("-1"),
            "mandatory" to dec("0"),
            "optional" to dec("0"),
            "cushionState" to cushion("0", "0"),
            "config" to config()
        ).forEach { (name, value) ->
            WhatIfInput::class.java.getDeclaredField(name).apply { isAccessible = true }.set(input, value)
        }
        val error = assertThrows<IllegalArgumentException> { BudgetCalculator.calculateWhatIf(input) }
        assertEquals("Amount must be non-negative: income", error.message)
    }

    private fun <T> allocateWithoutConstructor(type: Class<T>): T {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val unsafe = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }.get(null)
        return type.cast(unsafeClass.getMethod("allocateInstance", Class::class.java).invoke(unsafe, type))
    }
}
