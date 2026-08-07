package budget.formatters

import budget.DistributionResult
import budget.DistributionContext

interface Formatter {
    fun format(result: DistributionResult, ctx: DistributionContext): String
}
