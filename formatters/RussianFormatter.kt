package budget.formatters

import budget.DistributionResult
import budget.DistributionContext
import budget.DecimalUtils.quantizeMoney
import budget.DecimalUtils.quantizePct
import java.math.BigDecimal

class RussianFormatter : Formatter {
    override fun format(result: DistributionResult, ctx: DistributionContext): String {
        return when {
            result.isCrisis -> formatCrisis(result, ctx)
            result.level == null -> formatFullyFunded(result, ctx)
            else -> formatRedistribution(result, ctx)
        }
    }

    private fun formatCrisis(result: DistributionResult, ctx: DistributionContext): String {
        return when (result.crisisType) {
            "liquidity" -> {
                val liquid = ctx.liquidOnHand ?: BigDecimal.ZERO
                val mustReserve = ctx.mustReserve ?: BigDecimal.ZERO
                val shortfall = result.remaining?.abs() ?: BigDecimal.ZERO
                "🚨 LIQUIDITY CRISIS: On hand (${quantizeMoney(liquid)}) is insufficient " +
                "for upcoming mandatory expenses (${quantizeMoney(mustReserve)}). " +
                "Shortfall: ${quantizeMoney(shortfall)}. Cushion and piggy bank are not replenished. " +
                "Monthly budget: income ${quantizeMoney(ctx.income)}, " +
                "mandatory ${quantizeMoney(ctx.mandatory)}, optional ${quantizeMoney(ctx.optional)}."
            }
            else -> {
                val baseNoPiggy = result.baseRemainingNoPiggy ?: BigDecimal.ZERO
                if (baseNoPiggy < BigDecimal.ZERO) {
                    val shortfallBasics = baseNoPiggy.abs()
                    "🚨 CRISIS: Income (${quantizeMoney(ctx.income)}) does not cover " +
                    "mandatory (${quantizeMoney(ctx.mandatory)}) + optional " +
                    "(${quantizeMoney(ctx.optional)}). Basic deficit: ${quantizeMoney(shortfallBasics)}. " +
                    "Piggy bank (${quantizeMoney(ctx.piggyPlanned)}) canceled, cushion not replenished. " +
                    "Total deficit (including piggy bank): ${quantizeMoney(result.deficitTotal ?: BigDecimal.ZERO)}."
                } else {
                    "⚠️ OVERSPEND: Income (${quantizeMoney(ctx.income)}) covers mandatory + optional " +
                    "(${quantizeMoney(ctx.mandatory + ctx.optional)}), but adding piggy bank " +
                    "(${quantizeMoney(ctx.piggyPlanned)}) creates a deficit of ${quantizeMoney(result.deficitTotal ?: BigDecimal.ZERO)}. " +
                    "Piggy bank canceled, cushion not replenished."
                }
            }
        }
    }

    private fun formatFullyFunded(result: DistributionResult, ctx: DistributionContext): String {
        val fill = quantizePct(result.fillPct)
        return when {
            ctx.cushionTarget.compareTo(BigDecimal.ZERO) == 0 ->
                "✅ Cushion target is zero. Replenishment not required. Piggy bank accumulation mode is active."
            ctx.cushionCurrent > ctx.cushionTarget ->
                "✅ Safety cushion is overfilled ($fill%). Piggy bank accumulation mode is active."
            else ->
                "✅ Safety cushion is fully funded. Piggy bank accumulation mode is active."
        }
    }

    private fun formatRedistribution(result: DistributionResult, ctx: DistributionContext): String {
        val level = result.level
        return if (result.fromPiggy.compareTo(BigDecimal.ZERO) == 0 && result.fromRemaining.compareTo(BigDecimal.ZERO) == 0) {
            "📊 Level: ${level?.name}. Cushion is filled at ${quantizePct(result.fillPct)}%. " +
            "Need to add: ${quantizeMoney(result.cushionNeed)}. However, there is nothing to redirect."
        } else {
            "📊 Level: ${level?.name}. Cushion is filled at ${quantizePct(result.fillPct)}%. " +
            "Need to add: ${quantizeMoney(result.cushionNeed)}. " +
            "Redirected from piggy bank: ${quantizeMoney(result.fromPiggy)}, " +
            "from free remainder: ${quantizeMoney(result.fromRemaining)}."
        }
    }
}
