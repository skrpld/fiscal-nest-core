package budget

import java.time.LocalDate

data class BudgetMessages(
    val distribution: String,
    val timeAnalysis: String,
)

data class BudgetResult(
    val fillPct: Double,
    val cushionTopup: Double,
    val piggyActual: Double,
    val moneyRemaining: Double,
    val message: String,
    val crisisType: CrisisType,
    val cushionCurrent: Double,
    val cushionTarget: Double,
    val criticalityLevel: CriticalityLevel?,
    val baseRemainingNoPiggy: Double?,
    val remainingAfterMandatory: Double,
    val totalIncome: Double,
    val totalMandatory: Double,
    val totalOptional: Double,
    val totalPlanned: Double,
)

data class TimeBudgetResult(
    val fillPct: Double,
    val cushionTopup: Double,
    val piggyActual: Double,
    val moneyRemaining: Double,
    val messages: BudgetMessages,
    val periodStart: LocalDate,
    val periodEnd: LocalDate,
    val currentDate: LocalDate,
    val daysInPeriod: Int,
    val daysElapsed: Int,
    val daysRemaining: Int,
    val dailyBudgetPlan: Double,
    val dailyBudgetActual: Double,
    val dailyBudgetCashflow: Double,
    val incomeExpectedTotal: Double,
    val incomeReceivedTotal: Double,
    val incomePendingTotal: Double,
    val upcomingMandatoryTotal: Double,
    val upcomingOptionalTotal: Double,
    val alreadySpentTotal: Double,
    val burnRate: Double,
    val crisisType: CrisisType,
    val cushionCurrent: Double,
    val cushionTarget: Double,
    val criticalityLevel: CriticalityLevel?,
    val baseRemainingNoPiggy: Double?,
    val remainingAfterMandatory: Double,
    val totalIncome: Double,
    val totalMandatory: Double,
    val totalOptional: Double,
    val totalPlanned: Double,
)
