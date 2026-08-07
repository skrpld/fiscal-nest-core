# Glossary

| Term | Definition |
|------|------------|
| **Raw Remainder** | Income minus mandatory expenses. |
| **Net Remainder** | Raw remainder minus optional expenses. |
| **Post-Cushion Remainder** | Net remainder minus cushion top-up. |
| **Free Remainder** | Post-cushion remainder minus piggy bank. The truly discretionary money. |
| **Cushion** | Safety buffer with target balance. |
| **Cushion Fill %** | `cushionCurrent / cushionTarget * 100`. |
| **Cushion Need** | `max(0, cushionTarget - cushionCurrent)`. |
| **Post-Distribution Balance** | `cushionCurrent(input) + cushionTopup`. Reported in `DistributionResult.cushionCurrent`. |
| **Piggy Bank** | Savings allocation derived from the remainder. |
| **Admissibility %** | Max share of remainder that can go to the piggy bank (for piggy) or cushion top-up (for criticality levels). |
| **Expense Crisis** | Income insufficient for mandatory + optional expenses. |
| **Cushion Crisis** | Cushion fill % below the active criticality threshold. |
| **Criticality Level** | User-defined rule set for cushion top-up aggressiveness. |
| **Burn Rate** | Average daily spending so far in the period. |
| **Daily Cashflow** | Conservative daily budget after reserving for upcoming mandatory expenses and cushion top-up. |
| **Daily Plan** | Theoretical even split of free remainder across the whole period. |
| **Daily Actual** | Free remainder divided by days remaining. |
| **Forecast Horizon** | Number of future periods the engine should project. |
| **Period** | A contiguous date range `[periodStart, periodEnd]` inclusive. |
| **Opening Balance** | Free remainder carried from the previous period (`0` for period 1). Added to the period's income. |
| **Closing Balance** | Free remainder at the end of a period. Carried forward as the next period's opening balance. |
| **Liquid On Hand** | Cash physically available now: `receivedIncome + openingBalance - alreadySpent`. |
| **Must Reserve** | Upcoming mandatory expenses that must be reserved: `upcomingMandatory`. |
| **Available** | Conservative spending capacity: `liquidOnHand - mustReserve`. |
| **Carry-Forward** | Propagation of `closingBalance` and `cushionState` from one forecast period to the next. |
| **WHAT_IF Mode** | Light, time-agnostic snapshot calculation. |
| **FORECAST Mode** | Heavy, calendar-aware multi-period projection with daily metrics. |
| **Engine** | The `fiscal-nest-core` module — pure business logic, stateless. |
| **Client** | The consuming application (mobile, server, CLI) — handles UI, DB, formatting, messaging. |
| **Fail-Fast Validation** | Rejecting invalid inputs immediately with `IllegalArgumentException` before any calculation. |
| **Engine Facade** | `BudgetCalculator` — the single public entry point for all calculations. |
| **Internal API** | Engine implementation classes (`DistributionEngine`, `CalendarEngine`, `DecimalUtils`, `InputValidator`) marked `internal`. |
