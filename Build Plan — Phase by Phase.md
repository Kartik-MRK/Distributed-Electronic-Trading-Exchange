# DETE — Phase-by-Phase Build Plan

> **Purpose:** This document is the authoritative step-by-step reference for building the Distributed Electronic Trading Exchange end-to-end. Each phase defines exactly what must be built, how it fits together, and what the completion criteria are. Phases are sequential; later phases depend on earlier ones. Read the phase description fully before starting any task within it.

---

## Phase Map (Quick Reference)

| Phase | Name | Core Output |
|---|---|---|
| 0 | Foundation & Repository Setup | Mono-repo skeleton, toolchain, shared libraries, local infra |
| 1 | Authentication Service | JWT auth, user registration/login, token refresh |
| 2 | Account / Ledger Service | Balances, double-entry ledger, fund reservation |
| 3 | Matching Engine (Core) | In-process order book, price-time priority, limit/market/IOC/FOK/GTC |
| 4 | Order Service + Kafka Integration | Order lifecycle API, Kafka topics wired, transactional outbox |
| 5 | Risk Service | Pre-trade risk checks wired into order flow via gRPC |
| 6 | Market Data Service | Read-side materialized view, WebSocket price feed |
| 7 | Audit Service | Immutable append-only compliance log, admin query API |
| 8 | Gateway Service | Unified entry point, rate limiting, routing, auth enforcement |
| 9 | Frontend Phase A (Trading Terminal) | Auth, order entry, live order book, trade tape, account view |
| 10 | Observability Stack | Prometheus, Grafana dashboards, OpenTelemetry tracing |
| 11 | Simulator / Market-Maker Bot Service | Auto-trading bots, demo account seeding |
| 12 | Frontend Phase B (Dashboard & Replay) | Public dashboard, historical replay viewer, audit log viewer |
| 13 | Resilience & Fault Tolerance | Circuit breakers, bulkheads, chaos scenario, DLQ handling |
| 14 | Deployment — Docker & K3s | Dockerfiles, Helm chart, K3s cluster deployment |
| 15 | CI/CD Pipeline | GitHub Actions: test -> build -> deploy -> smoke test |
| 16 | Performance Engineering | JMH benchmarks, k6 load tests, tuning, results committed |
| 17 | Hardening & Polish | ArchUnit, JCStress, property tests, runbook, final demo |

---

## Phase 0 — Foundation & Repository Setup

**Goal:** A working mono-repo with shared toolchain, local infrastructure running via Docker Compose, and shared library modules that all services will depend on.

### 0.1 Repository Structure

```
dete/
├── services/
│   ├── gateway/
│   ├── auth/
│   ├── order/
│   ├── risk/
│   ├── account/
│   ├── matching-engine/
│   ├── market-data/
│   ├── audit/
│   └── simulator/
├── libs/
│   ├── common-domain/       # shared value objects, enums, event records
│   ├── common-events/       # Kafka event schema definitions (Java records)
│   ├── common-security/     # JWT verification logic shared across services
│   └── common-test/         # Testcontainers base classes, test utilities
├── frontend/
├── infra/
│   ├── docker-compose.yml
│   ├── helm/
│   └── k8s/
├── docs/
│   ├── architecture.md
│   └── runbook.md
├── benchmarks/
├── scripts/
└── .github/workflows/
```

### 0.2 Shared Library: `common-domain`

Define core value objects and enums used across all services:

- `OrderId`, `AccountId`, `TradeId`, `InstrumentId` — typed wrappers (not raw strings)
- `Instrument` enum: `BTC_USD`, `ETH_USD`, `SOL_USD`
- `OrderSide` enum: `BUY`, `SELL`
- `OrderType` enum: `LIMIT`, `MARKET`, `IOC`, `FOK`, `GTC`
- `OrderStatus` enum: `SUBMITTED`, `ACCEPTED`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`, `REJECTED`
- `Money`, `Quantity`, `Price` — all fixed-point `long`-based (8 decimal places). No BigDecimal on the hot path.

> **Critical:** All monetary and quantity types must use integer/fixed-point arithmetic. `double` and `float` must never appear in financial calculations.

### 0.3 Shared Library: `common-events`

Java records representing all Kafka event contracts between services:

```
OrderPlacedEvent       { orderId, accountId, instrument, side, type, price, quantity, timestamp, idempotencyKey }
OrderAcceptedEvent     { orderId, sequenceNumber, timestamp }
OrderRejectedEvent     { orderId, reason, timestamp }
OrderCancelledEvent    { orderId, remainingQuantity, timestamp }
OrderPartiallyFilledEvent { orderId, fillQuantity, fillPrice, remainingQuantity, tradeId, timestamp }
OrderFilledEvent       { orderId, fillQuantity, fillPrice, tradeId, timestamp }
TradeExecutedEvent     { tradeId, instrument, buyOrderId, sellOrderId, buyAccountId, sellAccountId, price, quantity, sequenceNumber, timestamp }
BalanceReservedEvent   { accountId, orderId, instrument, side, amount, timestamp }
BalanceReleasedEvent   { accountId, orderId, amount, timestamp }
BalanceSettledEvent    { tradeId, buyAccountId, sellAccountId, instrument, price, quantity, timestamp }
AuditEvent             { eventId, eventType, subjectId, subjectType, payload, timestamp }
```

All events are immutable records and include: `eventId` (UUID), `eventType` (string), `timestamp` (ISO-8601), `version` (integer).

### 0.4 Shared Library: `common-test`

- `KafkaTestContainerBase` — abstract base spinning up a Kafka container
- `PostgresTestContainerBase` — abstract base for Postgres
- `RedisTestContainerBase` — abstract base for Redis
- `FullStackTestBase` — Kafka + Postgres + Redis together
- Test data builders for all domain objects

### 0.5 Local Infrastructure (`docker-compose.yml`)

All containers start with `docker compose up`:

| Container | Image | Port | Purpose |
|---|---|---|---|
| `postgres` | `postgres:16` | 5432 | Primary DB |
| `redis` | `redis:7` | 6379 | Cache and idempotency |
| `kafka` | `confluentinc/cp-kafka:7.x` | 9092 | Event backbone |
| `zookeeper` | `confluentinc/cp-zookeeper:7.x` | 2181 | Kafka coordination |
| `kafka-ui` | `provectuslabs/kafka-ui` | 8080 | Topic inspector (dev) |
| `prometheus` | `prom/prometheus` | 9090 | Metrics scraper |
| `grafana` | `grafana/grafana` | 3000 | Dashboards |
| `jaeger` | `jaegertracing/all-in-one` | 16686 | Distributed traces |

### 0.6 Kafka Topic Provisioning

Topics to create (partitions=3 for main, partitions=1 for DLQs in dev):

```
order.commands          order.events            trade.executions
market-data             ledger.events           audit.events
simulator.commands      order.commands.DLQ      order.events.DLQ
trade.executions.DLQ    ledger.events.DLQ
```

### Phase 0 Completion Criteria

- [x] `./gradlew build` compiles all modules with zero errors
- [x] `docker compose up` starts all infrastructure cleanly
- [x] All Kafka topics are created and visible in Kafka UI
- [x] `common-domain`, `common-events`, `common-test` importable by other modules
- [x] A trivial Kafka producer/consumer test in `common-test` passes with Testcontainers

---

## Phase 1 — Authentication Service

**Goal:** A standalone `auth` service that handles user registration, login, JWT issuance, and token refresh. All other services validate tokens but do not issue them.

### 1.1 Database Schema (`auth` schema)

```sql
CREATE TABLE auth.users (
    user_id       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      VARCHAR(64) UNIQUE NOT NULL,
    email         VARCHAR(256) UNIQUE NOT NULL,
    password_hash VARCHAR(256) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    is_demo       BOOLEAN NOT NULL DEFAULT false
);

