# Simulation clock validation — 1 November 2026

The approved change adds a persistent shared market clock, active-session trading admission, durable execution-derived candles, and restart-safe session cleanup across BTC, ETH and SOL. The coordinated production reset preserves login identities and archives the previous demo trading state.

## Pre-deployment evidence

| Check | Result |
|---|---|
| Full Java suite with PostgreSQL/Kafka integration | 336 tests, zero failures or skips |
| Java formatting and all backend boot jars | Passed |
| Frontend tests, type checking, lint and production build | 26 tests passed; all checks passed |
| Python operator/validation/smoke tests | 11 passed |
| Helm lint and deployment shell syntax | Passed |
| Operator migration launcher and reset SQL | Passed on disposable full schema; reset transaction rolled back |

The fresh whole-branch review found three recovery defects. Regression tests reproduced and verified fixes for reservation admission ending before the account transaction commits, risk restarting with obsolete price references, and expired preparation remaining inaccessible from the UI. Additional integration testing fixed a shared test dependency that incorrectly enabled a gateway datasource, and separated clock polling from the account-read rate limit.

Real journal tests cover crashes after matching admission, after canonical output storage, and after publication but before acknowledgment. Recovery preserves the original execution context and output payloads; an undecided command arriving after admission closes is rejected.

## Isolated complete simulation

The private Docker preview runs on the Oracle VPS with separate networks, ports and volumes. It does not modify the Kubernetes production database or Kafka state.

- Epoch: `d6d661af-1229-43f8-9a4b-fa70be36292b`.
- Initialization: `2026-10-31T11:22:05.839Z`.
- Validation finished: `2026-10-31T11:29:26.202993Z`.
- Five-minute session: exactly `300000ms`; clock, prices and candles remained unchanged for 60 real seconds afterward.
- Another user's one-minute session: exactly `360000ms`, with no idle-time gap.
- Every candle's OHLC, volume and trade count matched persisted executions, including five-minute aggregation. Ledger totals reconciled and no reservation or order intent remained open.

| Instrument | Minute 1 trades | 2 | 3 | 4 | 5 | Resumed minute 6 |
|---|---:|---:|---:|---:|---:|---:|
| BTC_USD | 16 | 20 | 19 | 22 | 21 | 18 |
| ETH_USD | 17 | 21 | 19 | 20 | 22 | 19 |
| SOL_USD | 16 | 21 | 21 | 20 | 19 | 20 |

Cold restart preserved the first six minutes of candle history. An administrator deposit while paused returned `409 SIMULATION_NOT_ACTIVE` without changing balances. A further one-minute run used persisted references of BTC $70000, ETH $4000 and SOL $200, outside their original startup bands. It produced 20 BTC, 17 ETH and 16 SOL executions. The deployment smoke check passed actual fills, settlement, tape visibility, exact account deltas, released reservations and paused rejection.

## Production operation

Pending: sealed backup and restore rehearsal, coordinated reset, exact-commit CI/CD deployment, then the controlled first five-minute production run and pause/resume validation. No production success is claimed by the pre-deployment evidence above.

Browser surfaces were unavailable during implementation. API, database and frontend build/tests provide evidence; visual browser interaction remains a separate limitation.
