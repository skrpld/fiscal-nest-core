/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Remainder hierarchy, criticality levels, piggy bank and crisis rules of [DistributionEngine].
 */
class DistributionEngineTest {
    private val engine = DistributionEngine()

    private fun distribute(
        income: String,
        mandatory: String = "0",
        optional: String = "0",
        cushionState: CushionState = cushion("20000", "20000"),
        config: EngineConfig = config()
    ): DistributionResult = engine.distribute(dec(income), dec(mandatory), dec(optional), cushionState, config)

    /**
     * The README what-if example distributes 20000 of net remainder as 4000 / 5000 / 11000.
     */
    @Test
    fun `distributes the README example`() {
        val result = distribute("50000", "20000", "10000", cushion("5000", "20000"))
        assertFalse(result.expenseCrisis)
        assertTrue(result.cushionCrisis)
        assertFalse(result.cushionOverfilled)
        assertFalse(result.piggyBankCappedByAdmissibility)
        assertEquals("Critical", result.activeCriticalityLevel)
        assertDecimal("30000", result.rawRemainder)
        assertDecimal("20000", result.netRemainder)
        assertDecimal("0.25", result.cushionFillPct)
        assertDecimal("15000", result.cushionNeed)
        assertDecimal("4000", result.cushionTopup)
        assertDecimal("9000", result.cushionCurrent)
        assertDecimal("5000", result.piggyBankActual)
        assertDecimal("11000", result.freeRemainder)
        assertDecimal("0", result.expenseDeficit)
    }

    /**
     * The second level applies its own mode: 10% of the remainder.
     */
    @Test
    fun `applies the level matching the fill ratio`() {
        val result = distribute("50000", "20000", "10000", cushion("10000", "20000"))
        assertEquals("Warning", result.activeCriticalityLevel)
        assertDecimal("2000", result.cushionTopup)
        assertDecimal("12000", result.cushionCurrent)
    }

    /**
     * The top-up never exceeds what the cushion still needs.
     */
    @Test
    fun `caps the top-up at the cushion need`() {
        val levels = listOf(level("Critical", "0.30"), level("Almost", "1"))
        val result = distribute("50000", cushionState = cushion("19500", "20000"), config = config(criticalityLevels = levels))
        assertDecimal("500", result.cushionTopup)
        assertDecimal("20000", result.cushionCurrent)
    }

    /**
     * The top-up never exceeds the level's admissible share of the net remainder.
     */
    @Test
    fun `caps the top-up at the admissible share`() {
        val levels = listOf(level("Critical", "0.30", admissibilityPct = "0.10"))
        val result = distribute("20000", cushionState = cushion("0", "20000"), config = config(criticalityLevels = levels))
        assertDecimal("2000", result.cushionTopup)
    }

    /**
     * A cushion above every level is not topped up.
     */
    @Test
    fun `does not top up a funded cushion`() {
        val result = distribute("50000", "20000", "10000", cushion("15000", "20000"))
        assertFalse(result.cushionCrisis)
        assertNull(result.activeCriticalityLevel)
        assertDecimal("0", result.cushionTopup)
        assertDecimal("15000", result.cushionCurrent)
    }

    /**
     * An overfilled cushion keeps its excess and receives nothing.
     */
    @Test
    fun `reports an overfilled cushion`() {
        val result = distribute("10000", cushionState = cushion("25000", "20000"))
        assertTrue(result.cushionOverfilled)
        assertFalse(result.cushionCrisis)
        assertDecimal("0", result.cushionTopup)
        assertDecimal("25000", result.cushionCurrent)
        assertDecimal("1.25", result.cushionFillPct)
    }

