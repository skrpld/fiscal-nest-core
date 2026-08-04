package budget

import java.math.BigDecimal

sealed interface BudgetInput {
    fun toBigDecimals(): List<BigDecimal>

    data class Single(val value: Number) : BudgetInput {
        override fun toBigDecimals(): List<BigDecimal> = listOf(value.toBigDecimal())
    }

    data class Multiple(val values: kotlin.collections.List<Number>) : BudgetInput {
        override fun toBigDecimals(): List<BigDecimal> = values.map { it.toBigDecimal() }
    }

    companion object {
        fun of(value: Number): BudgetInput = Single(value)
        fun of(vararg values: Number): BudgetInput = Multiple(values.toList())
        fun of(values: kotlin.collections.List<Number>): BudgetInput = Multiple(values)
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
