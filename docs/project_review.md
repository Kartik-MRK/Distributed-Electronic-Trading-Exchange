# Project sanity audit ? 2026-10-23

## Verdict and scope

The project builds and its non-container test suite passes, but it is **not ready for real funds or a production security boundary**. This review found real accounting, lifecycle, data-contract, and misleading-demo behavior. The fixes below are in the working tree. Nothing was deployed, pushed, or tested against the public exchange.

The repository contains 500 tracked files, nine services, four shared libraries, a benchmark module, a Next.js frontend, and deployment/operations configuration. Review covered the main execution paths and cross-service contracts, supported by repository-wide searches and automated checks. This is not a claim that every line or every failure schedule has been exhaustively verified.

## Plan and completion

1. Inventory modules, repository instructions, build configuration, CI, tests, and documentation ? done.
2. Run baseline checks and trace authentication ? order ? risk ? reservation ? matching ? settlement ? market data ? done through static inspection and local tests.
3. Review frontend fallback behavior, amount conversions, subscription payloads, account data, and error handling ? done; fixes implemented.
4. Review infrastructure, scripts, operational claims, and recovery paths ? done; findings below.
5. Add targeted regressions, rebuild, and record remaining risks ? done for checks supported locally. Docker/Kubernetes scenarios remain unverified.

## Fixes implemented

| Area | Defect and resulting behavior |
| --- | --- |
| Settlement | Ignored debit row counts could credit both parties without taking their reserved assets. Both required debits must now succeed before any credits; failures propagate and the Spring transaction rolls back. Added unit failure cases and a PostgreSQL rollback integration test. |
| Currency accounting | Reservation, release, and settlement silently substituted USDT for USD. These are distinct balances; operations now use the instrument's actual asset. Frontend instruments and trading labels now use USD, and balance responses no longer duplicate USD into USDT. |
| Settlement consumer | Errors were logged and swallowed, consuming failed trades. Failures now propagate to an explicitly configured retry handler. Persistent failures stall the partition and require operator repair; they are no longer reported as processed. |
| Account events | Outbox JSON strings were passed through JsonSerializer, double-encoding them. The producer now uses StringSerializer. |
| Outbox locks | Both scheduled publishers fetched `FOR UPDATE SKIP LOCKED` records without a surrounding transaction. Locks now span fetch, publish, and mark-published. Account publication stops the batch on failure. This remains at-least-once publication. |
| Fill lifecycle | The publisher emitted final fills before partial fills from the same multi-maker match. Partial fills now precede final fills; cumulative filled quantity is derived from original minus remaining. SQL guards prevent terminal-state regression, acceptance regression, and older remaining quantities. |
| Cancellation/rejection | Fund-release failures were swallowed. They now fail the order transaction and reach error handling. Already terminal orders are not cancelled again; rejection does not overwrite an accepted or terminal order. |
| Order modification | The endpoint changed matching terms without adjusting reservations or persisting replacement terms. It now rejects the unsafe operation with a clear error. Cancel, wait for confirmation, then submit a new order. Safe atomic replacement remains future work. |
| Idempotency | A reused global key could return another account's order or a different request. Both cache and database hits validate account and order terms. New-order cache publication occurs after database commit. |
| Dead-letter handling | Source events were acknowledged even when DLQ publication failed. The consumer now waits for broker acknowledgement and propagates DLQ failures. |
| Market buys | Matching ignored the supplied reservation price. Market buys now require a positive cap and cannot execute above it; unmatched quantity is cancelled. |
| Risk | Self-trade checks compared prices across different instruments; resting orders now carry the instrument. Legacy cancellation payloads with nonzero remaining quantity now release their risk slot. |
| Gateway | Both ADMIN and ROLE_ADMIN are recognized. Deposits through the gateway require admin privileges; non-status simulator paths are no longer classified as public. Direct service security still needs work below. |
| Metrics | The dashboard endpoint returned invented throughput, latency, lag, memory, and connection values. Uncollected measurements now return null/empty collections. Health checks are real; missing health status is UNKNOWN. Dashboard handles missing metrics explicitly. |
| Authentication UI | Removed fabricated successful demo/guest login sessions after backend failure. Return URLs are restricted to local paths. |
| Trading UI | Removed generated balances, order fills, cancellation success, tape trades, chart candles, order-book movement, and latency. Chart/tape/book bootstrap from API data. Failed cancellations show an error; successful submission is labelled as a request awaiting confirmation. |
| Frontend contracts | Fixed sub-unit fixed-point conversions; added exact decimal-to-scaled-integer conversion with precision limits. Personal settlement responses now map to the UI shape and account side. Unknown instruments fail rather than silently becoming BTC. |
| Account updates | WebSocket deltas were treated as complete orders, overwriting fields with defaults. They now trigger authoritative REST refreshes. Polling also refreshes asynchronous fills/cancellations and repairs missed notifications. |
| CI/CD | CI no longer implicitly skips integrations based on the CI environment variable. Fast tests use an explicit flag; a separate Docker job runs the full suite. Frontend tests/build were added. Automatic CD waits for successful CI and deploys its exact SHA. Explicit manual dispatch remains available. |
| Smoke test | Removed direct-service fallbacks that masked gateway failures. Health/counterparty/sell/book/balance failures now fail the verdict, and a submitted buy must have a corresponding execution and account settlement. The script was syntax-checked, not run live. |
| Documentation | Corrected README frontend versions, local links, missing license link, currency/performance/guarantee wording, and replaced the previous promotional review with this evidence-based audit. |

