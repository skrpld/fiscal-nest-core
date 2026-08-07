package budget

import java.math.BigDecimal
import java.math.RoundingMode
import budget.DecimalUtils.sumInput
import budget.DecimalUtils.toDecimal
import budget.DecimalUtils.quantizePct
import budget.Models.CriticalityLevel
import budget.Models.selectLevel

/**
 * Context for the distribution engine.
 * In time-agnostic mode liquidityAvailable is null.
 */
data class DistributionContext(
    val income: BigDecimal,
    val mandatory: BigDecimal,
    val optional: BigDecimal,
    val piggyPlanned: BigDecimal,
    val cushionCurrent: BigDecimal,
    val cushionTarget: BigDecimal,
    val liquidityAvailable: BigDecimal? = null,
    val liquidOnHand: BigDecimal? = null,
    val mustReserve: BigDecimal? = null,
) {
    companion object {
        fun fromRaw(
            income: BudgetInput,
            expensesMandatory: BudgetInput,
            expensesOptional: BudgetInput,
            piggyPlanned: BudgetInput,
            cushionCurrent: Number,
            cushionTarget: Number,
        ): DistributionContext = DistributionContext(
            income = sumInput(income),
            mandatory = sumInput(expensesMandatory),
            optional = sumInput(expensesOptional),
            piggyPlanned = sumInput(piggyPlanned),
            cushionCurrent = toDecimal(cushionCurrent),
            cushionTarget = toDecimal(cushionTarget),
        )
    }

    init {
        require(cushionCurrent >= BigDecimal.ZERO) { "cushionCurrent must be non-negative, got $cushionCurrent" }
        require(cushionTarget >= BigDecimal.ZERO) { "cushionTarget must be non-negative, got $cushionTarget" }
    }
}

data class DistributionResult(
    val topup: BigDecimal,
    val piggyActual: BigDecimal,
    val remaining: BigDecimal,
    val level: CriticalityLevel?,
    val fillPct: BigDecimal,
    val cushionNeed: BigDecimal,
    val fromPiggy: BigDecimal = BigDecimal.ZERO,
    val fromRemaining: BigDecimal = BigDecimal.ZERO,
    val isCrisis: Boolean = false,
    val crisisType: String? = null,
    val deficitTotal: BigDecimal? = null,
    val baseRemainingNoPiggy: BigDecimal? = null,
)

private fun calcFillPct(cushionCurrent: BigDecimal, cushionTarget: BigDecimal): BigDecimal {
    if (cushionTarget <= BigDecimal.ZERO) return BigDecimal("100")
    return (cushionCurrent.divide(cushionTarget, 10, RoundingMode.HALF_UP)) * BigDecimal("100")
}

fun distribute(ctx: DistributionContext): DistributionResult {
    val monthlyBase = ctx.income - (ctx.mandatory + ctx.optional + ctx.piggyPlanned)
    val fillPct = calcFillPct(ctx.cushionCurrent, ctx.cushionTarget)
    val cushionNeed = (ctx.cushionTarget - ctx.cushionCurrent).max(BigDecimal.ZERO)
    val level = selectLevel(fillPct, cushionNeed)

    val available = ctx.liquidityAvailable ?: monthlyBase

    // Priority 1: Liquidity crisis
    if (ctx.liquidityAvailable != null && ctx.liquidityAvailable < BigDecimal.ZERO) {
        return DistributionResult(
            topup = BigDecimal.ZERO,
            piggyActual = BigDecimal.ZERO,
            remaining = ctx.liquidityAvailable,
            level = level,
            fillPct = fillPct,
            cushionNeed = cushionNeed,
            isCrisis = true,
            crisisType = "liquidity",
        )
    }

    // Priority 2: Monthly overspending
    if (monthlyBase < BigDecimal.ZERO) {
        val baseNoPiggy = ctx.income - (ctx.mandatory + ctx.optional)
        val deficit = if (baseNoPiggy < BigDecimal.ZERO) baseNoPiggy.abs() else monthlyBase.abs()
        val remaining = if (ctx.liquidityAvailable != null) available else baseNoPiggy

        return DistributionResult(
            topup = BigDecimal.ZERO,
            piggyActual = BigDecimal.ZERO,
            remaining = remaining,
            level = level,
            fillPct = fillPct,
            cushionNeed = cushionNeed,
            isCrisis = true,
            crisisType = "overspending",
            deficitTotal = deficit,
            baseRemainingNoPiggy = baseNoPiggy,
        )
    }

    // Priority 3: Fully funded cushion
    if (level == null) {
        val actualPiggy = ctx.piggyPlanned.min(available)
        val finalRemaining = available - actualPiggy
        return DistributionResult(
            topup = BigDecimal.ZERO,
            piggyActual = actualPiggy,
            remaining = finalRemaining,
            level = null,
            fillPct = fillPct,
            cushionNeed = cushionNeed,
        )
    }

    // Priority 4: Active redistribution
    var fromPiggy = ctx.piggyPlanned * level.piggyTakePct
    var fromRemaining = monthlyBase * level.remainingTakePct
    val plannedTopup = fromPiggy + fromRemaining

    var cap = cushionNeed
    if (ctx.liquidityAvailable != null) {
        cap = cap.min(ctx.liquidityAvailable)
    }

    val actualTopup = plannedTopup.min(cap)

    if (plannedTopup > BigDecimal.ZERO && actualTopup < plannedTopup) {
        val ratio = actualTopup.divide(plannedTopup, 10, RoundingMode.HALF_UP)
        fromPiggy = fromPiggy * ratio
        fromRemaining = fromRemaining * ratio
    }

    val plannedPiggyAfter = ctx.piggyPlanned - fromPiggy
    var actualPiggy = plannedPiggyAfter.min(available - actualTopup)
    if (actualPiggy < BigDecimal.ZERO) actualPiggy = BigDecimal.ZERO

    val finalRemaining = available - actualTopup - actualPiggy

    return DistributionResult(
        topup = actualTopup,
        piggyActual = actualPiggy,
        remaining = finalRemaining,
        level = level,
        fillPct = fillPct,
        cushionNeed = cushionNeed,
        fromPiggy = fromPiggy,
        fromRemaining = fromRemaining,
    )
}
