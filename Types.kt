package budget

import java.time.LocalDate

data class BudgetResult(
    val fillPct: Double,
    val cushionTopup: Double,
    val piggyActual: Double,
    val moneyRemaining: Double,
    val message: String,
    val crisisType: String?,
    val isOverspending: Boolean,
    val cushionCurrent: Double,
    val cushionTarget: Double,
)

data class TimeBudgetResult(
    val fillPct: Double,
    val cushionTopup: Double,
    val piggyActual: Double,
    val moneyRemaining: Double,
    val message: String,
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
    val messageTime: String,
    val crisisType: String?,
    val isOverspending: Boolean,
    val cushionCurrent: Double,
    val cushionTarget: Double,
)
