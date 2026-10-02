/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Configuration for every calculation. Passed explicitly on each call; the engine keeps no state.
 *
 * Every percentage-like value is a ratio on the `0.0..1.0` scale. Converting to a `0..100`
 * display value is a client concern.
 *
 * @property roundingMode rounding applied whenever a value is quantized
 * @property moneyScale decimal places of every monetary input and output, `0..1000`; `2` for cents
 * @property percentageScale decimal places of reported ratios such as
 * [DistributionResult.cushionFillPct], `0..1000`; `4` keeps two decimals once shown as `0..100`
 * @property criticalityLevels non-empty list of cushion levels sorted by
 * [CriticalityLevel.maxFillPct] ascending, without duplicates
 * @property piggyBankMode how [piggyBankTarget] is interpreted
 * @property piggyBankTarget ratio in `0.0..1.0` of the post-cushion remainder for
 * [PiggyBankMode.PERCENT_OF_REMAINDER], or a non-negative amount for [PiggyBankMode.FIXED_AMOUNT]
 * @property piggyBankAdmissibilityPct largest share, `0.0..1.0`, of the post-cushion remainder
 * that may go to the piggy bank
 * @throws IllegalArgumentException if any property violates the rules above
 * @see CriticalityLevel
 */
data class EngineConfig(
    val roundingMode: RoundingMode,
    val moneyScale: Int,
    val percentageScale: Int,
    val criticalityLevels: List<CriticalityLevel>,
    val piggyBankMode: PiggyBankMode,
    val piggyBankTarget: BigDecimal,
    val piggyBankAdmissibilityPct: BigDecimal
) {
    init {
        InputValidator.validateConfig(this)
    }
}

/**
 * A cushion criticality level: when the cushion fill ratio is below [maxFillPct], this level
 * decides how much of the net remainder is redirected to the cushion.
 *
 * @property name opaque label, reported back as [DistributionResult.activeCriticalityLevel]
 * @property maxFillPct exclusive upper bound of the fill ratio, in `(0.0..1.0]`
 * @property topupMode what [topupValue] is a share of
 * @property topupValue desired top-up as a ratio in `0.0..1.0`
 * @property admissibilityPct largest share, `0.0..1.0`, of the net remainder this level may take
 * @throws IllegalArgumentException if any ratio is outside its range
 * @see EngineConfig.criticalityLevels
 */
data class CriticalityLevel(
    val name: String,
    val maxFillPct: BigDecimal,
    val topupMode: TopupMode,
    val topupValue: BigDecimal,
    val admissibilityPct: BigDecimal
) {
    init {
        InputValidator.validateCriticalityLevel(this)
    }
}
