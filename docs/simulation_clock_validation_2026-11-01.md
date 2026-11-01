# Simulation clock validation — 1 November 2026

The approved change adds a persistent shared market clock, active-session trading admission, durable execution-derived candles, and restart-safe session cleanup across BTC, ETH and SOL. The coordinated production reset preserves login identities and archives the previous demo trading state.

## Pre-deployment evidence

| Check | Result |
|---|---|
| Full Java suite with PostgreSQL/Kafka integration | 336 tests, zero failures or skips |
| Java formatting and all backend boot jars | Passed |
| Frontend tests, type checking, lint and production build | 26 tests passed; all checks passed |
| Python operator/validation/smoke/benchmark tests | 15 passed |
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

The sealed backup passed a complete isolated restore rehearsal: database row counts, authentication identity digest, Kafka command history and consumer offsets, and all 243 Redis keys matched. Production migration and the approved demo reset completed with application writers stopped. All 68 existing authentication identities and account IDs were preserved; 272 seeded balances match 272 deposit ledger entries.

Release `95950e3e8006f307e37e6eca99b95810dfcc501d` passed [GitHub CI](https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange/actions/runs/41269568568) and [GitHub CD, including maintenance smoke](https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange/actions/runs/41270058848). All ten applications were verified ready with that exact image tag and no application pod restarts. The [scheduled benchmark workflow](https://github.com/Kartik-MRK/Distributed-Electronic-Trading-Exchange/actions/runs/41288152696) also succeeded; paused trading is explicitly skipped, not measured as a performance pass.

The epoch is `0da863fd-e3cb-4711-be74-48092604fe34`, initialized once at `2026-11-01T03:31:37.596Z`. It remained at zero through deployment and the interruption, then was conditionally enabled for live validation. The public HTTPS site and health endpoint return 200 with browser/smoke user agents. Cloudflare rejects Python's default user agent with error 1010; this is an edge access policy, not an application health failure.

The controlled production validation ran from `2026-11-01T08:31:32.566813Z` through `2026-11-01T08:38:50.012342Z` and passed:

- Five active minutes ended at exactly `300000ms`, with five execution-derived candles per instrument.
- Clock, prices and candles remained identical for 60 real seconds; paused orders were rejected without changing balances.
- A second authenticated user started a one-minute session, ending at exactly `360000ms` with no idle-time gap.
- All 18 one-minute candles and five-minute aggregations matched persisted executions exactly. Every full minute contained trades and price variation. Ledger totals reconciled; no reservation or order intent remained open.

| Instrument | Minute 1 trades | 2 | 3 | 4 | 5 | Resumed minute 6 |
|---|---:|---:|---:|---:|---:|---:|
| BTC_USD | 16 | 20 | 19 | 20 | 20 | 19 |
| ETH_USD | 16 | 21 | 20 | 20 | 21 | 18 |
| SOL_USD | 16 | 20 | 20 | 21 | 20 | 18 |

An independent client recorded 345 candle updates over the public TLS WebSocket: 114 BTC, 116 ETH and 115 SOL. Its final revisions matched all 18 REST candles exactly, and it received zero candle updates during the frozen interval. A guarded market-data restart preserved all six candles per instrument and the `360000ms` clock. The WebSocket reconnected successfully, with two connections and zero transport/protocol errors. Admission was restored after the restart; the market remains paused until a session starts.

Backups and private evidence remain under `/home/ubuntu/dete-backups/20261101-simulation-clock`: the sealed pre-reset archives, restore proof, storage incident record, reset verification, live simulation and restart evidence. Subsequent ordinary deployments run the session-aware one-minute smoke test and may therefore add another minute of legitimate market history.

The maintenance backup exposed a legacy storage defect: ZooKeeper's image-declared child volumes shadowed its parent PVC mount, so restarting the pod lost its metadata. The original PostgreSQL dump and Kafka log archive remained intact. Metadata reconstructed from the captured cluster ID, topic IDs, partition assignments and configuration passed an isolated recovery check against all 14 topic identities, command-log ends and consumer offsets, including replacement of the ZooKeeper container. Explicit persistent mounts now cover both child directories; a regression test prevents backup shutdown on the unsafe configuration. The backup retains the original empty ZooKeeper archive and records the reconstruction explicitly.

Browser surfaces were unavailable during implementation. API, database and frontend build/tests provide evidence; visual browser interaction remains a separate limitation.

The scheduled benchmark now uses the deployed release, skips explicitly while paused, carries the active session ID, and prices orders from the current market. Regression tests also prevent partial request success from being reported as a performance pass. The scheduled job does not start simulations.
