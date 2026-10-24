# Project and VPS audit — 2026-10-24

## Outcome

The working tree contains fixes for confirmed security, retry, concurrency, recovery, and market-data defects. **The running exchange has not been upgraded or repaired. Do not deploy this patch over its current data without the recovery work below.** The project remains a simulated-funds demonstration, not a production exchange.

This review continues [the earlier source audit](project_review.md). It examines the main authentication → order → risk → reservation → matching → settlement → market-data paths, frontend contracts, deployment configuration, and the actual Oracle VPS. It is not an exhaustive proof of every execution schedule or every line of code.

Local baseline: `f70c23b`. VPS checkout: `/home/ubuntu/dete`, `b78d33c`, with existing local script/benchmark changes. Those changes were preserved. Integration tests run in a separate `/tmp/dete-review-20261024` checkout with Testcontainers-managed databases and brokers. No live orders, deposits, cancellations, key rotations, database repairs, deployment restarts, commits, or pushes were performed.

## Live findings

SSH access works. Nginx listens on loopback port 80 and the site is reached through the existing Cloudflare tunnel. K3s application pods were running, but running pods and successful HTTP responses do not establish trading correctness.

| Observation at inspection time | Evidence and implication |
| --- | --- |
| Database active orders: **1,057**; matching books: **12** | Database: BTC 578 accepted + 17 partial, ETH 62 accepted, SOL 397 accepted + 3 partial. Matching stats: BTC 9, ETH 3, SOL 0. These are separate read-only observations, not one atomic cross-service snapshot, but the discrepancy is substantial. Existing deployments had been running approximately 45 hours. |
| `order.commands` partition 0 begins at offset **150** | Kafka earliest-offset query. Other partitions began at 0. Complete reconstruction from offset zero is already impossible from the retained command log alone. Increasing retention now cannot recover deleted records. |
| Balance totals disagree with ledger-derived totals | [Read-only audit SQL](../scripts/audit_runtime.sql), repeatable-read transaction; aggregate mismatches below. This establishes inconsistency, not its historical cause or a justified repair amount. |
| Live JWT public key matches the repository development key | Compared the live public JWKS modulus with the repository's default public key. No private key or user credentials were printed. Because the corresponding development private key is public in source, the deployed signing identity must be rotated. |
| Pending outbox rows were zero for order and account | This does not prove all consumer effects were applied, or that there are no failed/dead-letter events. |

`net_drift` means stored `(available + reserved)` minus deposits/credits minus withdrawals/debits/fees in the ledger, aggregated only over mismatched account/asset rows. Amounts below remain in the database's integer scale (100,000,000 units per asset unit).

| Asset | Mismatched account/asset rows | Net drift, scaled integer |
| --- | ---: | ---: |
| USDT | 4 | -3,432,071,526,950 |
| BTC | 1 | 9,681,500,000 |
| USD | 1 | 98,201,915,000,000 |

## Changes implemented in the working tree

### Authentication and authorization

- Missing or shared development RSA keys now fail normal startup. Tests opt into development keys explicitly. Auth verifies that configured private/public keys form a pair. Helm and Compose now wire the actual RSA properties; Helm expects an externally provisioned `dete-jwt-keys` Secret with `private-key` and `public-key` entries.
- Authorities normalize both `ADMIN` and `ROLE_ADMIN` once. Account-by-ID reads require owner or admin. Direct public deposit/reconciliation endpoints and order DLQ operations require admin authorization.
- STOMP CONNECT validates supplied JWTs. Subscriptions allow only exact public market topics or the authenticated account's private order topic; wildcards, other-account topics, and client SEND frames are denied. Public tape/replay/WebSocket trade DTOs omit private account and order identifiers.
- Frontend connections send the current token, reconnect on authentication changes, subscribe only to the current account's private topic, and ignore callbacks from retired clients. Explicit disconnect cancels pending authentication reconnects.
- Refresh rotation/reuse/logout serialize on the owning user row. Reuse revocation now commits even when authentication fails. OTP delivery reports failure when every configured provider fails or none is configured, and escapes the email greeting.
- Welcome-fund deposits use stable operation references. They use the existing internal-account trust boundary; this is **not** service-to-service authentication.

### Accounting, order placement, and risk

