/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

/**
 * Defines what [CriticalityLevel.topupValue] is a share of.
 */
enum class TopupMode {
    /**
     * The desired top-up is `topupValue * cushionTarget`.
     */
    PERCENT_OF_TARGET,

    /**
     * The desired top-up is `topupValue * netRemainder`.
     */
    PERCENT_OF_REMAINDER
}

/**
 * Defines how [EngineConfig.piggyBankTarget] is interpreted.
 */
enum class PiggyBankMode {
    /**
     * The target is a ratio in `0.0..1.0` of the post-cushion remainder.
     */
    PERCENT_OF_REMAINDER,

    /**
     * The target is a fixed non-negative amount per calculation.
     */
    FIXED_AMOUNT
}

/**
 * An amount the forecast cash view sets aside from cash on hand, in addition to upcoming mandatory
 * expenses, which are always reserved. Selected through [EngineConfig.cashReserves].
 *
 * @see CashFlow.mustReserve
 */
enum class CashReserve {
    /**
     * Reserve the period's planned [DistributionResult.cushionTopup].
     */
    CUSHION_TOPUP,

    /**
     * Reserve the period's planned [DistributionResult.piggyBankActual].
     */
    PIGGY_BANK,

    /**
     * Reserve optional expenses dated after the current date ([CashFlow.upcomingOptional]).
     */
    UPCOMING_OPTIONAL
}
