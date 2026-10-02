/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal
import java.math.MathContext

/**
 * Applies the remainder hierarchy: income, mandatory and optional expenses, cushion top-up,
 * piggy bank, free remainder.
 *
 * Inputs are quantized to [EngineConfig.moneyScale] first and every allocation is quantized when it
 * is decided, so `netRemainder == cushionTopup + piggyBankActual + freeRemainder` holds exactly.
 * The engine has no notion of dates or liquidity.
 */
internal class DistributionEngine {
    /**
     * Distributes one period's money.
     *
     * @param income total income; negative when a deficit is carried in from a previous period
     * @param mandatory non-negative total of mandatory expenses
     * @param optional non-negative total of optional expenses
     * @param cushionState cushion balances before the distribution
     * @param config engine configuration
     * @return the allocation and the crisis flags
     */
    fun distribute(
        income: BigDecimal,
        mandatory: BigDecimal,
        optional: BigDecimal,
        cushionState: CushionState,
        config: EngineConfig
    ): DistributionResult {
        val zero = DecimalUtils.quantizeMoney(BigDecimal.ZERO, config)
        val totalIncome = DecimalUtils.quantizeMoney(income, config)
        val totalMandatory = DecimalUtils.quantizeMoney(mandatory, config)
        val totalOptional = DecimalUtils.quantizeMoney(optional, config)
        val cushionCurrent = DecimalUtils.quantizeMoney(cushionState.current, config)
        val cushionTarget = DecimalUtils.quantizeMoney(cushionState.target, config)

        val rawRemainder = totalIncome - totalMandatory
        val netRemainder = rawRemainder - totalOptional
        val expenseCrisis = netRemainder.signum() < 0

        val fillPct = if (cushionTarget.signum() == 0) {
            BigDecimal.ONE
        } else {
            cushionCurrent.divide(cushionTarget, MathContext.DECIMAL128)
        }
        val cushionNeed = (cushionTarget - cushionCurrent).max(zero)
        val activeLevel = config.criticalityLevels.firstOrNull { fillPct < it.maxFillPct }

        val cushionTopup = if (expenseCrisis || activeLevel == null) {
            zero
        } else {
            val desired = activeLevel.topupValue * when (activeLevel.topupMode) {
                TopupMode.PERCENT_OF_TARGET -> cushionTarget
                TopupMode.PERCENT_OF_REMAINDER -> netRemainder
            }
            val admissible = activeLevel.admissibilityPct * netRemainder
            DecimalUtils.quantizeMoney(minOf(desired, admissible, cushionNeed, netRemainder), config)
        }
        val postCushionRemainder = netRemainder - cushionTopup

        val piggyTarget = when (config.piggyBankMode) {
            PiggyBankMode.PERCENT_OF_REMAINDER -> config.piggyBankTarget * postCushionRemainder.max(zero)
            PiggyBankMode.FIXED_AMOUNT -> config.piggyBankTarget
        }
        val piggyBankTarget = DecimalUtils.quantizeMoney(piggyTarget, config)
        val piggyBankActual = if (expenseCrisis) {
            zero
        } else {
            val admissible = config.piggyBankAdmissibilityPct * postCushionRemainder
            DecimalUtils.quantizeMoney(minOf(piggyTarget, admissible, postCushionRemainder), config)
        }

        return DistributionResult(
            expenseCrisis = expenseCrisis,
            cushionCrisis = activeLevel != null,
            cushionOverfilled = cushionCurrent > cushionTarget,
            piggyBankCappedByAdmissibility = !expenseCrisis && piggyBankActual < piggyBankTarget,
            totalIncome = totalIncome,
            totalMandatory = totalMandatory,
            totalOptional = totalOptional,
            rawRemainder = rawRemainder,
            netRemainder = netRemainder,
            cushionTopup = cushionTopup,
            cushionCurrent = cushionCurrent + cushionTopup,
            cushionTarget = cushionTarget,
            cushionFillPct = DecimalUtils.quantizePct(fillPct, config),
            cushionNeed = cushionNeed,
            piggyBankActual = piggyBankActual,
            piggyBankTarget = piggyBankTarget,
            freeRemainder = postCushionRemainder - piggyBankActual,
            expenseDeficit = if (expenseCrisis) netRemainder.negate() else zero,
            activeCriticalityLevel = activeLevel?.name
        )
    }
}
