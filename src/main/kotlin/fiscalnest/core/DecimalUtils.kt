/*
 * Copyright 2026 skrpld
 * SPDX-License-Identifier: Apache-2.0
 */

package fiscalnest.core

import java.math.BigDecimal

/**
 * Quantizes decimals using the scales and rounding mode of an [EngineConfig].
 */
internal object DecimalUtils {
    /**
     * Quantizes a monetary value to [EngineConfig.moneyScale].
     *
     * @param value monetary value to quantize
     * @param config engine configuration
     * @return value with exactly `moneyScale` decimal places
     */
    fun quantizeMoney(value: BigDecimal, config: EngineConfig): BigDecimal =
        value.setScale(config.moneyScale, config.roundingMode)

    /**
     * Quantizes a ratio on the `0.0..1.0` scale to [EngineConfig.percentageScale].
     *
     * @param value ratio to quantize
     * @param config engine configuration
     * @return value with exactly `percentageScale` decimal places
     */
    fun quantizePct(value: BigDecimal, config: EngineConfig): BigDecimal =
        value.setScale(config.percentageScale, config.roundingMode)

    /**
     * Divides a monetary value by a number of days and quantizes the quotient to
     * [EngineConfig.moneyScale].
     *
     * @param value monetary value to divide
     * @param days positive divisor
     * @param config engine configuration
     * @return per-day amount with exactly `moneyScale` decimal places
     */
    fun perDay(value: BigDecimal, days: Int, config: EngineConfig): BigDecimal =
        value.divide(BigDecimal.valueOf(days.toLong()), config.moneyScale, config.roundingMode)
}
