# Shared simulation market

The authenticated terminal shares one persistent market clock across BTC, ETH, SOL, and all traders. Supported sessions are 60, 120, 300 and 600 seconds. `POST /market-simulation/start` accepts `durationSeconds`; `GET /market-simulation/status` includes the authoritative `clock` snapshot.

The market starts at a single operator-initialized UTC timestamp. Only ACTIVE time advances it. Setup, idle time, finalization and downtime add no time. A normal five-minute run adds exactly 300000ms. Browser displays interpolate from server time with a monotonic clock and stop trading when the status is stale, the worker lease expires, or the deadline arrives.

Buy/sell and administrative public deposits require ACTIVE status. All authenticated users can trade during that session. A request may carry `sessionId` to reject delayed submissions from an older session; authoritative timestamps and fences are always assigned by the server. Cancellation, settlement, reservation release and internal demo provisioning remain available while paused.

At the deadline all session orders, including users' orders, expire. STOPPING waits for durable commands, matching outputs, consumer receipts and per-order reservation reconciliation. Work committed by the matcher before expiry may finish with its original market timestamp. New matching decisions after expiry are rejected. If cleanup fails, STOPPING blocks another session; Retry cleanup and start retries the same idempotent finalizer.

PostgreSQL owns the singleton session, short leases, fencing tokens, accumulated duration, order intents, matching decisions and canonical output journal. Replay reads recorded matching decisions rather than reevaluating the current wall clock. Live order modifications remain disabled. Per-order reservation ownership prevents one order from spending another order's funds; an orphan intent produces durable compensation.

Trade insertion and candle projection commit atomically. Windows are anchored to initialization time, not rounded calendar minutes. Candles carry epoch and revision; duplicate trades do not inflate volume, and late trades correct their own windows using elapsed time and sequence ordering. REST history reads durable storage with bounded `limit`, optional `epoch` and exclusive `before` cursor. Empty intervals stay absent; flat candles with actual equal-price executions are legitimate. Chart reconnect and finalization refetch durable history and merge revisions.

Bots begin around each instrument's persisted last close, seed levels across markets in turn, reconcile consumed orders, and replenish one bid/ask pair per turn. IOC takers use the matcher's actual best quote within the price band. A shared limiter defaults to eight orders per second. Submitted/proposed/rejected request metrics are separate from the matching engine's confirmed execution counters.

Run `./gradlew test` with Docker for PostgreSQL/Kafka integration coverage. Fast local checks use `-PexcludeIntegration`. Frontend validation is `npm test`, `npm run type-check`, `npm run lint`, and `npm run build` from `frontend`.

The event/schema transition requires the coordinated procedure in [simulation_reset_runbook.md](simulation_reset_runbook.md). Do not roll old and new trading traffic together or reset Kafka independently of trading state. The first retained live run is the controlled five-minute validation, followed by a frozen minute and another user's one-minute session.