    /**
     * An expense crisis suspends top-up and piggy bank but still reports the cushion crisis.
     */
    @Test
    fun `reports an expense crisis together with a cushion crisis`() {
        val result = distribute("25000", "20000", "10000", cushion("5000", "20000"))
        assertTrue(result.expenseCrisis)
        assertTrue(result.cushionCrisis)
        assertFalse(result.piggyBankCappedByAdmissibility)
        assertDecimal("-5000", result.netRemainder)
        assertDecimal("5000", result.expenseDeficit)
        assertDecimal("0", result.cushionTopup)
        assertDecimal("5000", result.cushionCurrent)
        assertDecimal("0", result.piggyBankActual)
        assertDecimal("5000", result.piggyBankTarget)
        assertDecimal("-5000", result.freeRemainder)
    }

    /**
     * A deficit carried in as negative income is an expense crisis.
     */
    @Test
    fun `treats negative income as an expense crisis`() {
        val result = distribute("-100")
        assertTrue(result.expenseCrisis)
        assertDecimal("100", result.expenseDeficit)
    }

    /**
     * In percent mode the piggy bank takes its share of the post-cushion remainder.
     */
    @Test
    fun `allocates a percentage of the remainder to the piggy bank`() {
        val percentConfig = config(
            piggyBankMode = PiggyBankMode.PERCENT_OF_REMAINDER,
            piggyBankTarget = "0.20",
            piggyBankAdmissibilityPct = "1"
        )
        val result = distribute("10000", config = percentConfig)
        assertDecimal("2000", result.piggyBankTarget)
        assertDecimal("2000", result.piggyBankActual)
        assertDecimal("8000", result.freeRemainder)
        assertFalse(result.piggyBankCappedByAdmissibility)
    }

    /**
     * Fixed piggy bank amounts follow the admissibility examples of the architecture document.
     */
    @Test
    fun `caps a fixed piggy bank amount by admissibility`() {
        val full = distribute("3145", config = config(piggyBankAdmissibilityPct = "1"))
        assertDecimal("3145", full.piggyBankActual)
        assertTrue(full.piggyBankCappedByAdmissibility)

        val partial = distribute("3145", config = config(piggyBankAdmissibilityPct = "0.80"))
        assertDecimal("2516", partial.piggyBankActual)
        assertDecimal("629", partial.freeRemainder)
        assertTrue(partial.piggyBankCappedByAdmissibility)

        val enough = distribute("15000", config = config(piggyBankAdmissibilityPct = "1"))
        assertDecimal("5000", enough.piggyBankActual)
        assertFalse(enough.piggyBankCappedByAdmissibility)
    }

    /**
     * Zero amounts and a zero cushion target are valid and produce no crisis.
     */
    @Test
    fun `handles zero amounts and a zero cushion target`() {
        val result = distribute("0", cushionState = cushion("0", "0"))
        assertFalse(result.expenseCrisis)
        assertFalse(result.cushionCrisis)
        assertFalse(result.cushionOverfilled)
        assertDecimal("1", result.cushionFillPct)
        assertDecimal("0", result.cushionTopup)
        assertDecimal("0", result.piggyBankActual)
        assertDecimal("0", result.freeRemainder)
    }

    /**
     * Rounded allocations always add up to the net remainder, and every amount has the money scale.
     */
    @Test
    fun `keeps the remainder hierarchy exact after rounding`() {
        val oddConfig = config(
            criticalityLevels = listOf(level("Low", "0.5", TopupMode.PERCENT_OF_REMAINDER, "0.333", "0.777")),
            piggyBankMode = PiggyBankMode.PERCENT_OF_REMAINDER,
            piggyBankTarget = "0.123",
            piggyBankAdmissibilityPct = "0.5"
        )
        listOf("1000.01", "333.33", "0.07", "98765.43", "1.005").forEach { income ->
            val result = distribute(income, "0.335", "0.005", cushion("0", "1000000"), oddConfig)
            assertDecimal(
                result.netRemainder.toPlainString(),
                result.cushionTopup + result.piggyBankActual + result.freeRemainder
            )
            listOf(result.cushionTopup, result.piggyBankActual, result.freeRemainder, result.totalIncome).forEach {
                assertEquals(2, it.scale(), "scale of $it")
            }
        }
    }
}
