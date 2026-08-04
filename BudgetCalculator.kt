package budget

import budget.formatters.RussianFormatter
import budget.formatters.TimeMessageFormatter
import java.math.BigDecimal
import java.time.LocalDate

object BudgetCalculator {

    @JvmStatic
    fun calculateBudget(
        income: BudgetInput,
        expensesMandatory: BudgetInput,
        expensesOptional: BudgetInput,
        piggyPlanned: BudgetInput,
        cushionCurrent: BudgetInput,
        cushionTarget: BudgetInput,
    ): BudgetResult {
        val ctx = DistributionContext.fromRaw(
            income, expensesMandatory, expensesOptional, piggyPlanned,
            cushionCurrent.sum(), cushionTarget.sum()
        )
        val result = distribute(ctx)
        val formatter = RussianFormatter()
        val message = formatter.format(result, ctx)

        return BudgetResult(
            fillPct = DecimalUtils.quantizePct(result.fillPct).toDouble(),
            cushionTopup = DecimalUtils.quantizeMoney(result.topup).toDouble(),
            piggyActual = DecimalUtils.quantizeMoney(result.piggyActual).toDouble(),
            moneyRemaining = DecimalUtils.quantizeMoney(result.remaining).toDouble(),
            message = message,
            crisisType = mapCrisisType(result.crisisType),
            cushionCurrent = DecimalUtils.quantizeMoney(ctx.cushionCurrent).toDouble(),
            cushionTarget = DecimalUtils.quantizeMoney(ctx.cushionTarget).toDouble(),
            criticalityLevel = CriticalityLevel.select(result.fillPct, ctx.cushionTarget - ctx.cushionCurrent),
            baseRemainingNoPiggy = result.baseRemainingNoPiggy?.let { DecimalUtils.quantizeMoney(it).toDouble() },
            remainingAfterMandatory = DecimalUtils.quantizeMoney(ctx.income - ctx.mandatory).toDouble(),
            totalIncome = DecimalUtils.quantizeMoney(ctx.income).toDouble(),
            totalMandatory = DecimalUtils.quantizeMoney(ctx.mandatory).toDouble(),
            totalOptional = DecimalUtils.quantizeMoney(ctx.optional).toDouble(),
            totalPlanned = DecimalUtils.quantizeMoney(ctx.mandatory + ctx.optional + ctx.piggyPlanned).toDouble(),
        )
    }

    // Overload 1: aggregate inputs (no schedule)
    @JvmStatic
    fun calculateBudgetTimed(
        income: BudgetInput,
        expensesMandatory: BudgetInput,
        expensesOptional: BudgetInput,
        piggyPlanned: BudgetInput = BudgetInput.of(0),
        cushionCurrent: BudgetInput = BudgetInput.of(0),
        cushionTarget: BudgetInput = BudgetInput.of(0),
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currentDate: LocalDate,
        alreadySpent: BudgetInput = BudgetInput.of(0),
    ): TimeBudgetResult {
        return calculateBudgetTimedInternal(
            income = income,
            expensesMandatory = expensesMandatory,
            expensesOptional = expensesOptional,
            incomeSchedule = emptyList(),
            expensesSchedule = emptyList(),
            piggyPlanned = piggyPlanned,
            cushionCurrent = cushionCurrent,
            cushionTarget = cushionTarget,
            periodStart = periodStart,
            periodEnd = periodEnd,
            currentDate = currentDate,
            alreadySpent = alreadySpent,
        )
    }

    // Overload 2: schedule-based (no aggregate inputs)
    @JvmStatic
    fun calculateBudgetTimed(
        incomeSchedule: List<IncomeEvent> = emptyList(),
        expensesSchedule: List<ExpenseEvent> = emptyList(),
        piggyPlanned: BudgetInput = BudgetInput.of(0),
        cushionCurrent: BudgetInput = BudgetInput.of(0),
        cushionTarget: BudgetInput = BudgetInput.of(0),
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currentDate: LocalDate,
        alreadySpent: BudgetInput = BudgetInput.of(0),
    ): TimeBudgetResult {
        return calculateBudgetTimedInternal(
            income = null,
            expensesMandatory = null,
            expensesOptional = null,
            incomeSchedule = incomeSchedule,
            expensesSchedule = expensesSchedule,
            piggyPlanned = piggyPlanned,
            cushionCurrent = cushionCurrent,
            cushionTarget = cushionTarget,
            periodStart = periodStart,
            periodEnd = periodEnd,
            currentDate = currentDate,
            alreadySpent = alreadySpent,
        )
    }

