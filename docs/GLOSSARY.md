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
| **Piggy Bank** | Savings allocation derived from the remainder. |
| **Admissibility %** | Max share of remainder that can go to the piggy bank (for piggy) or cushion top-up (for criticality levels). |
| **Expense Crisis** | Income insufficient for mandatory + optional expenses. |
| **Cushion Crisis** | Cushion fill % below the active criticality threshold. |
| **Criticality Level** | User-defined rule set for cushion top-up aggressiveness. |
| **Burn Rate** | Average daily spending so far in the period. |
| **Daily Cashflow** | Conservative daily budget after reserving for upcoming mandatory expenses. |
| **Forecast Horizon** | Number of future periods the engine should project. |
| **WHAT_IF Mode** | Light, time-agnostic snapshot calculation. |
| **FORECAST Mode** | Heavy, calendar-aware multi-period projection with daily metrics. |
| **Engine** | The `fiscal-nest-core` module — pure business logic, stateless. |
| **Client** | The consuming application (mobile, server, CLI) — handles UI, DB, formatting, messaging. |
