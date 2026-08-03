package budget

import java.math.BigDecimal

sealed interface BudgetInput {
    fun toBigDecimals(): List<BigDecimal>

    data class Single(val value: Number) : BudgetInput {
        override fun toBigDecimals(): List<BigDecimal> = listOf(value.toBigDecimal())
    }

    data class List(val values: kotlin.collections.List<Number>) : BudgetInput {
        override fun toBigDecimals(): List<BigDecimal> = values.map { it.toBigDecimal() }
    }
}

fun Number.toBigDecimal(): BigDecimal = when (this) {
    is Int -> BigDecimal(this)
    is Long -> BigDecimal(this)
    is Double -> BigDecimal.toBigDecimal(this.toString())
    is Float -> BigDecimal.toBigDecimal(this.toString())
    is BigDecimal -> this
    else -> throw IllegalArgumentException("Unsupported numeric type: ${this::class.simpleName}")
}
