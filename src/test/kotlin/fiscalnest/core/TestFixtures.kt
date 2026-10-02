/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import org.junit.jupiter.api.Assertions.assertEquals
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Parses a decimal literal.
 */
internal fun dec(value: String): BigDecimal = BigDecimal(value)

/**
 * Asserts numeric equality with [BigDecimal.compareTo], ignoring scale.
 */
internal fun assertDecimal(expected: String, actual: BigDecimal) {
    assertEquals(0, BigDecimal(expected).compareTo(actual), "expected $expected but was $actual")
}

/**
 * Builds a criticality level with test-friendly defaults.
 */
internal fun level(
    name: String,
    maxFillPct: String,
    topupMode: TopupMode = TopupMode.PERCENT_OF_TARGET,
    topupValue: String = "0.20",
    admissibilityPct: String = "0.80"
): CriticalityLevel = CriticalityLevel(name, dec(maxFillPct), topupMode, dec(topupValue), dec(admissibilityPct))

/**
 * The two criticality levels used in README examples.
 */
internal val readmeLevels: List<CriticalityLevel> = listOf(
    level("Critical", "0.30", TopupMode.PERCENT_OF_TARGET, "0.20", "0.80"),
    level("Warning", "0.70", TopupMode.PERCENT_OF_REMAINDER, "0.10", "0.50")
)

/**
 * Builds an engine configuration with README defaults.
 */
internal fun config(
    criticalityLevels: List<CriticalityLevel> = readmeLevels,
    piggyBankMode: PiggyBankMode = PiggyBankMode.FIXED_AMOUNT,
    piggyBankTarget: String = "5000",
    piggyBankAdmissibilityPct: String = "0.80",
    moneyScale: Int = 2,
    percentageScale: Int = 4,
    roundingMode: RoundingMode = RoundingMode.HALF_UP,
    cashReserves: Set<CashReserve> = setOf(CashReserve.CUSHION_TOPUP)
): EngineConfig = EngineConfig(
    roundingMode = roundingMode,
    moneyScale = moneyScale,
    percentageScale = percentageScale,
    criticalityLevels = criticalityLevels,
    piggyBankMode = piggyBankMode,
    piggyBankTarget = dec(piggyBankTarget),
    piggyBankAdmissibilityPct = dec(piggyBankAdmissibilityPct),
    cashReserves = cashReserves
)

/**
 * Builds a cushion state from decimal literals.
 */
internal fun cushion(current: String, target: String): CushionState = CushionState(dec(current), dec(target))