## Validation

- **228 Java tests pass, zero failures**, including unit, architecture, and property tests. Container integration tests were excluded explicitly with `-PexcludeIntegration`.
- Root Spotless checks pass. Changed services were formatted using their configured formatter.
- Integration test sources compile, including new transaction-rollback and state-regression tests. **Compilation is not evidence that those integration tests pass.**
- Frontend API contract regressions: **5 pass**; cover fractional values, precision rejection, distinct currencies, personal trade mapping, and cancellation errors.
- TypeScript, ESLint, and Next.js production build checked locally; final command outcomes are recorded in the accompanying review response.
- Python scripts parse; Compose and GitHub Actions YAML parse. YAML parsing does not validate GitHub execution semantics or Kubernetes resources.
- No Docker or Helm executable was available on this machine. No full exchange startup, Kafka failure/rebalance scenario, PostgreSQL concurrency integration run, Helm rendering, live email delivery, or live deployment was exercised.
- Benchmarks were not rerun; historical numbers are not newly validated evidence.

Useful commands:

```powershell
.\gradlew.bat test spotlessCheck -PexcludeIntegration
.\gradlew.bat bootJar
cd frontend
npm test
npm run type-check
npm run lint
npm run build
```

With Docker installed, run the full `gradlew test` suite without the exclusion flag. CI now attempts this explicitly and may reveal pre-existing integration failures previously hidden by the CI shortcut.

## Remaining findings, ordered by severity

### P0 ? Recovery can lose or duplicate trading effects

`services/matching-engine/.../consumer/OrderCommandConsumer.java` acknowledges failed commands and acknowledges skipped commands during replay. In-memory state changes before output publication, output topics are not committed atomically, and there is no durable processed-command/result store. `KafkaReplayService` starts after application readiness, clears books, replays beyond an initially captured watermark if traffic continues, swallows replay failures, and does not coordinate resumption with live consumer offsets. Startup replay is disabled in application.yml. Trade IDs/timestamps are random on replay.

Required work: define one durable ownership/checkpoint protocol, stable execution identities, atomic output/offset handling, partition assignment behavior, and fail/restart tests at each publication boundary. Merely turning replay on or removing an acknowledgement is insufficient.

### P0 ? Reservations are not owned and reconciled per order

`OrderService.placeOrder` reserves remotely before its database transaction commits. A database failure or concurrent duplicate request can leave orphaned reservations. Account balances track aggregate reserved amounts, and release/debit queries do not verify that the order owns the funds. Buy price improvement leaves excess quote currency reserved after fills. Release and settlement also race across independent topics. Repeated reservation/release calls collide with ledger uniqueness rather than returning a consistent idempotent result.

Required work: durable per-order reservation records, idempotent reserve/release/settle operations, a reservation saga/outbox, price-improvement release accounting, deterministic lock order, and reconciliation tests. The new debit checks prevent one money-creation path but do not solve this architecture.

### P0 ? Default signing key and deployment wiring

