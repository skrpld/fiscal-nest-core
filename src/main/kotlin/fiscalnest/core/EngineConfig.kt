package fiscalnest.core

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Configuration for the fiscal nest core engine.
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
        require(moneyScale >= 0) { "Scale must be >= 0: moneyScale" }
        require(percentageScale >= 0) { "Scale must be >= 0: percentageScale" }
        require(criticalityLevels.isNotEmpty()) {
            "Criticality levels must be non-empty and sorted by maxFillPct ascending."
        }
        criticalityLevels.zipWithNext().forEach { (first, second) ->
            require(first.maxFillPct < second.maxFillPct) {
                if (first.maxFillPct == second.maxFillPct) {
                    "Criticality level ranges overlap: ${first.name} and ${second.name}"
                } else {
                    "Criticality levels must be non-empty and sorted by maxFillPct ascending."
                }
            }
        }
        require(piggyBankTarget.signum() >= 0) {
            "Amount must be non-negative: piggyBankTarget"
        }
        if (piggyBankMode == PiggyBankMode.PERCENT_OF_REMAINDER) {
            require(piggyBankTarget.inPercentageRange()) {
                "Percentage must be in [0,1]: piggyBankTarget"
            }
        }
        require(piggyBankAdmissibilityPct.inPercentageRange()) {
            "Percentage must be in [0,1]: piggyBankAdmissibilityPct"
        }
    }

    private fun BigDecimal.inPercentageRange(): Boolean =
        compareTo(BigDecimal.ZERO) >= 0 && compareTo(BigDecimal.ONE) <= 0
}

/**
 * Defines the criticality threshold and top-up policy for a cushion level.
 */
data class CriticalityLevel(
    val name: String,
    val maxFillPct: BigDecimal,
    val topupMode: TopupMode,
    val topupValue: BigDecimal,
    val admissibilityPct: BigDecimal
) {
    init {
        require(maxFillPct > BigDecimal.ZERO && maxFillPct <= BigDecimal.ONE) {
            "Percentage must be in [0,1]: maxFillPct"
        }
        require(topupValue.inPercentageRange()) {
            "Percentage must be in [0,1]: topupValue"
        }
        require(admissibilityPct.inPercentageRange()) {
            "Percentage must be in [0,1]: admissibilityPct"
        }
    }

    private fun BigDecimal.inPercentageRange(): Boolean =
        compareTo(BigDecimal.ZERO) >= 0 && compareTo(BigDecimal.ONE) <= 0
}
