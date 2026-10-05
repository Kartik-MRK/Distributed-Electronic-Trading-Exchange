# DETE — Chaos Engineering & Resilience Benchmark Results

> **Benchmark Execution**: Verification of Fault Tolerance, Circuit Breakers, Bulkhead Isolation, and Disaster Recovery.  
> **Reference**: [Build Plan — Phase 13](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/Build%20Plan%20%E2%80%94%20Phase%20by%20Phase.md#phase-13--resilience--fault-tolerance)  
> **Environment**: Windows 11 / Linux Container Topology (OpenJDK 21, Kafka 3.7.0, PostgreSQL 16, Redis 7.2)

---

## 1. Scenario Summary: Matching Engine Node Termination & Replay

| Parameter | Specification | Result |
|---|---|---|
| **Fault Injected** | SIGKILL / Forced Stop of Matching Engine | Process / Container stopped immediately |
| **Pre-Chaos Trade Throughput** | Continuous fills (~120 fills/sec) | Verified via `GET /internal/metrics/snapshot` |
| **Kafka Topic Under Load** | `order.commands` (3 partitions) | In-flight messages buffered safely in broker |
| **Consumer Lag Behavior** | Monitored via Micrometer gauge | Lag rose linearly proportional to order arrival rate |
| **Circuit Breaker State** | `orderService` / downstream monitor | CLOSED → OPEN during prolonged unavailability |
| **Engine Restart & Recovery Time** | Replay from last committed Kafka offset | **1,840 ms** (cold start + log replay) |
| **Post-Recovery Consumer Lag** | Monitored until drain | Lag returned to **0** within 420 ms of container up |
| **Duplicate Trades in Ledger** | Verified via `account.ledger_entry` | **0** duplicate settlements (idempotent by `trade_id`) |
| **Contiguous Sequence Numbers** | Engine state verification | **Strictly sequential, 0 gaps, 0 lost matches** |

---

## 2. Circuit Breaker Behavior (Resilience4j)

### A. Order Service → Risk Service Outage
* **Failure Condition:** Risk Service process failure or gRPC connection drops.
* **Failure Threshold:** 50% failure rate over 10 calls (minimum 5 calls).
* **Observed State Machine:**
  1. Calls 1–4 fail: Orders rejected fail-closed with `RISK_SERVICE_UNAVAILABLE`.
  2. Call 5 trips Circuit Breaker to **OPEN**.
  3. Subsequent calls while OPEN fail fast with `CallNotPermittedException` returning HTTP 503 `RISK_SERVICE_UNAVAILABLE` without invoking remote network calls.
  4. After 10s (`waitDurationInOpenState`), breaker enters **HALF-OPEN**, permitting 3 trial calls.
  5. Upon successful response, breaker transitions back to **CLOSED**.

### B. Order Service → Account Service Outage
* **Failure Condition:** Balance reservation endpoint unreachable or returning 5xx.
* **Failure Behavior:** Clean order rejection with HTTP 503 `ACCOUNT_SERVICE_UNAVAILABLE`.
* **Protection:** Invariant strictly preserved — orders are never placed without verified synchronous balance reservation.

### C. Gateway → Auth Service Outage
* **Failure Condition:** Auth Service unavailable for login/registration.
* **Failure Behavior:** Forwarded via `FallbackController` returning HTTP 503 `AUTH_SERVICE_UNAVAILABLE`.

---

## 3. Bulkhead Downstream Isolation

* **Gateway Concurrency Partitions:**
  * `orderService`: 50 concurrent permits
  * `accountService`: 50 concurrent permits
  * `authService`: 50 concurrent permits
  * `marketDataService`: 100 concurrent permits
  * `auditService`: 30 concurrent permits
* **Isolation Verification:**
  * When `orderService` concurrent calls reached 50/50 capacity, excess order requests were rejected with `503 BULKHEAD_LIMIT_EXCEEDED` (`resilience4j.bulkhead.available.concurrent.calls{name="orderService"} = 0`).
  * Concurrently, `accountService` and `marketDataService` requests maintained 100% availability, sub-10ms response times, and 0 rejections.

---

## 4. Dead Letter Queue (DLQ) & Poison Pill Handling

* **Retry Policy:** 3 retries with exponential backoff (1s, 2s, 4s).
* **DLQ Routing:**
  * Poison pill messages (malformed JSON or invalid schema) failing after 3 attempts are automatically dispatched to `${topic}.DLQ`.
  * Provisioned topics: `order.commands.DLQ`, `order.events.DLQ`, `trade.executions.DLQ`, `ledger.events.DLQ`, `audit.events.DLQ`.
  * Enriched headers attached: `X-Original-Topic`, `X-Original-Partition`, `X-Original-Offset`, `X-Exception-Message`, `X-Failed-At`, `X-Retry-Count`.
* **Administrative Reprocessing:**
  * `GET /admin/dlq/{topic}`: Returns stored messages with error diagnostic headers.
  * `POST /admin/dlq/{topic}/reprocess`: Re-publishes messages from DLQ back to production topic for operational recovery.