- All account balance/ledger mutations lock account rows in a consistent order. Settlement locks both accounts in that order; this removes the opposite-buyer/seller lock inversion and serializes account ledger sequence allocation.
- Repeated deposits, reservations, and releases return without repeating effects when reference and terms match. Reusing a reference with different terms fails explicitly. Duplicate settlement checks occur inside the account locks.
- Order placement acquires a PostgreSQL transaction advisory lock on its idempotency key before risk evaluation or remote reservation. Concurrent same-key requests therefore share the committed result instead of creating separate reservations. Invalid or rounded-to-zero reservation amounts fail before risk admission.
- Risk check-and-admit is synchronized; duplicate admission is idempotent for matching terms, changed terms are rejected, and repeated close events cannot decrement another order's slot. Numeric/identity validation was strengthened. Risk outages now count as circuit-breaker failures instead of being treated as ignored business rejections.
- DLQ inspection bounds the requested page size and restricts topic names; replay waits for broker acknowledgement before reporting success.

### Matching and recovery

- Failed or paused commands are never acknowledged. A successfully calculated result is retained until publication and acknowledgement complete, so a publication retry does not run matching again. Failure before capturing a complete result disables processing until recovery, because partial in-memory mutation cannot safely be guessed away.
- Order-ID deduplication covers filled/cancelled orders as well as resting orders. Trade IDs derive deterministically from the participating orders and fill progress; execution timestamps derive from the command. Duplicate command delivery no longer emits a spurious rejection that could release active-order funds.
- Startup reconstructs only through captured committed offsets before starting the live listener. Replay manually assigns partitions, never commits replay offsets, rejects missing history, and fails closed on malformed input. Recovery health and snapshot publication reflect the recovery state.
- The chart requires one matching replica and uses `Recreate`. The command topic retains the full log until a real checkpoint protocol exists. This is deliberately **single-owner recovery**, not horizontal failover or atomic Kafka output/offset transactions. The current live log fails the new completeness check.
- REST statistics read on the book's owning thread instead of iterating its price trees concurrently with matching.

### Market data and tests

- Only matching-engine snapshots supply depth. Submitted commands and unsequenced lifecycle events no longer mutate it.
- Durable trade insertion precedes tape/candle updates. Database failures propagate to Kafka retry handling; duplicate trade IDs do not inflate volume. The recent tape/candle projection rebuilds from a bounded durable history after restart.
- Late trades update the appropriate retained candle, including event-time open/close, instead of contaminating the current candle. Last-price updates do not move backward in event time. API limits and replay windows are validated.
- Shared Testcontainers now live for the test worker rather than being stopped after the first class while Spring still holds cached datasource contexts. Linux shell line endings are specified in `.gitattributes`.
- Added regressions for authorization, key configuration, token reuse, simultaneous accounting/order/risk requests, publication retries, incomplete/malformed replay, duplicate trades, late candles, and browser account switching. Fixed an existing DLQ integration assertion that treated map-shaped messages as strings.

## Verification

**299 Java tests pass, with zero failures, errors, or skipped tests**, using Java 21 and the repository's Gradle 9.7.1 wrapper on the VPS. This includes the container integrations. The first full patched run completed in 9m 29s; an incremental full-suite invocation after the final auth/order concurrency tests and statistics change completed successfully in 3m 48s. The final XML totals are: common-domain 21, common-security 2, common-test 1, account 27, audit 17, auth 45, gateway 32, market-data 28, matching-engine 34, order 49, risk 31, simulator 12.

```bash
# In /tmp/dete-review-20261024, never in the deployed checkout:
bash ./gradlew test --continue --no-daemon --max-workers=2
```

- Local fast-suite XML: **249 tests**, zero failures; container tests excluded explicitly with `-PexcludeIntegration`.
- All service executable JARs build; final Spotless and `git diff --check` pass.
- Frontend: **7 tests pass**, including account-switch/reconnect regressions. TypeScript, ESLint, and the Next.js production build pass.
- Helm lint and template rendering pass. A two-replica matching configuration is deliberately rejected. Both infrastructure-only and full Compose configurations render; full Compose was checked with placeholder key strings. Rendering is not a key-validity or application-startup test.
- Final read-only operational check: Nginx root HTTP **200**, application pods still running without restarts during this work, deployed checkout's pre-existing edits preserved.

Reproduction logs remain at `/tmp/dete-review-20261024-current.log`, `/tmp/dete-review-20261024-final.log`, and `/tmp/dete-review-test-summary.json` on the VPS. Local build logs are ignored `audit-*.log` files. The isolated tests do not constitute an all-services production end-to-end test: some service integrations mock remote peers. No live email delivery, public trading mutation, arbitrary crash/rebalance matrix, or new benchmark was exercised.

