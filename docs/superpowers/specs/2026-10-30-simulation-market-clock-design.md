# Shared simulation time and trading windows

Status: approved by the user on 2026-10-30, including implementation, coordinated backed-up demo reset, deployment, and live validation.

## Intent

Make the exchange behave as one persistent simulated market. Anyone can start a session; every authenticated trader can participate while it is active. All three instruments (BTC, ETH, SOL) share the same clock. When nobody is running a session, market time and prices remain frozen. Five minutes today plus five minutes two days later advances the chart by ten minutes.

## Findings from the current release

Inspected source and Oracle deployment both identify release `f0806422e587996c7ab7ea047836ce07c407a089`. All application deployments were ready during the read-only inspection.

- `SimulatorService.start` and its database constraint accept only 30, 60, or 120 seconds. Timing starts before book seeding, consuming part of the chosen duration.
- Bot admission checks the session; `OrderService.placeOrder` does not. The matching consumer also has no session boundary check.
- `OrderPlacedEvent` uses real time, and matching derives execution timestamps from it. `OHLCVManager` buckets those timestamps into calendar intervals.
- `PriceChart` defaults to 5m; a short simulation naturally yields few candles. It falls back to browser time for unrecognized live timestamps and silently discards ordering errors. Reconnection does not explicitly reload candle history.
- Market-data restart rebuilds candles from the latest 500 trades, so older history can disappear from the chart even though trades remain in PostgreSQL.
- Bots restart around the risk reference; maker tracking does not fully reconcile against actual active orders. Taker prices based on a target midpoint do not guarantee crossing the current book. These are investigation targets for sparse/flat candles, not established explanations of every observed flat candle.
- The matcher reconstructs its books from Kafka commands. Removing Kafka alone would leave orders, reservations, projections, and books inconsistent.

## Proposed user-visible rules

1. There is one global market, not one clock per user or instrument. The person clicking Start owns the session for display purposes, but all authenticated traders may buy and sell during it.
2. Offer 1, 2, 5, and 10 minutes; default to 1 minute. Reject other durations consistently in UI, API, and database. Replace the 30-second option.
3. Show both session time remaining and cumulative simulated elapsed time. Countdown and cumulative clock use authoritative server data. They stop at the session deadline; page refresh and browser clock changes must not reset them.
4. At the fresh-start maintenance operation, store one exact UTC epoch `T0` and expose it as the market's initialization time. Do not advance market time while waiting for the first session. Keep actual timestamps for auditing, JWTs, leases, and operational logs.
5. Market time equals `T0 + completed active duration + current active duration`. Pauses, setup, cleanup, maintenance, and service downtime add no market time. Normal five-minute runs add exactly 300 seconds.
6. Session states: `IDLE -> STARTING -> ACTIVE -> STOPPING -> COMPLETED` (or `FAILED`). Setup must prepare credentials, balances, and quoting plans before activating the countdown. First live quotes are submitted only after activation; readiness and fair scheduling must prevent one instrument from using the others' startup window.
7. Buy/sell and other user-initiated balance mutations such as the existing public deposit endpoint require ACTIVE status. The server enforces this even for direct API requests. Read-only access remains available. Cancellation, already-earned settlement, reservation release, and authorized internal demo provisioning remain possible when paused so cleanup cannot deadlock.
8. Proposed session-order policy: expire/cancel all remaining orders from the ending session, including users' orders, and release their unused reservations. Do not carry unfilled orders invisibly into the next run. Explain this near order entry.
9. At deadline, close admission and stop bot work immediately. A short, explicit STOPPING period may finish matching operations durably committed before the deadline and deliver their trades/settlements/cancellations. Durable matching admission is the logical execution point: a committed operation may finish after a crash or deadline, with its original market timestamp. This period adds zero market time and permits no new matching decisions. Once marked Paused/Completed, price, candles, and clock must remain unchanged. Do not discard valid delayed execution events merely to hide latency.
10. Next session resumes the last market time and last traded prices, with risk bounds respected. The same partial candle continues if the previous session stopped partway through its interval.

## Clock, boundaries, and recovery

Use PostgreSQL as the durable authority, building on the current advisory-lock singleton design. Store an epoch ID, T0, committed elapsed milliseconds, active session/fence, deadline, lease, progress checkpoints, and per-market resume prices. Make the unique live-session index a constant-expression index covering STARTING, ACTIVE, and STOPPING; the current status-expression index alone permits one row of each status.

Use database time for admission and deadlines, with periodic persisted progress (target 250ms while active) and short worker leases. Every timestamp issued to a trading decision advances a durable high-water mark. On failure, stop at the last safe checkpoint/high-water mark, never behind an issued timestamp and never ahead through unattended downtime. Recovery cannot count the same session twice. An expired lease fails closed even if the row still says ACTIVE. Expose stale/unavailable status explicitly to clients.

