# Glossary

| Term | Definition |
|------|------------|
| **Ratio (percentage)** | Any percentage-like value. Always on the `0.0–1.0` scale inside the engine (`0.25` = 25%). Showing `0–100` is a client display concern. |
| **Raw Remainder** | Income minus mandatory expenses. |
| **Net Remainder** | Raw remainder minus optional expenses. |
| **Post-Cushion Remainder** | Net remainder minus cushion top-up. |
| **Free Remainder** | Post-cushion remainder minus piggy bank. The truly discretionary money. |
| **Cushion** | Safety buffer with target balance. |
| **Cushion Fill Ratio** | `cushionCurrent / cushionTarget` on the `0.0–1.0` scale (`1` when the target is `0`; above `1` when overfilled). Reported as `cushionFillPct`. |
| **Cushion Need** | `max(0, cushionTarget - cushionCurrent)`. |
| **Post-Distribution Balance** | `cushionCurrent(input) + cushionTopup`. Reported in `DistributionResult.cushionCurrent`. |
| **Piggy Bank** | Savings allocation derived from the remainder. |
| **Admissibility %** | Max share of remainder that can go to the piggy bank (for piggy) or cushion top-up (for criticality levels). |
| **Expense Crisis** | Income insufficient for mandatory + optional expenses. |
| **Cushion Crisis** | Cushion fill ratio below the active criticality threshold. |
| **Criticality Level** | User-defined rule set for cushion top-up aggressiveness. |
| **Plan (period plan)** | The distribution of a forecast period, built from **all** of its income and expenses regardless of date, exactly like WHAT_IF. `ForecastResult.distribution`. |
| **Cash View** | The money that has actually moved by `currentDate`, with income and expenses both split by date. `ForecastResult.cashFlow`. |
| **Received / Pending Income** | Income dated on or before / after `currentDate`. |
| **Paid / Upcoming Expenses** | Expenses dated on or before / after `currentDate`, tracked separately for mandatory and optional expenses. |
| **Already Spent** | Unscheduled spending in the first period so far — money not covered by any expense event. |
| **Burn Rate** | Average unscheduled spending per day so far, today included. |
| **Daily Cashflow** | Conservative daily budget after reserving for upcoming mandatory expenses and cushion top-up. |
| **Daily Plan** | Theoretical even split of free remainder across the whole period. |
| **Daily Actual** | Free remainder not yet spent, divided by days remaining. |
| **Forecast Horizon** | Number of future periods the engine should project. |
| **Period** | A contiguous date range `[periodStart, periodEnd]` inclusive. Periods spanning whole months chain by calendar months. |
| **Opening Balance** | Closing balance of the previous period (`0` for period 1). Added to the period's income. Negative when a deficit is carried. |
| **Closing Balance** | `freeRemainder - alreadySpent`: free money left at the end of a period. Carried forward as the next period's opening balance. |
| **Liquid On Hand** | Cash physically available now: `openingBalance + receivedIncome - paidMandatory - paidOptional - alreadySpent`. |
| **Must Reserve** | Upcoming mandatory expenses that must be reserved: `upcomingMandatory`. |
| **Available** | Conservative spending capacity: `liquidOnHand - mustReserve`. |
| **Carry-Forward** | Propagation of `closingBalance` and `cushionState` from one forecast period to the next. |
| **WHAT_IF Mode** | Light, time-agnostic snapshot calculation from aggregate amounts. |
| **FORECAST Mode** | Heavy, calendar-aware multi-period projection with a plan, a cash view and daily metrics per period. |
| **Engine** | The `fiscal-nest-core` module — pure business logic, stateless. |
| **Client** | The consuming application (mobile, server, CLI) — handles UI, DB, formatting, messaging, and display data such as event names and categories. |
| **Fail-Fast Validation** | Rejecting invalid inputs immediately with `IllegalArgumentException` before any calculation. |
| **Engine Facade** | `BudgetCalculator` — the single public entry point for all calculations. |
| **Internal API** | Implementation classes (`DistributionEngine`, `CalendarEngine`, `PeriodSchedule`, `PeriodSnapshot`, `DecimalUtils`, `InputValidator`) marked `internal`. |