## Remaining blockers and limits

1. **P0 — Existing data needs recovery, not guessed balances.** The missing commands, order/book divergence, and ledger drift require a frozen, backed-up reconciliation exercise. Do not silently delete active orders, manufacture missing commands, adjust balances, or replay into live consumers. Source fixes cannot retroactively establish the correct historical balances.
2. **P0 — Reservations remain aggregate rather than owned per order.** These fixes serialize retries; they do not add a durable reservation saga. Remote reservation can succeed before order commit fails. Cancellation and settlement consume independent topics. Settlement can debit another order's aggregate reserved funds, and buy price improvement can leave excess quote funds reserved. Required design: durable per-order reservation rows with remaining/spent/released totals, idempotent settlement allocation, terminal close reconciliation, and durable placement compensation. Test failures after remote success, duplicate deliveries, and every cancel/fill ordering before enabling real funds.
3. **P0 — Signing-key rotation is not performed.** The new source fails closed, but the deployed service still uses the public development identity. New keys must be provisioned and validators/auth rolled out coherently; existing access tokens must cease being accepted and refresh sessions must be invalidated under a planned migration. Do not add real private keys to Git or Helm values.
4. **P1 — Recovery has deployment constraints.** There is no durable checkpoint, ownership fencing, multi-replica rebalance protocol, or atomic output-topic/offset commit. Recovery depends on a complete log and unchanged per-instrument partitioning. IDs from the old random-ID implementation must be reconciled at the migration boundary. Retained logs and the processed-order set grow without bound; monitor storage/memory. Unit fault injection and integration replay do not establish arbitrary crash/rebalance correctness.
5. **P1 — Internal service trust remains.** Internal account mutation APIs, risk RPC, and matching operations rely on network placement. Existing broad network policy and direct Compose service ports need restriction and service authentication. WebSocket authorization checks expiry when subscribing; an already-open subscription is not forcibly disconnected at JWT expiry. Access-token revocation and automatic browser refresh are not implemented.
6. **P1 — Risk admission/provisioning still need durable ownership.** Risk state is in memory and placement failure does not reliably compensate admission. Welcome funding remains best effort across service transactions. Restart reconciliation and an explicit pending/failed provisioning state are needed.
7. **P1 — Market projections have bounded recovery.** Only the most recent configured tape capacity is rebuilt, not complete historical candles. A process failure between durable trade insertion and projection publication needs restart reconstruction; broadcasts are not transactional. Private order mappings are also in memory. Snapshot delivery remains asynchronous. REST polling repairs some missed UI notifications but does not solve these durability gaps.
8. **P2 — Other known demo limitations remain.** Unsafe order modification is disabled; simulator gateway routing and the UI/API `/admin/audit` collision remain unresolved; dashboard measurements remain unavailable where no real collector exists. Existing benchmark claims were not rerun. See the earlier audit for these details.

## Concrete recovery and rollout sequence

1. Schedule an ingestion pause; stop simulator/order submissions under an agreed maintenance window. Capture PostgreSQL backup, Kafka data and offsets, current image digests/configuration, and the existing VPS working-tree changes. Preserve originals.
2. Restore copies into an isolated environment. Correlate orders, retained commands, executions, settlements, ledger entries, reservations, DLQs, and consumer offsets. Establish a reviewed order/book checkpoint and an auditable repair ledger. The aggregate SQL is a detector, not a repair script.
3. Choose preservation/reconstruction or an explicitly authorized reset of disposable demo data. If evidence cannot recover exact state, report that gap instead of assigning invented balances. Implement the reservation and risk lifecycle changes above before presenting the system as correct under failure.
4. Provision fresh RSA keys separately. Reconcile old/random and new/deterministic execution IDs and choose the migration watermark. Ensure the single-owner engine can recover from a verified checkpoint or a complete new baseline log. Do not bypass the missing-history check. Recreate/rerun the completed Kafka initialization Job as a reviewed migration step (its pod template is immutable), then inspect the topic's effective retention configuration; a changed template alone does not alter an existing broker.
5. Rehearse the rollout against the restored copy, including partial publication/restart, cancel/fill reordering, concurrent duplicate placement, duplicate settlement, and denied cross-account access. Compare exact per-order/per-account invariants, not only health endpoints.
6. Deploy the reviewed artifacts during maintenance, verify balances/books/consumer offsets, and resume submissions only after reconciliation passes. Application rollback alone cannot undo new trading effects; retain the full data recovery plan.

No part of this procedure has been executed against live trading data by this audit.