CREATE TABLE auth.refresh_tokens (
    token_id    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID NOT NULL REFERENCES auth.users(user_id),
    token_hash  VARCHAR(256) NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked     BOOLEAN NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

Flyway migration: `V1__create_auth_schema.sql`

### 1.2 REST API Endpoints

| Method | Path | Description |
|---|---|---|
| `POST` | `/auth/register` | Register new user. Returns 201 with userId. |
| `POST` | `/auth/login` | Validate credentials. Returns access JWT + refresh token. |
| `POST` | `/auth/refresh` | Exchange refresh token for new access JWT. |
| `POST` | `/auth/logout` | Revoke refresh token. |
| `GET` | `/auth/me` | Return current authenticated user info. |

### 1.3 JWT Design

- Access token: short-lived (15 min), RS256, signed with private key in auth service
- Refresh token: long-lived (7 days), stored hashed in DB, rotated on use
- JWT claims: `sub` (userId), `username`, `roles`, `iat`, `exp`
- Public key at `/auth/.well-known/jwks.json` for all other services to verify without calling auth
- `common-security` library: Spring Security filter validating RS256 JWT — shared across all services

### 1.4 Security

- Passwords hashed with BCrypt (strength 12)
- Refresh tokens hashed with SHA-256 before DB storage
- Rate limiting on `/auth/login`: 5 attempts/minute per IP via Redis sliding window
- Return `429 Too Many Requests` with `Retry-After` header on breach

### 1.5 Demo User Seeding

On startup with `demo.enabled=true`, create demo user (`demo@dete.io`) with `is_demo=true`. Account funded in Phase 11.

### 1.6 Audit Events

Publish to `audit.events`: `USER_REGISTERED`, `USER_LOGIN`, `USER_LOGOUT`, `TOKEN_REFRESHED` — each with userId and timestamp.

### 1.7 Tests

- Unit: password hashing, JWT generation/validation, token rotation
- Integration (Testcontainers Postgres + Redis): full register → login → refresh → logout
- ArchUnit: no domain logic in controller layer

### Phase 1 Completion Criteria

- [x] `POST /auth/register` creates user in Postgres
- [x] `POST /auth/login` returns valid RS256 JWT and refresh token
- [x] `POST /auth/refresh` rotates refresh token correctly
- [x] JWT validation filter rejects expired/invalid tokens in all services
- [x] Rate limiting triggers after 5 login attempts per minute per IP
- [x] Audit events published for every auth action
- [x] All integration tests pass

---

## Phase 2 — Account / Ledger Service

**Goal:** The authoritative financial state. Double-entry accounting, balance reservation before order acceptance, and settlement after trades.

### 2.1 Database Schema (`account` schema)

```sql
CREATE TABLE account.accounts (
    account_id UUID PRIMARY KEY,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account.balances (
    account_id UUID NOT NULL REFERENCES account.accounts(account_id),
    asset      VARCHAR(16) NOT NULL,
    available  BIGINT NOT NULL DEFAULT 0 CHECK (available >= 0),
    reserved   BIGINT NOT NULL DEFAULT 0 CHECK (reserved >= 0),
    PRIMARY KEY (account_id, asset),
    CONSTRAINT total_non_negative CHECK (available + reserved >= 0)
);

CREATE TABLE account.ledger_entries (
    entry_id     BIGSERIAL PRIMARY KEY,
    account_id   UUID NOT NULL,
    asset        VARCHAR(16) NOT NULL,
    entry_type   VARCHAR(32) NOT NULL,   -- DEPOSIT, RESERVE, RELEASE, DEBIT, CREDIT, FEE
    amount       BIGINT NOT NULL,
    reference_id UUID NOT NULL,
    sequence_num BIGINT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ON account.ledger_entries(account_id, reference_id, entry_type);

CREATE TABLE account.settlements (
    settlement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trade_id      UUID NOT NULL UNIQUE,
    buyer_id      UUID NOT NULL,
    seller_id     UUID NOT NULL,
    instrument    VARCHAR(16) NOT NULL,
    price         BIGINT NOT NULL,
    quantity      BIGINT NOT NULL,
    settled_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE account.outbox (
    outbox_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic      VARCHAR(128) NOT NULL,
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published  BOOLEAN NOT NULL DEFAULT false
);
```

### 2.2 gRPC API (internal)

```protobuf
service AccountService {
    rpc GetBalance(GetBalanceRequest) returns (BalanceResponse);
    rpc ReserveFunds(ReserveFundsRequest) returns (ReserveFundsResponse);
    rpc ReleaseFunds(ReleaseFundsRequest) returns (ReleaseFundsResponse);
    rpc CreateAccount(CreateAccountRequest) returns (CreateAccountResponse);
}
```

`ReserveFunds`: atomically checks available >= requested, decrements available, increments reserved, writes ledger entry, publishes `BalanceReservedEvent` via outbox — all in one Postgres transaction.

### 2.3 REST API (external)

| Method | Path | Description |
|---|---|---|
| `GET` | `/accounts/me/balances` | Available and reserved balances for all assets |
| `GET` | `/accounts/me/ledger` | Paginated ledger entry history |
| `GET` | `/accounts/me/trades` | Paginated personal trade history |

### 2.4 Kafka Consumer — Trade Settlement

Consume `trade.executions`. For each `TradeExecutedEvent`:

1. Check `settlements` table for `trade_id` — skip if already present (idempotency).
2. In one Postgres transaction:
   - Debit buyer reserved quote asset
   - Credit buyer base asset
   - Debit seller reserved base asset
   - Credit seller quote asset
   - Write 4 ledger entries (double-entry)
   - Insert settlement record
   - Publish `BalanceSettledEvent` to outbox
3. Outbox publisher sends to `ledger.events` and `audit.events`.

### 2.5 Transactional Outbox

Scheduled poller reads `account.outbox WHERE published = false`, publishes to Kafka, marks `published = true`. Outbox write is always in the same transaction as the balance change.

### 2.6 Tests

- Unit: balance arithmetic, ledger entry logic, idempotency checks
- Integration: reserve → cancel → release; settle → verify 4 ledger entries; duplicate settlement is no-op
- Property test: `available + reserved = total` holds after any sequence of operations
- JCStress: concurrent reserve attempts do not produce negative balances

### Phase 2 Completion Criteria

- [x] `ReserveFunds` atomically reserves funds and writes ledger entry
- [x] Duplicate `trade_id` in settlement is idempotent
- [x] `available >= 0` enforced at DB level
- [x] Outbox reliably delivers all events
- [x] Ledger entries are immutable (no UPDATE or DELETE on ledger table)

---

## Phase 3 — Matching Engine (Core)

**Goal:** A high-performance, deterministic, single-writer matching engine.

### 3.1 Architecture

- One `OrderBook` per instrument; owned by a single thread (single-writer model)
- Orders received via Kafka `order.commands` (partitioned by instrument key)
- Trades published via Kafka `trade.executions` and `order.events`
- No Postgres on the hot path — fully in-memory
- Order book rebuilt from Kafka on startup (event replay)

### 3.2 In-Memory Order Book Data Structure

```
OrderBook
├── BidSide: TreeMap<Price, PriceLevel>   (descending: highest bid first)
├── AskSide: TreeMap<Price, PriceLevel>   (ascending: lowest ask first)
└── OrderIndex: HashMap<OrderId, Order>   (O(1) cancel lookup)

PriceLevel
├── price: long (fixed-point)
└── orders: ArrayDeque<Order>            (FIFO — time priority within price level)

Order
├── orderId, accountId, instrument, side, type
├── price, originalQuantity, remainingQuantity (all long fixed-point)
├── sequenceNumber: long
└── status: OrderStatus
```

### 3.3 Matching Logic

**LIMIT BUY:**
1. Assign monotonically increasing sequence number
2. Self-trade prevention: skip ask levels owned by same account
3. Walk ask side (lowest ask first): if ask.price <= order.price → match, produce `TradeExecutedEvent`, reduce quantities
4. Remove depleted ask; stop when order fully filled or no more crossing asks
5. Rest remainder on bid side (end of FIFO queue at that price level)

**MARKET:** Match aggressively, no resting. Reject if book empty.
**IOC:** Match immediately, cancel remainder.
**FOK:** Check sufficient depth first; match fully or reject entirely.
**CANCEL:** O(1) lookup via `OrderIndex`, remove from price level queue.
**MODIFY:** Cancel-and-reinsert — cancel existing, insert new with fresh sequence number (goes to back of FIFO queue at its price level).

### 3.4 Kafka Producer (output)

Use Kafka transactions: `trade.executions` and `order.events` published atomically. Commit Kafka offset only after successful publish.

### 3.5 Fixed-Point Arithmetic

```java
public final class FixedPoint {
    public static final long SCALE = 100_000_000L;  // 8 decimal places
    public static long multiply(long a, long b) { ... }  // careful of overflow
    public static long divide(long a, long b) { ... }
}
```

### 3.6 Tests

- Unit: all order types; price-time priority verified; self-trade prevention; partial fill; level cleanup
- Property: for any random order sequence — `executed_qty <= submitted_qty`, no negative quantities, bid < ask after matching
- JCStress: concurrent cancel + fill on same order — exactly one wins
- Integration: publish to Kafka `order.commands`, assert correct `trade.executions` and `order.events`

### Phase 3 Completion Criteria

- [x] All order types match correctly
- [x] Price-time priority verified: same-price orders fill FIFO
- [x] O(1) cancellation via `OrderIndex`
- [x] Modify = cancel-and-reinsert with new sequence number
- [x] Engine replays Kafka on restart and reconstructs state correctly
- [x] Kafka transactions ensure atomic publication of trade + order events
- [x] JMH benchmark stubs compile and run

---

## Phase 4 — Order Service + Full Kafka Integration

**Goal:** The public-facing order management API coordinating auth, risk, account reservation, and Kafka publishing.

### 4.1 Database Schema (`order_svc` schema)

```sql
CREATE TABLE order_svc.orders (
    order_id        UUID PRIMARY KEY,
    account_id      UUID NOT NULL,
    instrument      VARCHAR(16) NOT NULL,
    side            VARCHAR(4) NOT NULL,
    order_type      VARCHAR(8) NOT NULL,
    price           BIGINT,
    original_qty    BIGINT NOT NULL,
    remaining_qty   BIGINT NOT NULL,
    filled_qty      BIGINT NOT NULL DEFAULT 0,
    status          VARCHAR(32) NOT NULL,
    idempotency_key UUID NOT NULL UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON order_svc.orders(account_id, status);

CREATE TABLE order_svc.outbox (
    outbox_id  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    topic      VARCHAR(128) NOT NULL,
    key        VARCHAR(128),
    payload    JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published  BOOLEAN NOT NULL DEFAULT false
);
```

### 4.2 REST API

| Method | Path | Description |
|---|---|---|
| `POST` | `/orders` | Place a new order |
| `DELETE` | `/orders/{orderId}` | Cancel an order |
| `PUT` | `/orders/{orderId}` | Modify an order |
| `GET` | `/orders/{orderId}` | Single order status |
| `GET` | `/orders?status=OPEN` | List user's orders |
| `GET` | `/orders/history` | Paginated order history |

### 4.3 Order Placement Flow

```
POST /orders
  1. Validate request
  2. Check idempotency key in Redis (reject if duplicate)
  3. gRPC -> Risk Service: ValidateOrder [circuit breaker]
  4. gRPC -> Account Service: ReserveFunds [circuit breaker]
  5. Postgres transaction:
       INSERT order (status=SUBMITTED)
       INSERT outbox (topic=order.commands, payload=OrderPlacedEvent)
  6. Return 202 Accepted with orderId
  7. Outbox publisher sends to Kafka
  8. Matching Engine processes
  9. Order Service consumes order.events to update order status
```

### 4.4 Kafka Consumer — Order Events

Update `order_svc.orders` status on each event. All transitions must be idempotent.

`OrderRejectedEvent` → release reserved funds via Account Service gRPC.

### 4.5 Idempotency

- Client sends `Idempotency-Key` header (UUID)
- Stored in Redis (TTL=24h) on first receipt; subsequent calls return original response
- DB unique constraint on `idempotency_key` as backup

### 4.6 Dead-Letter Queue

Failed Kafka messages after N retries with exponential backoff → `.DLQ` topic. Expose `/admin/dlq/reprocess` endpoint.

### Phase 4 Completion Criteria

- [ ] Full order placement flow end-to-end
- [ ] Duplicate idempotency key → same response, no duplicate DB record
- [ ] Order status updates correctly from `order.events`
- [ ] Cancelled order triggers fund release
- [ ] Failed messages route to DLQ after retries

---

## Phase 5 — Risk Service

**Goal:** Pre-trade risk validation via gRPC, called synchronously before account reservation.

### 5.1 Risk Rules

| Rule | Description |
|---|---|
| Max order size | Quantity <= configured max per instrument |
| Max notional | Price x Quantity <= configured USD limit |
| Max open orders | Account cannot exceed N concurrent open orders |
| Price deviation | Limit price cannot deviate >X% from last trade price |
| Market order guard | Market orders require sufficient book depth |
| Self-trade flag | Flag if account has a resting order that would immediately match |

### 5.2 gRPC API

```protobuf
service RiskService {
    rpc ValidateOrder(ValidateOrderRequest) returns (ValidateOrderResponse);
}
message ValidateOrderResponse {
    bool approved = 1;
    string rejectionReason = 2;
}
```

### 5.3 State

- Last trade price per instrument: consumed from `trade.executions` topic, maintained in-memory
- Risk config: loaded from application YAML, hot-reloadable

### 5.4 Circuit Breaker

Order Service wraps gRPC call in Resilience4j circuit breaker. If Risk Service is down, orders are **rejected** (fail closed — never bypass risk). Circuit breaker state exported as Prometheus metric.

### Phase 5 Completion Criteria

- [ ] All 6 risk rules enforced with failing test cases
- [ ] gRPC call from Order Service works end-to-end
- [ ] Circuit breaker rejects orders cleanly when Risk Service is down
- [ ] Circuit breaker state in `/actuator/metrics`

---

## Phase 6 — Market Data Service

**Goal:** Read-side CQRS. Materialized view of order book, trade tape, and OHLCV candles. Serves frontend via WebSocket and REST.

### 6.1 Kafka Consumers

| Topic | Action |
|---|---|
| `order.events` | Add/remove orders from in-memory order book snapshot |
| `trade.executions` | Append to trade tape; update OHLCV candles |
| `market-data` | Consume periodic full snapshots from Matching Engine (resync) |

### 6.2 In-Memory State (per instrument)

```
OrderBookSnapshot: { bids: List<PriceLevel>, asks: List<PriceLevel> }
TradeTape:         bounded ring buffer of last 500 trades
OHLCVCandles:      Map<Interval, List<Candle>>   (1m, 5m, 15m, 1h, 1d)
LastTradedPrice:   long
```

### 6.3 WebSocket API

| Channel | Message | Trigger |
|---|---|---|
| `orderbook.{instrument}` | Order book diff or full snapshot | On every order event |
| `trades.{instrument}` | Single trade execution | On every TradeExecutedEvent |
| `candles.{instrument}.{interval}` | Updated candle | On candle update |
| `orders.{accountId}` | Order status update for authenticated user | On order.events for that user |

### 6.4 REST API

| Method | Path | Description |
|---|---|---|
| `GET` | `/market-data/{instrument}/orderbook` | Current snapshot |
| `GET` | `/market-data/{instrument}/trades` | Last 100 trades |
| `GET` | `/market-data/{instrument}/candles?interval=1m` | OHLCV data |
| `GET` | `/market-data/instruments` | Available instruments |

### 6.5 Order Book Snapshot Publishing

Matching Engine publishes a full order book snapshot to `market-data` topic every 100ms. Market Data Service resyncs from these periodically.

### 6.6 Historical Trade Storage (for Replay Viewer, Phase 12)

Persist all `TradeExecutedEvent` records to `market_data.trades` table (read model only). Enables order book reconstruction for any past time window.

### Phase 6 Completion Criteria

- [ ] WebSocket client receives order book update within 100ms of order event
- [ ] OHLCV candles calculate and update correctly
- [ ] REST endpoints return correct current state
- [ ] Historical trades persisted in read-model DB
- [ ] 1000 concurrent WebSocket connections under k6 load without errors

---

## Phase 7 — Audit Service

**Goal:** Immutable append-only compliance log.

### 7.1 Database Schema (`audit` schema — separate from operational schemas)

```sql
CREATE TABLE audit.audit_log (
    entry_id     BIGSERIAL PRIMARY KEY,
    event_id     UUID NOT NULL UNIQUE,
    event_type   VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id   UUID NOT NULL,
    actor_id     UUID,
    instrument   VARCHAR(16),
    payload      JSONB NOT NULL,
    trace_id     VARCHAR(64),
    event_time   TIMESTAMPTZ NOT NULL,
    ingested_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ON audit.audit_log(subject_id, event_time DESC);
CREATE INDEX ON audit.audit_log(event_type, event_time DESC);
CREATE INDEX ON audit.audit_log(instrument, event_time DESC);
```

Records must NEVER be updated or deleted. Enforce with a Postgres trigger that blocks UPDATE/DELETE.

### 7.2 Kafka Consumer

- Consume `audit.events`
- Deduplicate by `event_id` (unique constraint)
- Fully idempotent

### 7.3 REST API (admin role required)

| Method | Path | Description |
|---|---|---|
| `GET` | `/admin/audit?subjectId=&from=&to=` | Audit log for order/account/user |
| `GET` | `/admin/audit?instrument=&from=&to=` | Events for an instrument |
| `GET` | `/admin/audit?eventType=&from=&to=` | Events by type |
| `GET` | `/admin/audit/entries/{eventId}` | Single entry with full payload |

### Phase 7 Completion Criteria

- [ ] All `audit.events` messages stored in `audit.audit_log`
- [ ] Duplicate events idempotently ignored
- [ ] No UPDATE or DELETE possible (DB-level enforcement)
- [ ] Query API returns correct filtered results
- [ ] Trace IDs stored and queryable

---

## Phase 8 — Gateway Service

**Goal:** Single entry point. JWT enforcement, rate limiting, routing, request correlation.

### 8.1 Routing Table

| Path prefix | Downstream |
|---|---|
| `/auth/**` | auth service |
| `/orders/**` | order service |
| `/accounts/**` | account service |
| `/market-data/**` | market data service |
| `/admin/audit/**` | audit service |
| `/ws/**` | market data service (WebSocket) |

### 8.2 Rate Limiting (Redis sliding window)

- Authenticated: 100 req/min general; 20 req/min for order placement
- Unauthenticated: 20 req/min per IP
- Return `429 Too Many Requests` with `Retry-After` header

### 8.3 Other Responsibilities

- Validate JWT on every non-public request; inject user context into forwarded headers
- Assign correlation ID (OTel trace root)
- Bulkhead: separate thread pools per downstream service (Spring Cloud Gateway + Resilience4j)
- Implement with Spring Cloud Gateway (reactive, non-blocking)

### Phase 8 Completion Criteria

- [ ] All traffic routes correctly
- [ ] Invalid/expired JWT returns 401 at gateway; downstream never sees unauthenticated requests
- [ ] Rate limiting fires at configured thresholds
- [ ] Correlation ID in all forwarded requests and logs
- [ ] Bulkhead: slow order service does not degrade account service requests

---

## Phase 9 — Frontend, Phase A: Trading Terminal

**Goal:** A functional, polished trading terminal. Log in, place orders, see fills in real time.

### 9.1 Project Setup

```
frontend/
├── src/
│   ├── app/
│   │   ├── (auth)/        # login, register pages
│   │   ├── (terminal)/    # main trading terminal
│   │   └── (dashboard)/   # system dashboard (Phase 12)
│   ├── components/
│   │   ├── orderbook/
│   │   ├── tradetape/
│   │   ├── chart/
│   │   ├── orderentry/
│   │   ├── openorders/
│   │   ├── orderhistory/
│   │   ├── account/
│   │   └── common/
│   ├── hooks/
│   │   ├── useWebSocket.ts
│   │   ├── useOrderBook.ts
│   │   └── useAccount.ts
│   ├── lib/
│   │   ├── api.ts          # REST client (typed)
│   │   └── ws.ts           # WebSocket manager with reconnect
│   └── types/index.ts      # TypeScript types matching Java domain objects
```

### 9.2 Authentication Pages

- `/login`: username/password → `POST /auth/login` → store JWT in `httpOnly` cookie or `sessionStorage` (never `localStorage`)
- `/register`: registration form
- "Try Demo" button: one-click login as demo user
- Redirect to `/terminal` on success

### 9.3 Terminal Layout

```
+-------------------------------------------------------------------+
| Header: Instrument selector | WS status | Latency | User menu    |
+------------------+---------------------------+--------------------+
| Order Book       |  Price Chart (candles)    |  Order Entry       |
| (bid/ask depth   |  (live OHLCV, interval    |  (buy/sell, type,  |
|  ladder, spread) |   selector)               |   price, qty)      |
+------------------+---------------------------+                    |
| Trade Tape (scrolling fill feed, all recent) |                    |
+----------------------------------------------+--------------------+
| Open Orders table (cancel button, live status updates)            |
| Order History | Trade History                                     |
+-------------------------------------------------------------------+
| Account: Available | Reserved | P&L per instrument                |
+-------------------------------------------------------------------+
```

### 9.4 WebSocket Manager

- Auto-reconnects with exponential backoff
- Subscribes to: `orderbook.{instrument}`, `trades.{instrument}`, `orders.{accountId}`
- Displays connection state (green = connected, red = reconnecting)
- Displays round-trip latency (ping every 5 seconds, shown in header)

### 9.5 Order Book Component

- Display top 15 bid and top 15 ask levels
- Each level: price, quantity, cumulative quantity depth bar
- Price levels animate (brief highlight) when they change
- Spread displayed between bid and ask
- Clicking a price auto-fills the order entry form

### 9.6 Price Chart Component

- Use `lightweight-charts` (TradingView open-source) for candlestick rendering
- Subscribe to candle WebSocket channel
- Interval selector: 1m, 5m, 15m, 1h
- Last traded price line overlay

### 9.7 Order Entry Component

- Buy/Sell tab selector
- Order type dropdown: LIMIT, MARKET, IOC, FOK, GTC
- Price input (disabled for MARKET)
- Quantity input with estimated cost display
- Available balance display
- Submit calls `POST /orders` with `Idempotency-Key: <client-generated UUID>` header
- Success toast on fill; error toast on rejection

### 9.8 Open Orders & History Tables

- Open Orders: SUBMITTED/ACCEPTED/PARTIALLY_FILLED orders; cancel button; WebSocket live updates
- Order History: paginated, all statuses
- Trade History: personal fills with fill price, quantity

### 9.9 Account Panel

- Available balance per asset (USD, BTC, ETH, SOL)
- Reserved balance per asset
- Updates in real time as trades settle (via `ledger.events` WebSocket push)

### Phase 9 Completion Criteria

- [ ] User can register, log in, and reach the trading terminal
- [ ] Demo login works with one click
- [ ] Order book updates in real time via WebSocket
- [ ] Candlestick chart renders and updates live
- [ ] User can place LIMIT and MARKET orders and see fills
- [ ] Open orders table shows real-time status; cancel works
- [ ] Account balances update after settlement
- [ ] WebSocket auto-reconnects after disconnect

---

## Phase 10 — Observability Stack

**Goal:** Full metrics, distributed tracing, and structured logging across all services. Pre-built Grafana dashboards committed to the repo.

### 10.1 Metrics — Actuator + Micrometer + Prometheus

Every service exposes `/actuator/prometheus`. Custom metrics:

| Metric | Type | Service |
|---|---|---|
| `orders_placed_total` | Counter | Order Service |
| `orders_rejected_total` | Counter | Order / Risk |
| `trades_executed_total` | Counter | Matching Engine |
| `matching_latency_seconds` | Histogram (P50/P95/P99) | Matching Engine |
| `order_e2e_latency_seconds` | Histogram | ME / Market Data |
| `kafka_consumer_lag` | Gauge | All consumers |
| `ledger_entries_total` | Counter | Account Service |
| `websocket_connections_active` | Gauge | Market Data |
| `circuit_breaker_state` | Gauge (0/1/2) | Order Service |
| `db_query_latency_seconds` | Histogram | All services |

### 10.2 Distributed Tracing — OpenTelemetry

- All services: `opentelemetry-spring-boot-starter`
- Exporter: OTLP → Jaeger
- Context propagated via: HTTP `traceparent` headers, Kafka message headers, gRPC metadata
- Spans on: every HTTP request, every Kafka produce/consume, every gRPC call, every DB query
- Trace ID stored in every `audit.events` message and in all structured log lines
- Manual spans on matching engine hot path

### 10.3 Structured Logging

- Logback + JSON encoder (`logstash-logback-encoder`) on every service
- Every log line includes: `timestamp`, `level`, `service`, `traceId`, `spanId`, `message`, domain fields
- No unstructured logging; no `System.out.println`

### 10.4 Grafana Dashboards (committed as JSON to `infra/grafana/dashboards/`)

| Dashboard | Key Panels |
|---|---|
| **System Overview** | All services health, request rate, error rate, P99 latency |
| **Matching Engine** | Orders/sec, fills/sec, latency histogram, sequence counter |
| **Order Lifecycle** | Placement rate, rejection rate, fill rate, e2e latency |
| **Kafka** | Per-topic throughput, consumer lag per group |
| **Account / Ledger** | Settlement rate, ledger entry rate, reservation rate |
| **JVM** | Heap, GC pause, threads, CPU per service |
| **Circuit Breakers** | State per breaker, failure rate, calls in half-open |
| **WebSocket** | Active connections, message rate, reconnection events |

All dashboards provisioned automatically via Grafana provisioning config — no manual import.

### Phase 10 Completion Criteria

- [ ] All services expose `/actuator/prometheus` with custom business metrics
- [ ] Single trade traceable from Gateway to WebSocket in Jaeger UI
- [ ] All Grafana dashboards load and show live data
- [ ] `kafka_consumer_lag` in Prometheus for every consumer group
- [ ] Circuit breaker state visible in Grafana
- [ ] Trace ID in logs and audit log

---

## Phase 11 — Simulator / Market-Maker Bot Service

**Goal:** Automated bots that keep the exchange live at all times. Enable demo mode.

### 11.1 Bot Design (one bot per instrument)

Each bot:
- Maintains a configurable target mid-price (e.g., BTC=65,000)
- Places N limit orders per side spread across ±2% from mid
- Randomly re-prices some orders every few seconds
- Occasionally places a small market order to generate fills
- Cancels and replaces orders to keep the book fresh

Bots use the same `POST /orders` REST API as real users — not internal shortcuts. They exercise the full system end-to-end.

### 11.2 Mid-Price Drift

Random walk: every 10s, `mid_price += random_delta in [-0.3%, +0.3%]`. Makes price chart look realistic.

### 11.3 Demo Account Seeding (on startup if `demo.enabled=true`)

1. Create demo user via Auth Service if not exists
2. Create bot accounts if not exist
3. Fund bot accounts (admin ledger deposit)
4. Fund demo user: `10,000 USD + 1 BTC + 5 ETH + 50 SOL`
5. Start bot threads

### 11.4 Configuration (application.yml)

```yaml
simulator:
  enabled: true
  instruments:
    - symbol: BTC-USD
      initial-mid: 65000
      spread-bps: 20
      levels: 15
      order-size-min: 0.001
      order-size-max: 0.1
```

### Phase 11 Completion Criteria

- [ ] Order book for all 3 instruments populated within 10 seconds of startup
- [ ] Fills generated continuously — visible in trade tape
- [ ] Price chart shows moving candlesticks over time
- [ ] Demo user account funded and can place orders immediately
- [ ] Simulator can be disabled via config

---

## Phase 12 — Frontend, Phase B: System Dashboard, Replay & Audit

**Goal:** Public-facing System Dashboard, Historical Replay Viewer, and Audit Log Viewer.

### 12.1 System Dashboard (no login required — route: `/dashboard`)

| Panel | Data Source | Update Rate |
|---|---|---|
| Matching Throughput | `trades_executed_total` via metrics endpoint | 1 second |
| E2E Latency Histogram | `order_e2e_latency_seconds` histogram buckets | 5 seconds |
| Kafka Consumer Lag | `kafka_consumer_lag` gauge per topic | 5 seconds |
| Service Health | `/actuator/health` per service | 5 seconds |
| Active WS Connections | `websocket_connections_active` gauge | 5 seconds |
| Recent Trade Feed | WebSocket `trades.*` all instruments | Real-time |
| JVM Heap (key services) | `jvm_memory_used_bytes` metric | 10 seconds |

Prometheus metrics exposed via a backend aggregation endpoint (`GET /internal/metrics/snapshot`) so the frontend does not need direct Prometheus access.

### 12.2 Historical Replay Viewer (route: `/terminal?tab=replay`)

- Time range picker (max 1-hour window)
- `GET /market-data/{instrument}/replay?from={ts}&to={ts}` streams stored events
- Frontend animates order book frame by frame (1 frame per 100ms simulated time)
- Play/pause/speed controls
- Demonstrates Kafka event replayability visually

### 12.3 Audit Log Viewer (route: `/admin/audit`, admin JWT required)

- Filter bar: order ID, account ID, event type, instrument, time range
- Table: `event_time | event_type | subject_type | subject_id | instrument | trace_id`
- Row expansion: full JSON payload
- Trace ID is a clickable link opening Jaeger trace in new tab
- CSV export button

### Phase 12 Completion Criteria

- [ ] `/dashboard` loads without login; all panels update live
- [ ] Replay viewer animates a past order book for a selected time window
- [ ] Audit viewer loads, filters, shows full payloads
- [ ] Trace ID links open correct Jaeger traces
- [ ] Dashboard panels update in near-real-time without manual refresh

---

## Phase 13 — Resilience & Fault Tolerance

**Goal:** Measurable fault tolerance. Circuit breakers, bulkheads, DLQ handling, and a runnable chaos scenario.

### 13.1 Circuit Breakers — Resilience4j

| Caller | Called | Failure Behavior |
|---|---|---|
| Order Service | Risk Service | Reject order: `RISK_SERVICE_UNAVAILABLE` |
| Order Service | Account Service | Reject order: `ACCOUNT_SERVICE_UNAVAILABLE` |
| Gateway | Auth Service | Return 503: `AUTH_SERVICE_UNAVAILABLE` |

Config example:
```yaml
resilience4j.circuitbreaker:
  instances:
    riskServiceCall:
      slidingWindowSize: 10
      failureRateThreshold: 50
      waitDurationInOpenState: 10s
      permittedNumberOfCallsInHalfOpenState: 3
```

Circuit breaker state exported as Prometheus gauge. Visible in Grafana Circuit Breakers dashboard.

### 13.2 Bulkheads

- Gateway uses separate `ThreadPoolBulkhead` per downstream service
- Slow order service does not exhaust threads handling account service requests

### 13.3 Kafka Retry + DLQ

Every Kafka consumer:
- Retry up to 3 times with exponential backoff (1s, 2s, 4s)
- After 3 failures → route to `.DLQ` topic with full error metadata
- `GET /admin/dlq/{topic}`: inspect DLQ entries
- `POST /admin/dlq/{topic}/reprocess`: replay messages from DLQ

### 13.4 Chaos Engineering Scenario (script: `scripts/chaos/kill_matching_engine.sh`)

```
Steps:
  1. Record current sequence number and trade count from Grafana / audit log
  2. kubectl delete pod matching-engine-<hash>
  3. Observe in Grafana:
       - kafka_consumer_lag rises
       - circuit_breaker_state transitions to OPEN in Order Service
       - new order rejections with MATCHING_ENGINE_UNAVAILABLE
  4. Pod restarts; replays Kafka from committed offset; resumes processing
  5. Observe:
       - kafka_consumer_lag returns to 0
       - circuit_breaker_state returns to CLOSED
  6. Verify audit log:
       - No trades duplicated (settlement records unique by trade_id)
       - No trades lost (sequence numbers contiguous)
  7. Record recovery time; commit to benchmarks/chaos_results.md
```

### Phase 13 Completion Criteria

- [ ] Circuit breaker trips when Risk Service pod killed; orders cleanly rejected
- [ ] Circuit breaker transitions OPEN → HALF_OPEN → CLOSED on recovery; visible in Grafana
- [ ] Kafka DLQ receives poison-pill messages after retry exhaustion
- [ ] DLQ reprocess endpoint works
- [ ] Chaos script runs end-to-end; results committed to `benchmarks/`
- [ ] No duplicate trades in audit log after matching engine restart

---

## Phase 14 — Docker & K3s Deployment

**Goal:** Every service containerized; full system deploys to K3s via single `helm install`.

### 14.1 Dockerfiles

Multi-stage Dockerfile per service:

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /app
COPY . .
RUN ./gradlew :services:{service}:bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /app/services/{service}/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### 14.2 Helm Chart Structure

```
infra/helm/dete/
├── Chart.yaml
├── values.yaml
├── values-prod.yaml
├── templates/
│   ├── _helpers.tpl
│   ├── gateway/
│   ├── auth/
│   ├── order/
│   ├── risk/
│   ├── account/
│   ├── matching-engine/
│   ├── market-data/
│   ├── audit/
│   ├── simulator/
│   ├── frontend/
│   ├── postgres/         # StatefulSet
│   ├── redis/            # StatefulSet
│   ├── kafka/            # StatefulSet
│   └── monitoring/       # Prometheus + Grafana
└── dashboards/           # Grafana dashboard JSONs (auto-provisioned)
```

### 14.3 Kubernetes Requirements Per Service

Every service deployment must have:
- `resources.requests` and `resources.limits` (CPU + memory)
- `readinessProbe`: `GET /actuator/health/readiness`
- `livenessProbe`: `GET /actuator/health/liveness`
- `startupProbe` for slow-starting services (Matching Engine during Kafka replay)
- `ConfigMap` for non-secret config; `Secret` for DB passwords, JWT keys, Redis password
- `NetworkPolicy` restricting traffic to only declared consumers

### 14.4 Resource Budget (2 vCPU / 12 GB constraint)

| Service | CPU Request | Mem Request |
|---|---|---|
| Matching Engine | 400m | 512Mi |
| Order Service | 200m | 384Mi |
| Account Service | 200m | 384Mi |
| Market Data | 200m | 256Mi |
| Gateway | 150m | 256Mi |
| Auth | 100m | 256Mi |
| Risk | 100m | 256Mi |
| Audit | 100m | 256Mi |
| Simulator | 150m | 256Mi |
| Kafka | 300m | 1Gi |
| Postgres | 200m | 1Gi |
| Redis | 100m | 256Mi |
| Prometheus | 200m | 512Mi |
| Grafana | 100m | 256Mi |
| Frontend | 100m | 128Mi |

### 14.5 Ingress + TLS

- `dete.yourdomain.com/api/*` → Gateway
- `dete.yourdomain.com/*` → Frontend
- `dete.yourdomain.com/grafana/*` → Grafana
- `dete.yourdomain.com/jaeger/*` → Jaeger UI
- TLS via cert-manager + Let's Encrypt

### Phase 14 Completion Criteria

- [ ] `helm install dete ./infra/helm/dete` deploys the full system
- [ ] All services pass readiness and liveness probes
- [ ] Frontend accessible at public domain with TLS
- [ ] Grafana dashboards available without manual import
- [ ] `kubectl top pods` shows no service exceeding memory limit

---

## Phase 15 — CI/CD Pipeline

**Goal:** Every push to `main` triggers: lint → test → build → deploy → smoke test.

### 15.1 `ci.yml` — Pull Request Pipeline (parallel jobs)

1. **Format check**: `./gradlew spotlessCheck`
2. **Compile**: `./gradlew compileJava`
3. **Unit tests**: `./gradlew test --tests "*UnitTest*"`
4. **Integration tests**: Testcontainers (Postgres, Kafka, Redis)
5. **Architecture tests**: ArchUnit
6. **Security scan**: Trivy / Grype on dependencies
7. **Frontend**: `npm run lint && npm run type-check`

### 15.2 `cd.yml` — Deployment Pipeline (runs after ci.yml passes on main)

1. Docker build + push all service images (tagged with git SHA) to GHCR
2. `helm lint ./infra/helm/dete`
3. `helm upgrade --install dete ./infra/helm/dete --set image.tag={SHA}` via SSH to Oracle VPS
4. **Smoke tests** (automated):
   - Register test user → Login → Place limit order → Wait for fill (bot fills it) → Assert status=FILLED → Assert balance changed → Delete test user
5. Post deploy summary to GitHub commit status

### 15.3 `benchmarks.yml` — Performance Regression Check (nightly / manual)

1. Run JMH benchmarks on self-hosted Oracle VPS runner
2. Compare against baseline in `benchmarks/baseline.json`
3. If P99 latency regressed >20% → fail job, post warning
4. Commit updated results to `benchmarks/results/{date}.json`

### Phase 15 Completion Criteria

- [ ] CI runs on every PR; no PR can merge with failing tests
- [ ] CD deploys on push to main with zero manual steps
- [ ] Smoke tests pass: order placed → filled → settled
- [ ] Benchmark regression check runs and compares against baseline

---

## Phase 16 — Performance Engineering

**Goal:** Measure everything. Documented, committed benchmark results on actual 2 vCPU hardware.

### 16.1 JMH Benchmarks (in `benchmarks/` module)

| Benchmark | What it Measures | Target |
|---|---|---|
| `OrderBookMatchingBenchmark.thrpt` | Pure matching throughput (no Kafka) | >100k orders/sec |
| `MatchingLatencyBenchmark.avgt` | P50 / P99 matching latency | P99 < 1ms |
| `AllocationRateBenchmark` | Object allocation rate on hot path | Near-zero allocs |
| `TreeMapVsSkipListBenchmark` | Price level structure comparison | Document winner |
| `FixedPointVsBigDecimalBenchmark` | Fixed-point vs BigDecimal speed | Document delta |

### 16.2 k6 Load Tests (`infra/k6/`)

| Script | Scenario | Success Criteria |
|---|---|---|
| `order_placement.js` | 500 concurrent users, 1 order/sec, 60s | P99 < 500ms, errors < 0.1% |
| `websocket_stress.js` | 2000 concurrent WS connections receiving order book | No drops, no OOM |
| `settlement_load.js` | 1000 trades/sec sustained 30s | All settle, no balance drift |

### 16.3 End-to-End Latency Measurement

- `T0`: Gateway receives `POST /orders`
- `T1`: Matching Engine publishes `TradeExecutedEvent`
- `T2`: Market Data delivers fill to WebSocket client
- Measure `T2 - T0`; target: P99 < 200ms on 2 vCPU VPS

### 16.4 GC Tuning

- Run Matching Engine with G1GC initially
- Benchmark GC pause impact on latency tail
- Evaluate ZGC or Shenandoah if P99 spikes during GC
- Document findings in `benchmarks/gc_tuning.md`

### 16.5 Result Format

```
benchmarks/
├── baseline.json
├── results/
│   ├── YYYY-MM-DD_matching.md
│   ├── YYYY-MM-DD_k6_load.md
│   └── YYYY-MM-DD_gc_tuning.md
└── chaos_results.md
```

### Phase 16 Completion Criteria

- [ ] JMH benchmarks run; results committed
- [ ] k6 load tests pass all success criteria on actual VPS
- [ ] E2E latency P99 < 200ms documented
- [ ] GC tuning analysis documented and configuration committed
- [ ] TreeMap vs skip-list comparison documented with winner rationale

---

## Phase 17 — Hardening, Testing & Final Polish

**Goal:** Complete test coverage, runbook, chaos run, and a verified live demo.

### 17.1 ArchUnit Tests

Enforce in every service:

- Controllers do not import domain/service layer packages directly
- No service imports another service's internal packages (only `common-*`)
- No `java.util.logging` — only SLF4J
- No `float` or `double` in any class under `financial.*` or `matching.*`
- Kafka consumers annotated `@Transactional` or explicitly documented as idempotent

### 17.2 JCStress Concurrency Tests

- Concurrent `ReserveFunds` on same account → no negative balance
- Concurrent cancel + fill on same order ID → exactly one wins
- Concurrent outbox polling → same message not published twice

### 17.3 Property-Based / Invariant Tests

Using jqwik or similar:

- Any random sequence of orders: fill price never outside submitted price range
- Any random ledger operations: `available + reserved = total` always holds
- Any random order event sequence: status transitions are monotonically forward (no FILLED → PARTIALLY_FILLED)

### 17.4 Reconciliation Job

Scheduled nightly:
1. Sum `available + reserved` across all accounts per asset
2. Sum all CREDIT minus DEBIT ledger entries per asset
3. Assert they match
4. On mismatch: publish to `system.alerts` Kafka topic and log CRITICAL

### 17.5 Operational Runbook (`docs/runbook.md`)

Complete the runbook covering:

- **Matching Engine crash recovery**: which Kafka offset, how to verify no events lost
- **Balance drift detection**: how to run reconciliation job manually and interpret results
- **DLQ reprocessing**: how to inspect and replay dead-letter messages
- **Secret rotation**: JWT key and Redis password without downtime
- **Adding a new instrument**: step-by-step end-to-end
- **Grafana runbook**: what each panel means, actions on anomalies
- **Chaos recovery checklist**: post-pod-kill verification steps

### 17.6 Final Demo Walk-Through (10-step verification on live system)

1. Open `/dashboard` without login — verify all panels live and updating
2. Click "Try Demo" — verify instant login and funded account
3. Place a limit buy order — verify in Open Orders
4. Watch order book — bot fills the order within seconds
5. Verify account balance: BTC increased, USD decreased
6. Open Audit Viewer — find exact audit entries for the trade
7. Click trace ID — verify full trace in Jaeger
8. Open Grafana — find matching latency spike from the fill
9. Run chaos script — kill matching engine pod — verify Grafana recovery
10. Verify audit log shows no duplicate trades after recovery

### Phase 17 Completion Criteria

- [ ] ArchUnit tests enforce all listed rules
- [ ] JCStress tests pass for all concurrency scenarios
- [ ] Property tests pass for matching engine and ledger invariants
- [ ] Reconciliation job reports no drift on seeded data
- [ ] `docs/runbook.md` complete for all scenarios
- [ ] All 10 final demo steps pass on live deployed system
- [ ] `README.md` contains: public URL, demo credentials, architecture diagram, benchmark summary

---

## Cross-Cutting Concerns (Apply Throughout All Phases)

### Consistent Error Response Format

```json
{
  "errorCode": "INSUFFICIENT_FUNDS",
  "message": "Available balance (500 USD) is less than required (1000 USD)",
  "traceId": "4bf92f3577b34da6a3ce929d0e0e4736",
  "timestamp": "2025-01-15T10:30:00Z"
}
```

### Logging Standard

Every log line: structured JSON with `timestamp`, `level`, `service`, `traceId`, `spanId`, `message`, and relevant domain fields (`orderId`, `accountId`, etc.).

### Secret Management

- Never commit secrets
- Kubernetes Secrets in production; `.env` files (gitignored) in local dev
- Required variables documented in `docs/env.md`

### Database Migration Rule

Every schema change = new Flyway migration file. Never modify existing migration files. Always test migrations on a clean database in CI.

### Kafka Message Contract Rule

Every Kafka message must include: `eventId` (UUID), `eventType` (string), `timestamp` (ISO-8601), `version` (integer). These fields are never removed in schema evolution.

### Financial Arithmetic Rule

`double` and `float` are banned in all financial calculations. Use `long` fixed-point (8 decimal places, `SCALE = 100_000_000L`) everywhere.

---

## Locked Dependency Versions

| Dependency | Version |
|---|---|
| Java | 21 |
| Spring Boot | 3.3.x |
| Spring Cloud Gateway | 4.x |
| Kafka Clients | 3.7.x |
| jOOQ | 3.19.x |
| Flyway | 10.x |
| Resilience4j | 2.x |
| OpenTelemetry Java Agent | 2.x |
| Testcontainers | 1.19.x |
| JMH | 1.37 |
| Micrometer | 1.13.x |
| Next.js | 14.x |
| lightweight-charts | 4.x |
| k6 | 0.52.x |