    private fun calculateBudgetTimedInternal(
        income: BudgetInput?,
        expensesMandatory: BudgetInput?,
        expensesOptional: BudgetInput?,
        incomeSchedule: List<IncomeEvent>,
        expensesSchedule: List<ExpenseEvent>,
        piggyPlanned: BudgetInput,
        cushionCurrent: BudgetInput,
        cushionTarget: BudgetInput,
        periodStart: LocalDate,
        periodEnd: LocalDate,
        currentDate: LocalDate,
        alreadySpent: BudgetInput,
    ): TimeBudgetResult {
        val expensesMandatorySchedule = expensesSchedule.filter { it.isMandatory }
        val expensesOptionalSchedule = expensesSchedule.filter { !it.isMandatory }

        var snapshot = buildSnapshot(
            periodStart = periodStart,
            periodEnd = periodEnd,
            currentDate = currentDate,
            incomeSchedule = incomeSchedule,
            expensesMandatorySchedule = expensesMandatorySchedule,
            expensesOptionalSchedule = expensesOptionalSchedule,
        )

        fun scheduleTotal(schedule: List<ExpenseEvent>): BigDecimal {
            return if (schedule.isNotEmpty()) schedule.sumOf { DecimalUtils.sumInput(it.amount) } else BigDecimal.ZERO
        }

        fun scheduleIncomeTotal(schedule: List<IncomeEvent>): BigDecimal {
            return if (schedule.isNotEmpty()) schedule.sumOf { DecimalUtils.sumInput(it.amount) } else BigDecimal.ZERO
        }

        val schedIncomeTotal = scheduleIncomeTotal(incomeSchedule)
        val schedMandTotal = scheduleTotal(expensesMandatorySchedule)
        val schedOptTotal = scheduleTotal(expensesOptionalSchedule)

        val totalIncome =
            if (incomeSchedule.isNotEmpty()) schedIncomeTotal else DecimalUtils.sumInput(income ?: BudgetInput.of(0))
        val totalMandatory = if (expensesMandatorySchedule.isNotEmpty()) schedMandTotal else DecimalUtils.sumInput(
            expensesMandatory ?: BudgetInput.of(0)
        )
        val totalOptional = if (expensesOptionalSchedule.isNotEmpty()) schedOptTotal else DecimalUtils.sumInput(
            expensesOptional ?: BudgetInput.of(0)
        )

        val totalPiggyPlanned = DecimalUtils.sumInput(piggyPlanned)
        val alreadySpentDec = DecimalUtils.sumInput(alreadySpent)

        if (incomeSchedule.isEmpty()) {
            snapshot = snapshot.copy(
                receivedIncome = totalIncome,
                pendingIncome = BigDecimal.ZERO,
            )
        }

        val liquid = snapshot.receivedIncome - alreadySpentDec
        val mustReserve = snapshot.upcomingMandatory
        val available = liquid - mustReserve

        val monthlyBaseRemaining = totalIncome - (totalMandatory + totalOptional + totalPiggyPlanned)

        val ctx = DistributionContext(
            income = totalIncome,
            mandatory = totalMandatory,
            optional = totalOptional,
            piggyPlanned = totalPiggyPlanned,
            cushionCurrent = cushionCurrent.sum(),
            cushionTarget = cushionTarget.sum(),
            liquidityAvailable = available,
            liquidOnHand = liquid,
            mustReserve = mustReserve,
        )
        val result = distribute(ctx)

        val daily = DailyMetrics.compute(
            snapshot = snapshot,
            distResult = result,
            alreadySpent = alreadySpentDec,
            monthlyBaseRemaining = monthlyBaseRemaining,
        )

        val formatter = RussianFormatter()
        val timeFormatter = TimeMessageFormatter()

        val message = formatter.format(result, ctx)
        val messageTime = timeFormatter.format(snapshot, daily)

        val moneyRemainingValue = if (result.crisisType == "overspending") {
            result.baseRemainingNoPiggy ?: result.remaining
        } else {
            result.remaining
        }

        return TimeBudgetResult(
            fillPct = DecimalUtils.quantizePct(result.fillPct).toDouble(),
            cushionTopup = DecimalUtils.quantizeMoney(result.topup).toDouble(),
            piggyActual = DecimalUtils.quantizeMoney(result.piggyActual).toDouble(),
            moneyRemaining = DecimalUtils.quantizeMoney(moneyRemainingValue).toDouble(),
            messages = BudgetMessages(
                distribution = message,
                timeAnalysis = messageTime,
            ),
            periodStart = snapshot.periodStart,
            periodEnd = snapshot.periodEnd,
            currentDate = snapshot.currentDate,
            daysInPeriod = snapshot.daysInPeriod,
            daysElapsed = snapshot.daysElapsed,
            daysRemaining = snapshot.daysRemaining,
            dailyBudgetPlan = daily.plan,
            dailyBudgetActual = daily.actual,
            dailyBudgetCashflow = daily.cashflow,
            incomeExpectedTotal = DecimalUtils.quantizeMoney(totalIncome).toDouble(),
            incomeReceivedTotal = DecimalUtils.quantizeMoney(snapshot.receivedIncome).toDouble(),
            incomePendingTotal = DecimalUtils.quantizeMoney(snapshot.pendingIncome).toDouble(),
            upcomingMandatoryTotal = DecimalUtils.quantizeMoney(snapshot.upcomingMandatory).toDouble(),
            upcomingOptionalTotal = DecimalUtils.quantizeMoney(snapshot.upcomingOptional).toDouble(),
            alreadySpentTotal = DecimalUtils.quantizeMoney(alreadySpentDec).toDouble(),
            burnRate = daily.burnRate,
            crisisType = mapCrisisType(result.crisisType),
            cushionCurrent = DecimalUtils.quantizeMoney(ctx.cushionCurrent).toDouble(),
            cushionTarget = DecimalUtils.quantizeMoney(ctx.cushionTarget).toDouble(),
            criticalityLevel = CriticalityLevel.select(result.fillPct, ctx.cushionTarget - ctx.cushionCurrent),
            baseRemainingNoPiggy = result.baseRemainingNoPiggy?.let { DecimalUtils.quantizeMoney(it).toDouble() },
            remainingAfterMandatory = DecimalUtils.quantizeMoney(available).toDouble(),
            totalIncome = DecimalUtils.quantizeMoney(totalIncome).toDouble(),
            totalMandatory = DecimalUtils.quantizeMoney(totalMandatory).toDouble(),
            totalOptional = DecimalUtils.quantizeMoney(totalOptional).toDouble(),
            totalPlanned = DecimalUtils.quantizeMoney(totalMandatory + totalOptional + totalPiggyPlanned).toDouble(),
        )
    }

    private fun mapCrisisType(type: String?): CrisisType = when (type) {
        "overspending" -> CrisisType.Overspending
        "critical" -> CrisisType.Critical
        "cushion_deficit" -> CrisisType.CushionDeficit
        else -> CrisisType.None
    }
}
