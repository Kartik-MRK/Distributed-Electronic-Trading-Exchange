# Simulation Market Clock Implementation Plan

> **For agentic workers:** Use superpowers:executing-plans to implement this plan task by task after user approval. Steps use checkbox syntax for tracking.

**Goal:** Enforce shared timed trading sessions and retain continuous chart history that advances only during simulation.

**Architecture:** PostgreSQL owns the shared epoch, session clock, fencing, and matching-admission decisions. Versioned events carry simulated and real time separately; durable candle projections use simulated time. The existing single matcher and Kafka replay remain, with recorded admission decisions making replay independent of the current clock.

**Tech Stack:** Existing Java/Spring Boot, PostgreSQL/Flyway, Kafka, Redis, Next.js/React/TypeScript, lightweight-charts, Gradle, Helm/K3s, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-30-simulation-market-clock-design.md` (read first).

## Global constraints

- Approved by the user on 2026-10-30; implement and validate the complete plan.
- One global session and clock for BTC, ETH, SOL; all authenticated users can trade during it.
- Durations: 60, 120, 300, 600 seconds; default 60. Millisecond clock precision; fixed-point monetary values.
- Preserve real time for security/audit/leases. Anchor simulated candles to the exact persisted T0.
- Fail closed on unavailable control state, expired sessions/leases, and unknown epochs. Cleanup/settlement must still progress.
- Session orders expire at session end. Delayed completion is shown as STOPPING; it cannot advance the clock or admit new matches.
- Fresh-start reset preserves login identities and resets demo trading balances/history only after a verified backup.
- Implement in an isolated worktree based on current origin/master. The main checkout is behind the reviewed live release; do not implement against that old snapshot.

## Review focus

1. Commands admitted just before expiry but consumed afterwards: reject without a fill; release reservation (Task 2).
2. Worker/matcher crashes between clock checkpoint, admission, publication, and acknowledgment: no rewind, duplicate duration, or replay disagreement (Tasks 1–2).
3. Two-day idle interval, another user, browser refresh, and stale status response: same shared frozen time and correct resumed interval (Tasks 1, 4, 6).
4. Late/duplicate trades and market-data restart after more than 500 trades: durable OHLCV remains exact (Task 3).
5. One instrument starved by quoting or request limits: detect failed execution coverage rather than accepting fabricated/empty candles (Tasks 4, 6).

## Task 1: Persist the global clock and session lifecycle

**Modify:**
- `services/simulator/src/main/java/com/dete/simulator/service/{SimulatorService,SimulationSession}.java`
- `services/simulator/src/main/java/com/dete/simulator/controller/SimulatorController.java`
- `services/simulator/src/main/java/com/dete/simulator/config/SimulatorProperties.java`
- `services/simulator/src/main/resources/application.yml`

**Create:**
- `services/simulator/src/main/resources/db/migration/V2__simulation_market_clock.sql`
- `libs/common-domain/src/main/java/com/dete/common/domain/simulation/{MarketClockSnapshot,SimulationContext}.java`
- `libs/simulation-control/` with Gradle registration in `settings.gradle.kts`, JDBC-backed `com.dete.simulation.control.SimulationControlRepository`, and module integration tests. Follow existing library dependency conventions.

**Interfaces:** `MarketClockSnapshot` carries epochId, initializedAt, serverNow, sessionId, ownerUserId, status, fence, elapsedMs, remainingMs, deadline, leaseUntil, and tradingAllowed. `SimulationContext` carries epochId, sessionId, fence, marketTime, elapsedMs, and realTime. Repository operations: `snapshot()`, `claim(UUID userId, int durationSeconds)`, `activate(UUID sessionId, long fence)`, `checkpoint(UUID sessionId, long fence)`, `beginStopping(UUID sessionId, long fence, String reason)`, and `complete(UUID sessionId, long fence)` with explicit stale-owner errors. Schema migrations stay simulator-owned; library consumers do not race to migrate it.

- [x] Add clock/session tests to `services/simulator/src/test/java/com/dete/simulator/SimulationSessionConcurrencyTest.java` and new `SimulationClockTest.java`: 300000ms active + two days idle + 300000ms active = 600000ms; two simultaneous starts produce one live session; lease expiry denies trading; recovery never moves behind a persisted decision timestamp.
- [x] Add duration/state migration tests, including STARTING versus ACTIVE concurrent rows and recovery repeated twice without duplicate elapsed time.
- [x] Implement persisted epoch, constant-expression singleton index, fenced state transitions, bounded leases/checkpoints, and start preparation. Separate short DB transitions from cleanup network calls.
- [x] Run simulator/control module tests with PostgreSQL Testcontainers; require zero skips for concurrency tests. Commit the clock/lifecycle change.

## Task 2: Enforce trading windows through matching and settlement

**Modify:**
- `services/order/src/main/java/com/dete/order/service/OrderService.java`, order model/repository/outbox, and `controller/GlobalExceptionHandler.java`
- `services/account/src/main/java/com/dete/account/controller/AccountController.java` and account mutation service entry points
- `libs/common-events/src/main/java/com/dete/common/events/order/OrderPlacedEvent.java`
- `libs/common-events/src/main/java/com/dete/common/events/trade/TradeExecutedEvent.java`
- `services/matching-engine/src/main/java/com/dete/matching/consumer/OrderCommandConsumer.java`
- `services/matching-engine/src/main/java/com/dete/matching/replay/KafkaReplayService.java`
- `services/matching-engine/src/main/java/com/dete/matching/engine/{OrderBook.java,model/BookOrder.java}`
- affected service Gradle dependencies, application configuration, Helm deployment templates, and local Compose configuration

**Create:** `services/simulator/src/main/resources/db/migration/V3__simulation_matching_admissions.sql`; new simulation-gate integration tests in order/account/matching-engine. New order migration records epoch/session association and durable cleanup/compensation work.

**Interfaces:** `requireActiveMutation(): SimulationContext`; `decideMatching(String topic, int partition, long offset, SimulationContext submitted, String payloadHash): MatchingAdmission`. Admission contains immutable allow/reject outcome, reason, and assigned market context. Persist it before book mutation and reuse it during replay. HTTP business rejection is 409 with code `SIMULATION_NOT_ACTIVE`; unavailable authority is 503. Idempotent retries of an existing order return the original result without creating a new mutation.

- [ ] Test paused direct API submission, public deposit, and second-user trading. Assert a denied request creates no order/reservation; allow cancellations and settlement outside ACTIVE.
- [ ] Test deadline crossing before reservation and before final outbox admission. Persist/retry compensation for any remote reservation/risk slot already acquired; do not rely on an in-memory catch block.
- [ ] Test delayed commands, stale fences/epochs, publication retry, and matcher restart after durable decision but before acknowledgment. Replay must reconstruct the identical book and outputs while the market is paused. Assert a decision persisted before expiry can finish during STOPPING with its original timestamp, but a command lacking that decision cannot. Test direct `OrderModifyCommand` injection is rejected without a fill; preserve the existing disabled modification policy at both API and matcher.
- [ ] Implement live admission plus matching decisions and additive event versioning. Retain real event timestamps and attach simulation context explicitly. Update all constructors, serializers, consumers, and replay fixtures together; legacy pre-reset archives are not admitted into the new epoch.
- [ ] Add idempotent session-wide order expiry and acknowledged drain/finalization. Include unpublished outbox work in the barrier. Keep STOPPING on unresolved cleanup and expose a reason; recovery continues cleanup without reopening trading.
- [ ] Run affected library/order/account/matching tests including Kafka/PostgreSQL integration; compare reservation/ledger invariants after expiry. Commit the boundary change.

## Task 3: Make candle history durable and use simulated time

**Modify:** market-data `service/{MarketDataService,OHLCVManager}.java`, `repository/MarketDataTradeRepository.java`, `model/{Candle,MarketDataTradeRecord}.java`, `controller/MarketDataController.java`, and trade consumer/broadcaster.

**Create:** `services/market-data/src/main/resources/db/migration/V2__simulation_trade_time_and_candles.sql`; `repository/CandleRepository.java` and projection integration tests.

**Interfaces:** `onTrade` consumes explicit simulation context and sequence. Candle storage key is epoch/instrument/interval/openTime; history queries accept epoch, instrument, interval, cursor, and bounded limit. Candle payload adds epoch, monotonic revision, and synthetic/empty indicator. Prices and quantities retain current scale.

- [ ] Test T0 at a non-minute boundary: five full active minutes produce exactly five 1m windows; paused wall time produces none; a partial window continues after resume.
- [ ] Test two same-time trades ordered by sequence, out-of-order delivery, duplicates, equal-price legitimate candles, empty intervals, and restart beyond 500 trades. Assert exact open/high/low/close/volume/count against executions.
- [ ] Persist trade deduplication and candle updates atomically, plus finalization watermark. Read history from durable storage; bound the memory cache independently of history retention. Publish revisions only after commit; repair missed publication through refetch.
- [ ] Run market-data unit/integration suites. Commit candle persistence and clock projection.

## Task 4: Fix bot liquidity and price continuity

**Modify:** simulator `bot/InstrumentBot.java`, `service/{SimulatorService,BotAccountManager}.java`, `client/ExchangeRestClient.java`, configuration and metrics.

**Interfaces:** persist per-instrument last traded price at finalization; bot startup consumes that price plus the current risk reference. Extend the exchange client to retrieve authoritative best bid/ask. Keep one bounded global order limiter and use fair per-market scheduling.

- [ ] Add deterministic bot tests with controlled random values and exchange responses: consumed maker orders disappear from tracked state, cancellations settle before replenishment, crossing prices reach actual best quotes within risk bands, and all markets get opportunities under throttling.
- [ ] Measure proposed versus accepted versus executed orders and rejection reasons; correct the execution metrics to count confirmed fills. Avoid long sequential quote refreshes starving later instruments.
- [ ] Resume each market from its stored close; prepare credentials/funding before activation. Keep initial seeding inside ACTIVE and prioritize useful near-touch liquidity across all three instruments.
- [ ] Run simulator tests and isolated three-market integration; assert actual executions in every full minute of a healthy five-minute run. Commit bot changes.

## Task 5: Share simulation state across the UI

**Modify:** `frontend/src/components/simulation/MarketSimulationControl.tsx`, `components/orderentry/OrderEntry.tsx`, `components/chart/PriceChart.tsx`, API/type definitions, terminal composition, and relevant styles.

**Create:** `frontend/src/hooks/useSimulationClock.ts` and a single shared provider in the terminal; tests for clock and chart data merging using existing frontend test conventions.

**Interfaces:** expose the authoritative snapshot, estimated display time, tradingAllowed, connection freshness, and refresh action. A browser monotonic clock interpolates from serverNow only while ACTIVE and within the deadline/lease; missing or stale status disables mutation controls.

- [ ] Read repository frontend instructions and installed Next.js documentation before editing.
- [ ] Test two viewers, refresh, browser clock skew, status fetch failure, delayed out-of-order status, and deadline reached before the next poll. Clock freezes; controls deny trading until authority confirms ACTIVE.
- [ ] Add 1/2/5/10-minute durations, cumulative elapsed market time, countdown, initialization timestamp, clear paused/finalizing/unavailable states, and session-order expiry copy.
- [ ] Default chart to 1m and label simulation time. Remove Date.now timestamp fallback and silent dropping of valid historical corrections. Merge REST/live data by epoch/window/revision; refetch on reconnect and session finalization. Preserve chart navigation while data updates.
- [ ] Run frontend tests, type-check, lint, build, and browser verification when available. Commit UI changes.

## Task 6: Rehearse reset, deploy, and prove the result on Oracle

**Modify:** `scripts/smoke_test.py`, `scripts/test_smoke_test.py`, `scripts/test_exchange_live.py`, `.github/workflows/cd.yml` as needed for maintenance-aware rollout and session-aware checks; `docs/market_simulation.md`.

**Create:** `scripts/reset_demo_market.py`, `scripts/verify_simulation_clock.py`, reset manifest/runbook, and `docs/simulation_clock_validation_2026-10-30.md` (use actual execution date if later).

- [ ] Inventory exact reset resources and demo seed balances. Make the reset tool dry-run by default with explicit namespace, epoch, backup manifest, and maintenance preconditions. Refuse unlisted resources or active producers. Preserve authentication/account identities; seed reconciled balances and ledger entries.
- [ ] Back up and rehearse restore/reset on an isolated copy. Verify database, Kafka, offset, cache, and deployed-image compatibility. Do not run rolling mixed-version trade traffic through the event-schema change.
- [ ] Update existing trading smoke checks to explicitly start a session and wait for finalization; ensure tests cannot race the controlled live run. Complete full CI, integration, migration, and Helm checks. Push the reviewed commits and monitor CI/CD; leave live trading closed through the coordinated reset.
- [ ] After backup verification, reset approved demo trading state and affected application Kafka streams/offsets as one maintenance operation. Initialize T0 exactly once. Deploy matching service configurations/migrations coherently and verify all services are ready with the intended SHA.
- [ ] Run the first five-minute simulation on Oracle. Capture per-market trades, clock snapshots, candle revisions, request rejections, and order/ledger reconciliation. Compare all five 1m candles per market to executions; check longer interval aggregation too. Treat an unexpected no-trade full minute as a validation failure requiring diagnosis.
- [ ] Wait 60 real seconds after finalization and compare frozen clock/price/candles. Submit paused orders/deposits and confirm rejection without reservations. Start one minute from a second account; assert cumulative time is 360000ms with no idle gap and prices resume from the prior close.
- [ ] Verify cold history retrieval, reconnect, and safe service restart. Perform crash/deadline fault tests in isolation, not by disrupting unrelated live users. Record actual initial/final timestamps, exact OHLCV comparisons, deployed SHA, CI/CD links, and any visual-test limitation.
- [ ] Report results and retain backups. If a coordinated stage fails, keep maintenance enabled and restore the whole compatible state set rather than rolling back only application images.

## Approval handoff

The proposed product choices needing review are: shared trading for all users; 1/2/5/10-minute durations; expiry of unfilled session orders; cancellation/settlement allowed while paused; brief finalization for already executed work; exact T0-relative candle windows; and a backed-up demo trading reset that preserves logins but replaces balances and trading history. Execution will be native in the current session with a review before deployment. Implementation and live operations begin only after the user approves the plan.
