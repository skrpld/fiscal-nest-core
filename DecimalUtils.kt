package budget

import java.math.BigDecimal
import java.math.RoundingMode

object DecimalUtils {
    private val MONEY_SCALE = 2
    private val PCT_SCALE = 1

    fun sumInput(input: BudgetInput): BigDecimal {
        val list = input.toBigDecimals()
        if (list.isEmpty()) return BigDecimal.ZERO
        return list.fold(BigDecimal.ZERO) { acc, d ->
            require(d >= BigDecimal.ZERO) { "Value must be non-negative, got $d" }
            acc + d
        }
    }

    fun toDecimal(value: Number): BigDecimal = value.toBigDecimal()

    fun quantizeMoney(value: BigDecimal): BigDecimal =
        value.setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    fun quantizePct(value: BigDecimal): BigDecimal =
        value.setScale(PCT_SCALE, RoundingMode.HALF_UP)
}