Add versioned simulation context to order/trade events: epoch ID, session ID, fence, simulation timestamp/elapsed time, and real event time. Clients cannot supply authoritative context. Use integer milliseconds and existing fixed-point money. Preserve existing real timestamps for operational consumers.

Gate both order admission and live matching. A command accepted before the deadline but first reaching matching afterwards is rejected and its reservation released; it must not execute in a later session. A historical replay must not reevaluate yesterday's command against today's session or wall clock. Persist each live matching admission decision and assigned market timestamp, keyed by epoch/topic/partition/offset, before applying it to the book. Replay uses those decisions. Repeating an already admitted decision is completion of the original operation, not a new admission. Store enough identity and payload integrity information to reject mismatched retries. Keep the currently disabled order modification feature disabled; reject live modify commands explicitly, since cancel-and-reinsert modification can otherwise create a fill outside the admission gate. Cancellation cannot create a new match.

The boundary protocol must wait for admitted matching work and its published outputs to finish before declaring the session frozen. Track durable publication/consumer watermarks or equivalent explicit acknowledgments for matching, order/account settlement, and market-data projections. Fixed sleeps and zero consumer lag alone are insufficient proof when an outbox still contains unpublished work. Cleanup retries are idempotent and fenced; a new session stays blocked if reconciliation is incomplete. Do not hold a database transaction across a minute of cleanup network calls.

This adds durable simulation control to the existing single-owner matching architecture; it does not introduce multiple concurrent matchers.

## Candles and bot behavior

Anchor candle windows at T0: `T0 + floor(simulatedElapsedMs / intervalMs) * intervalMs`, with half-open intervals. This makes the first five active minutes exactly five 1m windows rather than six wall-clock-aligned partial windows. The chart labels these as simulation time. Default the chart to 1m; preserve longer interval choices.

Build OHLCV only from actual executions. Order trades by simulated timestamp and stable per-instrument sequence; deduplicate by epoch/trade ID. Persist candles transactionally with durable trade insertion, or a durable projection checkpoint, so duplicate delivery and restart cannot inflate volume or lose history. REST history must read durable candle storage with bounded pagination. WebSocket delivery is an update mechanism; reconnect/refetch repairs missing updates.

At interval completion, a genuinely empty active interval may have a clearly identified carry-forward candle with zero volume and zero trades, if a previous price exists. Before the first trade, show an empty state. Never manufacture wicks or volume. Equal-price trades can legitimately produce a flat candle. No candles are emitted for idle real-world time.

Improve bots based on measured executions: reconcile maker/taker state against the exchange, quote around the previous closing price within risk limits, replenish near-touch liquidity, use actual best bid/ask for bounded crossing orders, and schedule all three instruments fairly within the global order budget. Record execution counts and rejections by instrument; a submitted HTTP order is not evidence of a fill.

## Fresh-start proposal requiring approval

Approval of this design includes a coordinated reset of disposable demo trading data, consistent with the previously permitted backed-up demo reset. Preserve users/login identities and infrastructure configuration. Reset trading orders, trades, reservations, balances/ledger history to a documented funded demo baseline, candle projections, session history, matching decision history, trading caches, and affected application Kafka topics/consumer offsets. Archive old trading audit data with the backup. Do not delete Kafka broker internals or unrelated topics.

Inventory exact tables/topics/cache prefixes and seed amounts in a reset manifest before execution. Pause all producers/consumers, capture a mutually consistent database/Kafka/offset/configuration backup, verify checksums and rehearse restoration in isolation. Then recreate only listed application streams and reset their associated state together. Seed balances through a documented ledger baseline, initialize the new epoch once, and start paused. A rollback restores matching code, PostgreSQL, Kafka, offsets, and relevant caches as one set. Partial resets remain in maintenance.

The initialization timestamp is chosen during this operation after approval, not during planning. The five-minute validation run becomes the first retained history of the new epoch.

## Acceptance

- Direct and UI order/deposit attempts outside ACTIVE fail without new balance reservations. A second user can trade in another user's active session. Concurrent starts yield exactly one global session.
- A 300-second run advances cumulative time by 300 seconds across all markets. An idle interval does not change it. A resumed run continues time and prices, including across reloads and service restarts.
- Five consecutive 1m windows per market after a healthy five-minute run; compare each candle's OHLC, volume, and trade count against stored executions. Diagnose unexpected no-trade windows; do not turn a missing market into a passing cosmetic candle test.
- The frozen snapshot remains unchanged during at least 60 real seconds after finalization. A second user's one-minute run adds exactly one minute; a simulated two-day pause in automated tests adds zero.
- Fault tests cover deadline races, delayed Kafka delivery, duplicate/reordered events, crashed workers, replay, and reservation cleanup. No lost trades, duplicate volume, negative balances, stranded reservations, or stale-session matching.
- CI/CD succeeds; verify deployed SHA, public HTTPS/API, connected chart updates, and all three markets on Oracle. If visual browser access is unavailable, report that limit separately from API/data verification.