`libs/common-security/.../RsaKeyUtils.java` contains a shared default private/public signing pair. `JwtKeyProvider` and validators use it when explicit keys are absent. Helm injects `JWT_SECRET`, while the signing provider expects `auth.jwt.private-key` and `auth.jwt.public-key`; validators expect `security.jwt.public-key`. Repository defaults therefore do not establish a safe production configuration.

Required work: provision a new key pair, wire matching private/public configuration across services, fail startup when production keys are absent, and rotate any deployed default. No deployed-key state was inspected and no secrets were rotated by this review.

### P1 ? Private APIs and WebSocket channels need authorization

`AccountController.getBalancesById` does not compare the requested account with the authenticated principal. `WebSocketConfig` accepts anonymous STOMP subscriptions; account-specific `/topic/orders.<id>` destinations have no ownership checks. Market trade payloads also expose account identifiers. Internal deposit/reservation APIs rely on network trust. The chart's NetworkPolicy admits all namespaces via an empty namespace selector.

Required work: account ownership/admin checks, authenticated STOMP CONNECT and destination authorization, sanitized public trade DTOs, and explicit service-to-service authentication/network restrictions. Gateway deposit protection alone is not a complete fix.

### P1 ? Risk reservations can leak or race

Risk evaluation increments open-order state before account reservation and order persistence succeed. Failed placement does not compensate it. Check-and-increment and duplicate evaluation are not atomic; state is lost on restart. Cross-instrument comparison and cancellation recognition are fixed, but consistent risk-state ownership is not.

### P1 ? Market-data projections mix incompatible ordering models

The read model consumes commands, lifecycle events, trades, and snapshots on independent topics. It can display submitted/rejected/non-resting orders before matching confirms them. Snapshot resync clears the per-order index and compares engine sequence numbers against a locally incremented counter. Rejected orders are not removed from depth. OHLCVManager adds older-window trades to the current candle. Trade persistence failures are swallowed, and duplicate executions can inflate candles/tape.

Required work: choose authoritative sequenced snapshots or sequenced deltas, deduplicate trades, repair rejection/removal semantics, and define late-trade candle behavior. The frontend fixes faithfully display the supplied data; they do not certify backend depth accuracy.

### P1 ? Auth failure and token lifecycle gaps

OTP email delivery errors can be swallowed while registration says the message was sent. Refresh-token reuse revokes tokens and then throws inside a transaction, so that revocation can roll back; concurrent refresh operations are not serialized. Welcome funding is best effort and deposits both USD and USDT separately. Provisioning failures need durable retry and a truthful user-visible state.

### P2 ? Operational/demo limitations

- Simulator API calls exist in the UI, but the gateway route configuration lacks a simulator route. The ingress also does not solve the `/admin/audit` collision between the UI page and backend API. Define distinct page/API paths and test the deployed ingress.
- The default smoke script uses installation-specific cluster IPs. Use explicit environment URLs or stable cluster DNS. Its matching/settlement check is stronger now, but shared demo accounts make it unsuitable for exact isolated balance-delta assertions.
- Dashboard telemetry is unavailable until real Prometheus queries are implemented. Existing metric objects/gauges also need their production call sites verified; a metric unit test alone proves only the meter wrapper.
- Replay renders executions with illustrative depth, not a reconstructed historical order book. Automatic synthetic history was removed; the depth label remains explicit.
- Reference order-entry prices are configured defaults, not current quotes. Token refresh is not wired into the browser session. Account REST refresh errors retain last data; a dedicated stale/error indicator is still desirable.
- Cancellation is not O(1): `PriceLevel` uses `ArrayDeque.remove`. Some comments, benchmark labels, archived build plans, and resume documents still overstate guarantees. Audit storage is append-only SQL, not a cryptographic hash chain. Historical benchmarks require reproducible reruns before reuse as claims.

## On ?AI slop?

Authorship cannot be established from coding style. The concrete issues here were automatic fake-success paths, hard-coded telemetry, synthetic market data blended with real flows, tests that only verified calls, inflated guarantees, and stale planning prose presented as implementation evidence. Those are actionable quality problems regardless of who wrote them. The most harmful examples were removed; the remaining architectural work is listed rather than hidden behind a passing build.
