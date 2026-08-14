package fiscalnest.core

import java.math.BigDecimal

/**
 * Provides exact decimal summation and configurable quantization.
 */
internal object DecimalUtils {
    /**
     * Quantizes a monetary value using the configured money scale and rounding mode.
     *
     * @param value monetary value to quantize
     * @param config engine configuration
     * @return quantized monetary value
     */
    fun quantizeMoney(value: BigDecimal, config: EngineConfig): BigDecimal =
        value.setScale(config.moneyScale, config.roundingMode)

    /**
     * Quantizes a percentage value using the configured percentage scale and rounding mode.
     *
     * @param value percentage value to quantize
     * @param config engine configuration
     * @return quantized percentage value
     */
    fun quantizePct(value: BigDecimal, config: EngineConfig): BigDecimal =
        value.setScale(config.percentageScale, config.roundingMode)

    /**
     * Sums non-negative decimal values without quantization.
     *
     * @param values values to sum
     * @return exact sum of the values
     * @throws IllegalArgumentException if a value is negative
     */
    fun sum(values: List<BigDecimal>): BigDecimal {
        values.forEachIndexed { index, value ->
            requireNonNegative(value, "values[$index]")
        }
        return values.fold(BigDecimal.ZERO, BigDecimal::add)
    }

    /**
     * Requires a decimal value to be non-negative.
     *
     * @param value value to validate
     * @param name logical field name
     * @throws IllegalArgumentException if the value is negative
     */
    fun requireNonNegative(value: BigDecimal, name: String) {
        require(value.signum() >= 0) {
            "Amount must be non-negative: $name"
        }
    }
}
