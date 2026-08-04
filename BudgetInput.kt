package budget

import java.math.BigDecimal

sealed interface BudgetInput {
    fun toBigDecimals(): List<BigDecimal>
    fun sum(): BigDecimal = toBigDecimals().fold(BigDecimal.ZERO, BigDecimal::add)

    data class Single(val value: Number) : BudgetInput {
        override fun toBigDecimals(): List<BigDecimal> = listOf(value.toBudgetDecimal())
    }

    data class Multiple(val values: kotlin.collections.List<Number>) : BudgetInput {
        override fun toBigDecimals(): List<BigDecimal> = values.map { it.toBudgetDecimal() }
    }

    companion object {
        fun of(value: Number): BudgetInput = Single(value)
        fun of(vararg values: Number): BudgetInput = Multiple(values.toList())
        fun of(values: kotlin.collections.List<Number>): BudgetInput = Multiple(values)
    }
}

internal fun Number.toBudgetDecimal(): BigDecimal = when (this) {
    is Int -> BigDecimal(this)
    is Long -> BigDecimal(this)
    is Double -> BigDecimal.toBigDecimal(this.toString())
    is Float -> BigDecimal.toBigDecimal(this.toString())
    is BigDecimal -> this
    else -> throw IllegalArgumentException("Unsupported numeric type: ${this::class.simpleName}")
}
