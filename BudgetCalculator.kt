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
        cushionCurrent: Number,
        cushionTarget: Number,
    ): BudgetResult {
        val ctx = DistributionContext.fromRaw(
            income, expensesMandatory, expensesOptional, piggyPlanned, cushionCurrent, cushionTarget
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
        )
    }

    @JvmStatic
    fun calculateBudgetTimed(
        income: BudgetInput? = null,
        expensesMandatory: BudgetInput? = null,
        expensesOptional: BudgetInput? = null,
        piggyPlanned: BudgetInput = BudgetInput.Single(0),
        cushionCurrent: Number = 0,
        cushionTarget: Number = 0,
        incomeSchedule: List<IncomeEvent>? = null,
        expensesMandatorySchedule: List<ExpenseEvent>? = null,
        expensesOptionalSchedule: List<ExpenseEvent>? = null,
        periodStart: LocalDate? = null,
        currentDate: LocalDate? = null,
        alreadySpent: BudgetInput = BudgetInput.Single(0),
    ): TimeBudgetResult {
        requireNotNull(periodStart)
        requireNotNull(currentDate)

        var snapshot = buildSnapshot(
            periodStart = periodStart,
            currentDate = currentDate,
            incomeSchedule = incomeSchedule,
            expensesMandatorySchedule = expensesMandatorySchedule,
            expensesOptionalSchedule = expensesOptionalSchedule,
        )

        fun scheduleTotal(schedule: List<ExpenseEvent>?): BigDecimal? {
            return if (!schedule.isNullOrEmpty()) schedule.sumOf { DecimalUtils.sumInput(it.amount) } else null
        }
        fun scheduleIncomeTotal(schedule: List<IncomeEvent>?): BigDecimal? {
            return if (!schedule.isNullOrEmpty()) schedule.sumOf { DecimalUtils.sumInput(it.amount) } else null
        }

        val schedIncomeTotal = scheduleIncomeTotal(incomeSchedule)
        val schedMandTotal = scheduleTotal(expensesMandatorySchedule)
        val schedOptTotal = scheduleTotal(expensesOptionalSchedule)

        val totalIncome = schedIncomeTotal ?: DecimalUtils.sumInput(income ?: BudgetInput.Single(0))
        val totalMandatory = schedMandTotal ?: DecimalUtils.sumInput(expensesMandatory ?: BudgetInput.Single(0))
        val totalOptional = schedOptTotal ?: DecimalUtils.sumInput(expensesOptional ?: BudgetInput.Single(0))

        val totalPiggyPlanned = DecimalUtils.sumInput(piggyPlanned)
        val alreadySpentDec = DecimalUtils.sumInput(alreadySpent)

        if (incomeSchedule.isNullOrEmpty()) {
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
            cushionCurrent = DecimalUtils.toDecimal(cushionCurrent),
            cushionTarget = DecimalUtils.toDecimal(cushionTarget),
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
            message = message,
            periodStart = snapshot.periodStart.toString(),
            periodEnd = snapshot.periodEnd.toString(),
            currentDate = snapshot.currentDate.toString(),
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
            messageTime = messageTime,
        )
    }
}
