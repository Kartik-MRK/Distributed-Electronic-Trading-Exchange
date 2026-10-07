# DETE — Project Phase Progress Tracker

> **Tracking Document**: Real-time progress and completion record for the Distributed Electronic Trading Exchange (DETE).  
> Reference: [Build Plan — Phase by Phase.md](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/Build%20Plan%20%E2%80%94%20Phase%20by%20Phase.md)

---

## Progress Overview

| Metric | Status |
|---|---|
| **Total Phases** | 18 (Phase 0 to 17) |
| **Completed** | 15 / 18 (83.3%) |
| **Current Focus** | **Phase 15 — CI/CD Pipeline** |
| **Progress Bar** | `[███████████████░░░]` |

---

## Phase Status Summary

| Phase | Name | Status | Completion Date |
|:---:|---|:---:|:---:|
| **0** | **Foundation & Repository Setup** | **Completed** | Oct 04, 2026 |
| **1** | **Authentication Service** | **Completed** | Oct 04, 2026 |
| **2** | **Account / Ledger Service** | **Completed** | Oct 04, 2026 |
| **3** | **Matching Engine (Core)** | **Completed** | Oct 04, 2026 |
| **4** | **Order Service + Kafka Integration** | **Completed** | Oct 04, 2026 |
| **5** | **Risk Service** | **Completed** | Oct 04, 2026 |
| **6** | **Market Data Service** | **Completed** | Oct 05, 2026 |
| **7** | **Audit Service** | **Completed** | Oct 05, 2026 |
| **8** | **Gateway Service** | **Completed** | Oct 05, 2026 |
| **9** | **Frontend Phase A (Trading Terminal)** | **Completed** | Oct 05, 2026 |
| **10** | **Observability Stack** | **Completed** | Oct 05, 2026 |
| **11** | **Simulator / Market-Maker Bot Service** | **Completed** | Oct 05, 2026 |
| **12** | **Frontend Phase B (Dashboard & Replay)** | **Completed** | Oct 05, 2026 |
| **13** | **Resilience & Fault Tolerance** | **Completed** | Oct 05, 2026 |
| **14** | **Deployment — Docker & K3s (Helm)** | **Completed** | Oct 07, 2026 |
| **15** | CI/CD Pipeline | *Up Next* | — |
| **16** | Performance Engineering | Pending | — |
| **17** | Hardening & Polish | Pending | — |

---

## Completed Phases

### Phase 0 — Foundation & Repository Setup
- **Status:** **Completed** (Oct 04, 2026)
- **Completed Components:**
  - **Mono-repo Architecture:** Gradle multi-project setup with 13 modules (`libs/` and `services/`), unified dependency catalog ([gradle/libs.versions.toml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/gradle/libs.versions.toml)), Microsoft OpenJDK 21 LTS toolchain, and Spotless formatting enforcement.
  - **Shared Domain Library ([libs/common-domain](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-domain)):** Nano-scale fixed-point arithmetic (`FixedPoint`, 8 decimals, no floating-point on financial paths), typed identifier wrappers (`OrderId`, `AccountId`, `TradeId`, `InstrumentId`), domain financial wrappers (`Price`, `Quantity`, `Money`), and core enums (`Instrument`, `OrderSide`, `OrderType`, `OrderStatus`) with 21 passing unit tests.
  - **Shared Events Library ([libs/common-events](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-events)):** Immutable Java records for Kafka contracts across order lifecycle, matching engine executions, balance reservation/settlement, and audit trails.
  - **Shared Security Library ([libs/common-security](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-security)):** RS256 JWT parsing and authentication filter for Spring Security.
  - **Shared Test Library ([libs/common-test](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-test)):** Testcontainers base classes (`KafkaTestContainerBase`, `PostgresTestContainerBase`, `RedisTestContainerBase`, `FullStackTestBase`), `TestDataBuilders`, and passing Kafka producer/consumer integration test.
  - **Local Infrastructure ([infra/docker-compose.yml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/infra/docker-compose.yml)):** Multi-container stack (Postgres 16 with pre-provisioned schemas, Redis 7, ZooKeeper, Kafka 7.6.2 broker, Kafka UI on port 8080, Prometheus, Grafana, Jaeger) running and healthy.
  - **Kafka Topics Provisioned:** 13 topics created (partitions=3 for operational feeds, partitions=1 for DLQs/alerts) and active in broker.

### Phase 1 — Authentication Service
- **Status:** **Completed** (Oct 04, 2026)
- **Completed Components:**
  - **Database Persistence & Migrations ([services/auth/src/main/resources/db/migration/V1__create_auth_schema.sql](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/resources/db/migration/V1__create_auth_schema.sql)):** PostgreSQL `auth.users` and `auth.refresh_tokens` tables with UUID primary keys, foreign key cascading, and b-tree indexes on lookup columns (`username`, `email`, `user_id`, `token_hash`).
  - **Password Security ([PasswordService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/service/PasswordService.java)):** BCrypt strength 12 password hashing and verification.
  - **RS256 JWT & Token Rotation Engine ([JwtService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/service/JwtService.java), [JwtKeyProvider](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/service/JwtKeyProvider.java)):** Short-lived RS256 access tokens (15m, signed with 2048-bit RSA private key) carrying claims (`sub`, `username`, `roles`), cryptographically random refresh tokens (7d, SHA-256 hashed in DB with single-use rotation), and public JWKS endpoint (`GET /auth/.well-known/jwks.json`).
  - **Sliding-Window Rate Limiting ([RateLimiterService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/service/RateLimiterService.java)):** Atomic Redis Lua script enforcing 5 login attempts/minute per IP, rejecting breaches with HTTP 429 and `Retry-After` header.
  - **Demo User Startup Seeder ([DemoUserSeeder](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/service/DemoUserSeeder.java)):** Automatic bootstrap of `demo@dete.io` / `demo` / `DemoPassword123!` on startup when `auth.demo.enabled=true`.
  - **Kafka Audit Event Publishing ([AuditProducerService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/service/AuditProducerService.java)):** Asynchronous event dispatch to topic `audit.events` for `USER_REGISTERED`, `USER_LOGIN`, `USER_LOGOUT`, and `TOKEN_REFRESHED`.
  - **REST API & Exception Handling ([AuthController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/controller/AuthController.java), [GlobalExceptionHandler](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/controller/GlobalExceptionHandler.java)):** Complete endpoints (`POST /auth/register`, `POST /auth/login`, `POST /auth/refresh`, `POST /auth/logout`, `GET /auth/me`, `GET /auth/.well-known/jwks.json`) with RFC-compliant error envelopes and validation.
  - **Security Filter Chain ([SecurityConfig](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/config/SecurityConfig.java)):** Stateless Spring Security filter chain with `JwtAuthenticationFilter` protecting `/auth/me` and permitting public auth routes.
  - **Verification & Test Suite (20 Passing Tests):** Unit tests, ArchUnit structural tests, and full end-to-end integration tests verifying lifecycle, token rotation, demo seeding, and rate limiting with Testcontainers.

### Phase 2 — Account / Ledger Service
- **Status:** **Completed** (Oct 04, 2026)
- **Completed Components:**
  - **Database Persistence & Invariants ([services/account/src/main/resources/db/migration/V1__create_account_schema.sql](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/resources/db/migration/V1__create_account_schema.sql)):**
    - `account.accounts`: Master account table.
    - `account.balances`: Per-asset available and reserved balances with strict PostgreSQL check constraints (`CHECK (available >= 0)`, `CHECK (reserved >= 0)`, and `CONSTRAINT total_non_negative CHECK (available + reserved >= 0)`).
    - `account.ledger_entries`: Append-only, double-entry financial journal.
    - **Database-Level Immutability Enforcement:** Dedicated PostgreSQL trigger `trg_ledger_immutable` preventing any `UPDATE` or `DELETE` on `account.ledger_entries`.
    - `account.settlements`: Authoritative trade settlement records with unique constraint on `trade_id` for idempotency.
    - `account.outbox`: Transactional outbox table for reliable at-least-once event delivery.
  - **Persistence Repositories ([services/account/src/main/java/com/dete/account/repository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/repository)):**
    - [AccountRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/repository/AccountRepository.java): Account creation and lookup.
    - [BalanceRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/repository/BalanceRepository.java): Atomic SQL balance mutation (`reserve`, `release`, `creditAvailable`, `debitReserved`, `debitAvailable`) with conditional checks in SQL.
    - [LedgerRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/repository/LedgerRepository.java): Per-account sequential entry numbering, entry persistence, and pagination.
    - [SettlementRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/repository/SettlementRepository.java): Idempotency check via `existsByTradeId`, trade settlement persistence, and history queries.
    - [OutboxRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/repository/OutboxRepository.java): `FOR UPDATE SKIP LOCKED` querying for safe concurrent outbox batch processing.
  - **Core Financial Engine ([AccountService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/service/AccountService.java)):**
    - `deposit`: Credits available balance and records `DEPOSIT` journal entry.
    - `reserveFunds`: Atomically decrements available and increments reserved within a single transaction, records `RESERVE` entry, writes `BalanceReservedEvent` and `AuditEvent` to outbox.
    - `releaseFunds`: Atomically decrements reserved and increments available, records `RELEASE` entry, writes `BalanceReleasedEvent` and `AuditEvent` to outbox.
    - `settleTrade`: Double-entry settlement of matched trades, debiting buyer reserved quote / crediting base, debiting seller reserved base / crediting quote, writing 4 distinct double-entry journal records, and producing `BalanceSettledEvent` + `AuditEvent` to outbox.
    - `Idempotency`: Automatically skips duplicate trade settlements cleanly without creating redundant balance entries.
  - **Transactional Outbox Worker ([OutboxPublisher](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/service/OutboxPublisher.java)):** Scheduled poller delivering unpublished events to Kafka (`ledger.events`, `audit.events`) with synchronous ack and `published = true` status updates.
  - **Trade Settlement Kafka Consumer ([TradeSettlementConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/service/TradeSettlementConsumer.java)):** Consumes `TradeExecutedEvent` from `trade.executions` and drives double-entry settlement.
  - **REST API Endpoints ([AccountController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/controller/AccountController.java), [InternalAccountController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/controller/InternalAccountController.java)):**
    - User Endpoints: `GET /accounts/me/balances`, `GET /accounts/me/ledger`, `GET /accounts/me/trades`, `POST /accounts/deposit`.
    - Internal Endpoints: `POST /internal/accounts/create`, `POST /internal/accounts/reserve`, `POST /internal/accounts/release`, `GET /internal/accounts/{accountId}/balances/{asset}`.
  - **Verification & Test Suite (16 Passing Tests):**
    - Unit tests ([AccountServiceUnitTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/test/java/com/dete/account/service/AccountServiceUnitTest.java)) verifying deposit, reserve, release, 4-way settlement, and duplicate idempotency.
    - Property tests ([BalancePropertyTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/test/java/com/dete/account/property/BalancePropertyTest.java)) mathematically proving the financial invariant `available >= 0`, `reserved >= 0`, and `available + reserved == total` across randomized operation sequences using jqwik.
    - Architecture tests ([AccountArchitectureTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/test/java/com/dete/account/arch/AccountArchitectureTest.java)) enforcing strict separation between controllers and repositories.
    - Concurrency test proving that simultaneous reservation attempts never over-reserve or cause negative balances under high contention.
    - End-to-end integration tests ([AccountIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/test/java/com/dete/account/AccountIntegrationTest.java)) on Testcontainers (Postgres + Redis + Kafka) testing full account lifecycle, double-entry settlement, trigger immutability, database check constraints, and transactional outbox event delivery.

### Phase 3 — Matching Engine (Core)
- **Status:** **Completed** (Oct 04, 2026)
- **Completed Components:**
  - **In-Memory Order Book Data Structures ([com.dete.matching.engine](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/engine)):**
    - [BookOrder](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/engine/model/BookOrder.java): High-performance, mutable in-memory order representation tracking fixed-point quantities, price, side, type, sequence number, and status.
    - [PriceLevel](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/engine/model/PriceLevel.java): Strict FIFO queue (`ArrayDeque<BookOrder>`) enforcing price-time priority within identical price levels.
    - [OrderBook](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/engine/OrderBook.java): In-memory limit order book for an instrument using dual `TreeMap`s (`bids` descending, `asks` ascending), an `orderIndex` `HashMap` for O(1) order lookups, and a monotonically increasing sequence number.
  - **Matching Algorithms & Execution Rules:**
    - `Limit Orders`: Walks crossing price levels, matches at maker's resting price (price improvement), records trades and fill events, rests unfilled remainder in book.
    - `Market Orders`: Aggressively sweeps opposite side of book without price limit; immediately cancels unfilled remainder when liquidity exhausts; rejects with clear reason when book is empty.
    - `IOC (Immediate-or-Cancel)`: Matches available crossing liquidity; cancels unfilled remainder immediately with zero resting.
    - `FOK (Fill-or-Kill)`: Atomically inspects available depth across price levels first; fully matches if sufficient liquidity exists, or rejects immediately with zero fills and zero book modification.
    - `Self-Trade Prevention (STP)`: Automatically detects and skips resting orders belonging to the same account ID to prevent wash trading.
    - `O(1) Cancellation`: Immediate lookup in `orderIndex`, removal from `PriceLevel`, level pruning if empty, emitting `OrderCancelledEvent`.
    - `Modify Order`: Implements cancel-and-reinsert with a fresh sequence number, placing modified quantity at the back of the queue to preserve time priority invariants.
  - **Single-Writer Thread Model ([MatchingEngineService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/service/MatchingEngineService.java)):** Dedicated single-threaded executor per trading instrument (`BTC_USD`, `ETH_USD`, `SOL_USD`), ensuring 100% deterministic, zero-lock, race-condition-free execution without database bottlenecks on the matching path.
  - **Kafka Event Streaming & Atomic Dispatch ([MatchingEventPublisher](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/publisher/MatchingEventPublisher.java), [OrderCommandConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/consumer/OrderCommandConsumer.java)):** Consumes incoming order commands (`OrderPlacedEvent`, `OrderCancelCommand`, `OrderModifyCommand`) from `order.commands`, processes on instrument single-writer thread, and atomically dispatches resulting `TradeExecutedEvent`s to `trade.executions` and order lifecycle events (`OrderAcceptedEvent`, `OrderFilledEvent`, `OrderPartiallyFilledEvent`, `OrderCancelledEvent`, `OrderRejectedEvent`) to `order.events`.
  - **Deterministic State Reconstruction & Replay ([KafkaReplayService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/replay/KafkaReplayService.java)):** Replays the complete `order.commands` Kafka log from offset 0 upon engine startup, accurately rebuilding all in-memory order books and sequence numbers without duplicating outbound trade events.
  - **REST API & Level 2 Depth ([MatchingEngineController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/controller/MatchingEngineController.java)):** Endpoints for querying live aggregated L2 order book depth (`GET /matching/orderbook/{symbol}?depth=10`), engine operational statistics (`GET /matching/orderbook/{symbol}/stats`), and triggering state replay (`POST /matching/replay`).
  - **JMH Microbenchmarking ([OrderBookBenchmark](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/benchmark/OrderBookBenchmark.java)):** In-memory performance exceeding 3.02 million ops/sec for order cancellations, 1.76 million ops/sec for resting limit order insertions, and 1.21 million ops/sec for crossing order matches.
  - **Verification & Test Suite (18 Passing Tests across matching-engine):**
    - Unit tests ([OrderBookUnitTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/test/java/com/dete/matching/engine/OrderBookUnitTest.java)) verifying price improvement, price-time FIFO priority, market orders, IOC, FOK, O(1) cancel, self-trade prevention, cancel-and-reinsert modify, and L2 depth aggregation.
    - Property tests ([MatchingEnginePropertyTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/test/java/com/dete/matching/property/MatchingEnginePropertyTest.java)) mathematically proving that the order book is never crossed (`bestBid < bestAsk`), volume conservation holds (`totalResting + matched/2 <= totalSubmitted`), and self-trade prevention preserves distinct counterparty invariants across 200 randomized execution cycles via jqwik.
    - Architecture tests ([MatchingArchitectureTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/test/java/com/dete/matching/arch/MatchingArchitectureTest.java)) verifying with ArchUnit that the matching engine hot path has zero database, JDBC, JPA, or Spring dependencies.
    - End-to-end integration tests ([MatchingEngineIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/test/java/com/dete/matching/MatchingEngineIntegrationTest.java)) with Testcontainers Kafka validating full command consumption, trade execution dispatch, order cancellation, order modification, Kafka log replay reconstruction, and REST API monitoring.

### Phase 4 — Order Service + Kafka Integration
- **Status:** **Completed** (Oct 04, 2026)
- **Completed Components:**
  - **Database Persistence & Outbox Migrations ([services/order/src/main/resources/db/migration/V1__create_order_schema.sql](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/resources/db/migration/V1__create_order_schema.sql)):** PostgreSQL `order_svc.orders` and `order_svc.outbox` tables with UUID primary keys, b-tree indexes on `(account_id, status)` and `created_at DESC`, unique constraint on `idempotency_key`, and partial index on unpublished outbox messages (`WHERE published = false`).
  - **Two-Tier Distributed Idempotency ([IdempotencyService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/service/IdempotencyService.java)):** Fast-path Redis cache (TTL=24h) checking incoming `Idempotency-Key` headers, paired with database unique constraint fallback to guarantee identical responses on duplicate submissions with zero duplicate balance reservations or database insertions.
  - **Pre-Trade Risk Checks & Synchronous Fund Reservation ([DefaultPreTradeRiskValidator](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/client/DefaultPreTradeRiskValidator.java), [HttpAccountClient](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/client/HttpAccountClient.java)):** Pre-trade validation enforcing strictly positive prices/quantities, instrument size limits (100 BTC, 1,000 ETH, 10,000 SOL), and single-order notional limits ($5,000,000 USD). Synchronous balance reservations with Account Service before order placement with Resilience4j circuit breaker integration (`riskService`, `accountService`).
  - **Transactional Outbox Dispatcher ([OrderOutboxPublisher](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/service/OrderOutboxPublisher.java), [OrderOutboxRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/repository/OrderOutboxRepository.java)):** Scheduled polling worker querying unpublished outbox records using `FOR UPDATE SKIP LOCKED` and reliably publishing `OrderPlacedEvent`, `OrderCancelCommand`, and `OrderModifyCommand` to Kafka topic `order.commands`.
  - **Order Events Kafka Consumer ([OrderEventConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/consumer/OrderEventConsumer.java)):** Consumes `order.events` to drive order lifecycle state transitions (`ACCEPTED`, `PARTIALLY_FILLED`, `FILLED`, `CANCELLED`, `REJECTED`). Automatically triggers fund releases via Account Service when orders are cancelled or rejected.
  - **Dead-Letter Queue (DLQ) & Admin Reprocessing ([OrderEventConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/consumer/OrderEventConsumer.java), [DlqAdminController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/controller/DlqAdminController.java)):** Automatic error routing for unparseable or poisoned messages to `order.events.DLQ`, paired with administrative endpoints `GET /admin/dlq/messages` (peeking DLQ) and `POST /admin/dlq/reprocess` (reprocessing back to main topic).
  - **REST API Endpoints ([OrderController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/controller/OrderController.java)):** Complete endpoints: `POST /orders`, `DELETE /orders/{orderId}`, `PUT /orders/{orderId}`, `GET /orders/{orderId}`, `GET /orders` (active orders), `GET /orders/history` (paginated history), and `/admin/dlq/**`.
  - **Verification & Test Suite (27 Passing Tests across order service):**
    - Unit tests ([OrderServiceUnitTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/service/OrderServiceUnitTest.java), [PreTradeRiskValidatorTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/client/PreTradeRiskValidatorTest.java), [IdempotencyServiceTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/service/IdempotencyServiceTest.java)) verifying placement, fund calculations, idempotency caching, cancellation, modification, and risk thresholds.
    - ArchUnit tests ([OrderArchitectureTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/arch/OrderArchitectureTest.java)) enforcing layer boundary separation (controllers in `controller`, repositories in `repository`, controllers decoupled from repositories).
    - Resilience4j Circuit Breaker & Fail-Closed tests ([OrderRiskCircuitBreakerTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/client/OrderRiskCircuitBreakerTest.java)) proving that when Risk Service is down or breaker is OPEN, orders are strictly rejected with `PreTradeRiskException` and circuit breaker transitions to OPEN.
    - End-to-end integration tests ([OrderIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/OrderIntegrationTest.java)) on Testcontainers (Postgres, Kafka, Redis) validating order placement, fund reservations, outbox polling & Kafka dispatch, duplicate idempotency deduplication, order event transitions, cancellation fund release, order modification, and DLQ routing/reprocessing.

### Phase 5 — Risk Service
- **Status:** **Completed** (Oct 04, 2026)
- **Completed Components:**
  - **gRPC Contract & Wire Protocol ([RiskGrpcContracts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/grpc/RiskGrpcContracts.java), [ValidateOrderRequest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-domain/src/main/java/com/dete/common/domain/risk/ValidateOrderRequest.java), [ValidateOrderResponse](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-domain/src/main/java/com/dete/common/domain/risk/ValidateOrderResponse.java)):** Defined `ValidateOrder` RPC over standard HTTP/2 Netty framing using streaming JSON marshallers without requiring external `protoc` binaries on Windows.
  - **Six Pre-Trade Risk Rules ([RiskRuleEvaluator](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/engine/RiskRuleEvaluator.java)):**
    - `Rule 1: Max Order Size`: Rejects orders exceeding configured instrument thresholds (100 BTC, 1,000 ETH, 10,000 SOL).
    - `Rule 2: Max Order Notional`: Enforces $5,000,000 USD limit calculated via 64-bit fixed-point arithmetic (`FixedPoint.multiply(price, quantity)`).
    - `Rule 3: Max Concurrent Open Orders`: Rejects orders when an account reaches 50 concurrent active open orders, automatically adjusting on fills and cancels.
    - `Rule 4: Price Deviation Band`: Rejects BUY limit orders priced > +10% above the last trade price and SELL limit orders priced < -10% below the last trade price.
    - `Rule 5: Market Order Guard`: Requires a live reference trade price to exist before permitting market execution, and enforces market order quantities <= 50% of the instrument's maximum order size.
    - `Rule 6: Self-Trade Prevention Check`: Tracks resting order price levels per account and side; rejects incoming orders that would cross or immediately execute against the account's own resting orders.
  - **Real-Time In-Memory State & Kafka Tracking ([RiskTradeEventConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/consumer/RiskTradeEventConsumer.java), [RiskOrderEventConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/consumer/RiskOrderEventConsumer.java), [AccountRiskState](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/model/AccountRiskState.java)):**
    - Consumes `trade.executions` to maintain up-to-the-millisecond last trade prices per instrument.
    - Consumes `order.events` to clean up terminal states (`ORDER_FILLED`, `ORDER_CANCELLED`, `ORDER_REJECTED`), decrementing open orders and releasing resting prices.
  - **gRPC Server Lifecycle ([RiskGrpcServer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/grpc/RiskGrpcServer.java), [RiskGrpcService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/grpc/RiskGrpcService.java)):** Manages Netty gRPC server lifecycle cleanly integrated into Spring Boot `SmartLifecycle` (port 9095).
  - **Order Service Client with Fail-Closed Circuit Breaking ([GrpcRiskClient](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/client/GrpcRiskClient.java)):**
    - Replaces local checks with high-performance gRPC pre-trade risk call before fund reservation.
    - Wrapped with Resilience4j `@CircuitBreaker(name = "riskService", fallbackMethod = "riskFallback")`.
    - Enforces **Fail Closed** invariant: any connection failure, gRPC deadline, or OPEN circuit breaker state strictly throws `PreTradeRiskException` to reject the order (never bypass risk).
    - Circuit breaker state and failure counters exported via Actuator Prometheus metrics (`/actuator/metrics`).
  - **REST & Monitoring API ([RiskController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/controller/RiskController.java)):** Endpoints for querying reference prices (`GET /risk/instruments`), active rule limits (`GET /risk/rules`), account risk statistics (`GET /risk/accounts/{id}`), and standalone validation testing (`POST /risk/validate`).
  - **Verification & Test Suite (26 Passing Tests across risk service):**
    - Unit tests ([RiskRuleEvaluatorTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/test/java/com/dete/risk/RiskRuleEvaluatorTest.java)) thoroughly validating all 6 rules with positive and negative test cases.
### Phase 6 — Market Data Service
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Materialized In-Memory CQRS State Models ([com.dete.marketdata.model](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/model)):**
    - [OrderBookView](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/model/OrderBookView.java): High-performance, thread-safe in-memory L2 order book representation maintaining sorted bids (descending) and asks (ascending), order index for fast partial fill/cancellation lookups, and sequence number tracking.
    - [TradeTape](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/model/TradeTape.java): Thread-safe, bounded ring buffer maintaining the last 500 trades per instrument with FIFO eviction and newest-first pagination.
    - [OHLCVManager](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/service/OHLCVManager.java): Multi-interval rolling OHLCV candle aggregator supporting all 5 intervals (`1m`, `5m`, `15m`, `1h`, `1d`), tracking open, high, low, close, volume, and trade count, with automatic window rollover and historical caching.
    - `LastTradedPrice`: Real-time atomic fixed-point execution price tracking per instrument.
  - **Historical Trade Storage for Replay ([V1__create_market_data_schema.sql](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/resources/db/migration/V1__create_market_data_schema.sql), [MarketDataTradeRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/repository/MarketDataTradeRepository.java)):**
    - PostgreSQL `market_data.trades` table with UUID primary keys and B-tree indexes on `(instrument, executed_at DESC)` and `(instrument, sequence_number DESC)`.
    - Idempotent batch insertion (`ON CONFLICT (trade_id) DO NOTHING`) and queries by time window or sequence number range for the Phase 12 Replay Viewer.
  - **Kafka Ingestion Pipeline ([com.dete.marketdata.consumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/consumer)):**
    - [TradeExecutionConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/consumer/TradeExecutionConsumer.java): Listens to `trade.executions`, updates tape, candles, last price, persists to DB, and streams over WebSocket.
    - [OrderEventConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/consumer/OrderEventConsumer.java): Listens to `order.events` and `order.commands`, materializes placed/filled/cancelled order transitions into the L2 order book, and routes private user updates.
    - [MarketDataSnapshotConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/consumer/MarketDataSnapshotConsumer.java): Listens to `market-data` for periodic full snapshots from the Matching Engine for authoritative resync.
  - **Matching Engine Periodic Snapshot Publishing ([OrderBookSnapshotPublisher](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/publisher/OrderBookSnapshotPublisher.java)):**
    - Scheduled component in Matching Engine periodically publishing full 50-level L2 snapshots (`OrderBookSnapshotEvent`) to topic `market-data`.
  - **Real-Time WebSocket & STOMP Streaming ([WebSocketConfig](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/config/WebSocketConfig.java), [MarketDataWebSocketBroadcaster](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/service/MarketDataWebSocketBroadcaster.java)):**
    - Endpoint `/ws` with SockJS fallback and STOMP message broker on `/topic`.
    - Channels: `/topic/orderbook.{instrument}`, `/topic/trades.{instrument}`, `/topic/candles.{instrument}.{interval}`, and private user channel `/topic/orders.{accountId}`.
  - **REST API Endpoints ([MarketDataController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/controller/MarketDataController.java)):**
    - `GET /market-data/{instrument}/orderbook?depth=20`: Current L2 order book snapshot.
    - `GET /market-data/{instrument}/trades?limit=100`: Recent trade tape.
    - `GET /market-data/{instrument}/candles?interval=1m&limit=100`: OHLCV candlestick series.
    - `GET /market-data/instruments`: List of all trading instruments with live prices.
    - `GET /market-data/{instrument}/price`: Last traded price.
  - **Verification & Test Suite (15 Passing Tests across market-data):**
    - Unit tests ([OrderBookViewTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/test/java/com/dete/marketdata/model/OrderBookViewTest.java), [OHLCVManagerTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/test/java/com/dete/marketdata/service/OHLCVManagerTest.java), [TradeTapeTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/test/java/com/dete/marketdata/model/TradeTapeTest.java)) verifying price level sorting, depth limits, partial/full fills, cancellations, multi-interval candle aggregation, window rollovers, and tape capacity.
    - Architecture tests ([MarketDataArchitectureTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/test/java/com/dete/marketdata/arch/MarketDataArchitectureTest.java)) verifying package conventions and Spring annotations with ArchUnit.
    - End-to-end integration tests ([MarketDataIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/test/java/com/dete/marketdata/MarketDataIntegrationTest.java)) on Testcontainers (Postgres, Kafka, Redis) validating live Kafka ingestion, DB persistence, REST queries, and real-time WebSocket STOMP subscriptions.

### Phase 7 — Audit Service
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Database Persistence & Immutability Trigger ([V1__create_audit_schema.sql](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/resources/db/migration/V1__create_audit_schema.sql)):**
    - PostgreSQL schema `audit` and table `audit.audit_log` storing `entry_id`, `event_id`, `event_type`, `subject_type`, `subject_id`, `actor_id`, `instrument`, `payload` (JSONB), `trace_id`, `event_time`, and `ingested_at`.
    - Dedicated B-Tree indexes on `(subject_id, event_time DESC)`, `(event_type, event_time DESC)`, `(instrument, event_time DESC)`, `(trace_id, event_time DESC)`, and `(event_time DESC)`.
    - **Database-Level Immutability Enforcement:** Dedicated PL/pgSQL trigger `trg_audit_immutable` attached `BEFORE UPDATE OR DELETE ON audit.audit_log`, strictly throwing an exception to prohibit any modification or deletion for compliance.
  - **Kafka Consumer Pipeline ([AuditKafkaConsumer](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/java/com/dete/audit/consumer/AuditKafkaConsumer.java)):**
    - Consumes topic `audit.events` with group ID `audit-service-group` and manual immediate acknowledgment (`AckMode.MANUAL_IMMEDIATE`).
    - Deserializes `AuditEvent` envelopes and passes to service layer.
    - Handles poison pills gracefully by logging and committing offsets.
  - **Idempotent Audit Log Repository & Service ([AuditLogRepository](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/java/com/dete/audit/repository/AuditLogRepository.java), [AuditService](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/java/com/dete/audit/service/AuditService.java)):**
    - `insert`: Atomically inserts into `audit.audit_log` using `ON CONFLICT (event_id) DO NOTHING` to guarantee idempotency on retried Kafka messages.
    - `findByEventId`: Fetches single audit record with full JSON payload.
    - `findByCriteria`: Dynamic parameterized SQL query supporting any combination of filters (`subjectId`, `instrument`, `eventType`, `traceId`, `from`, `to`, `limit`, `offset`).
  - **Admin REST API ([AuditAdminController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/java/com/dete/audit/controller/AuditAdminController.java)):**
    - `GET /admin/audit`: Filtered audit log query endpoint.
    - `GET /admin/audit/entries/{eventId}`: Detailed entry lookup returning 200 OK or 404 Not Found.
  - **Security Filter Chain ([SecurityConfig](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/java/com/dete/audit/config/SecurityConfig.java)):**
    - Stateless Spring Security filter chain with RS256 `JwtAuthenticationFilter`.
    - Enforces `ROLE_ADMIN` on `/admin/audit` and `/admin/audit/**`.
    - Returns 401 Unauthorized for unauthenticated requests and 403 Forbidden for non-admin tokens.
  - **Verification & Test Suite (15 Passing Tests across audit service):**
    - Unit tests ([AuditServiceUnitTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/test/java/com/dete/audit/service/AuditServiceUnitTest.java), [AuditAdminControllerUnitTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/test/java/com/dete/audit/controller/AuditAdminControllerUnitTest.java)) verifying argument validation, idempotency detection, and controller responses.
    - Architecture tests ([AuditArchitectureTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/test/java/com/dete/audit/arch/AuditArchitectureTest.java)) enforcing ArchUnit package and annotation conventions.
    - End-to-end integration tests ([AuditIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/test/java/com/dete/audit/AuditIntegrationTest.java)) on Testcontainers (Postgres, Kafka, Redis) validating Kafka ingestion, idempotency deduplication, trigger-level immutability blocking `UPDATE` and `DELETE`, security role validation (401/403/200), and multi-criteria query filtering.

### Phase 8 — Gateway Service
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Reactive Reverse Proxy & Dynamic Routing ([application.yml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/resources/application.yml)):**
    - High-performance, non-blocking Spring Cloud Gateway Netty engine on port 8080.
    - Configured declarative routes with circuit breaker fallbacks:
      - `/auth/**` -> Auth Service (`http://localhost:8081`)
      - `/orders/**` -> Order Service (`http://localhost:8082`)
      - `/accounts/**` -> Account Service (`http://localhost:8083`)
      - `/market-data/**` -> Market Data Service (`http://localhost:8085`)
      - `/admin/audit/**` -> Audit Service (`http://localhost:8086`)
      - `/ws/**` -> Market Data WebSocket (`ws://localhost:8085`)
    - Global CORS configuration supporting cross-origin trading terminal requests.
  - **Request Correlation & OTel Trace Propagation ([CorrelationIdGlobalFilter](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/filter/CorrelationIdGlobalFilter.java)):**
    - Ordered at `-100` (highest precedence).
    - Preserves incoming `X-Correlation-Id` / `X-Trace-Id` or generates cryptographic UUID roots.
    - Mutates downstream request headers, response headers, and Reactor subscriber context.
  - **JWT Authentication & Role Enforcement Filter ([JwtAuthGlobalFilter](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/filter/JwtAuthGlobalFilter.java)):**
    - Ordered at `-50`.
    - Whitelists public paths (`/auth/**`, `/market-data/**`, `/ws/**`, `/actuator/**`, `/fallback/**`).
    - Enforces valid RS256 JWT on all protected routes (`/orders/**`, `/accounts/**`, `/admin/**`).
    - Intercepts invalid, expired, or missing tokens with HTTP 401 Unauthorized before downstream services are contacted.
    - Enforces `ADMIN` role on `/admin/**` routes, rejecting unauthorized tokens with HTTP 403 Forbidden.
    - Injects downstream user context headers: `X-User-Id`, `X-Account-Id`, `X-Username`, and `X-User-Roles`.
  - **Reactive Redis Sliding-Window Rate Limiting ([DeteRedisRateLimiter](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/ratelimit/DeteRedisRateLimiter.java), [RateLimiterGlobalFilter](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/filter/RateLimiterGlobalFilter.java)):**
    - Ordered at `-20`.
    - Atomic Redis sorted-set Lua script enforcing strict sliding window thresholds:
      - Authenticated General: 100 req/min (`ratelimit:user:{userId}`)
      - Authenticated Order Placement (`POST /orders`): 20 req/min (`ratelimit:order:{userId}`)
      - Unauthenticated Traffic: 20 req/min per IP (`ratelimit:ip:{clientIp}`)
    - On breach: rejects with HTTP 429 Too Many Requests, calculating and setting the `Retry-After` header.
    - Fails open gracefully on transient Redis network drops to prevent total system outages.
  - **Resilience4j Circuit Breakers & Bulkhead Fallbacks ([FallbackController](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/fallback/FallbackController.java)):**
    - Circuit breaker instances configured per downstream service (`orderService`, `accountService`, `authService`, `marketDataService`, `auditService`).
    - Dedicated fallback endpoints returning HTTP 503 Service Unavailable (`ORDER_SERVICE_UNAVAILABLE`, `ACCOUNT_SERVICE_UNAVAILABLE`, etc.).
    - Bulkhead isolation: degradation or slowness in order service does not impact or exhaust resources for account service.
  - **Verification & Test Suite (20 Passing Tests across gateway service):**
    - Architecture tests ([GatewayArchitectureTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/test/java/com/dete/gateway/arch/GatewayArchitectureTest.java)) verifying controller and filter package conventions with ArchUnit.
    - Unit tests ([CorrelationIdGlobalFilterTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/test/java/com/dete/gateway/filter/CorrelationIdGlobalFilterTest.java), [JwtAuthGlobalFilterTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/test/java/com/dete/gateway/filter/JwtAuthGlobalFilterTest.java), [RateLimiterGlobalFilterTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/test/java/com/dete/gateway/filter/RateLimiterGlobalFilterTest.java)) testing filter chains, correlation ID generation, token validation, role checking, sliding window keys, and 429 generation.
    - End-to-end integration tests ([GatewayIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/test/java/com/dete/gateway/GatewayIntegrationTest.java)) on Testcontainers Redis and WireMock verifying real HTTP routing, correlation header propagation, unauthenticated rejection (downstream unreached), expired token rejection, user header injection, admin 403/200 role enforcement, real Redis sliding-window 429 throttling with `Retry-After`, and circuit breaker fallbacks.

### Phase 9 — Frontend Phase A (Trading Terminal)
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Next.js 16 + React 19 + TypeScript Setup ([frontend/](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend)):**
    - High-performance Next.js App Router project configured with strict TypeScript (`"type-check": "tsc --noEmit"`).
    - Rich cyber dark theme design system ([globals.css](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/app/globals.css)) with glassmorphic cards, HSL tailored neon palette (`#00f5a0` bids, `#ff3b69` asks, `#00d2ff` cyan, `#ffb800` amber), monospace tabular numeric formatting, pulse indicators, and price level update animations.
  - **Domain Type Definitions ([frontend/src/types/index.ts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/types/index.ts)):** Complete TypeScript interfaces mirroring Java domain contracts: `Instrument`, `OrderSide`, `OrderType`, `OrderStatus`, `PriceLevel`, `OrderBookData`, `TradeRecord`, `CandleData`, `OrderRecord`, `BalanceRecord`, `UserRecord`, `AuthResponse`, and `InstrumentMeta`.
  - **Client Authentication & Storage ([frontend/src/lib/auth.ts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/lib/auth.ts)):**
    - Secure token storage utilizing `sessionStorage` (strictly avoiding insecure `localStorage`).
    - Session management functions (`getAuthToken`, `setAuthSession`, `clearAuthSession`, `getCurrentUser`, `isAuthenticated`).
    - Dedicated 1-click Demo credentials setup (`demo` / `DemoPassword123!`).
  - **Typed REST API Client ([frontend/src/lib/api.ts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/lib/api.ts)):**
    - Base URL routed via Spring Cloud Gateway (`http://localhost:8080`).
    - Injected headers: `Authorization: Bearer <token>`, `X-Correlation-Id: <UUID>`, and `Idempotency-Key: <UUID>` on order submissions.
    - Endpoints mapped for Auth (`/auth/login`, `/auth/register`, `/auth/me`), Account balances & ledger (`/accounts/me/balances`, `/accounts/me/trades`), Orders (`POST /orders`, `GET /orders`, `DELETE /orders/{orderId}`), and Market Data.
  - **WebSocket STOMP Connection Manager ([frontend/src/lib/ws.ts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/lib/ws.ts), [frontend/src/hooks/useWebSocket.ts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/hooks/useWebSocket.ts)):**
    - Auto-reconnection engine with exponential backoff.
    - Heartbeat ping monitor measuring 5-second roundtrip latency.
    - Subscription handling for `/topic/orderbook.{instrument}`, `/topic/trades.{instrument}`, `/topic/candles.{instrument}.{interval}`, and `/topic/orders.{accountId}`.
    - Fallback simulation mode ensuring continuous, reactive visual inspection even during offline frontend development.
  - **Terminal Layout & Interactive Components ([frontend/src/components/](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/)):**
    - **Header ([Header.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/header/Header.tsx)):** Instrument switcher (`BTC_USDT`, `ETH_USDT`, `SOL_USDT`), live 24h ticker (last price, change %, high/low, volume), pulsing green/red WS connection status, latency counter, and user logout.
    - **Order Book ([OrderBook.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/orderbook/OrderBook.tsx), [useOrderBook.ts](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/hooks/useOrderBook.ts)):** Top 15 bid and top 15 ask ladders, cumulative depth percentage fill bars, live spread and spread % indicator, animated flash highlights on depth changes, and click-to-populate order price.
    - **Trade Tape ([TradeTape.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/tradetape/TradeTape.tsx)):** Scrolling feed of recent market executions with price, quantity, timestamp, and side coloration.
    - **Price Chart ([PriceChart.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/chart/PriceChart.tsx)):** TradingView `lightweight-charts` canvas candlestick renderer with interval selector (`1m`, `5m`, `15m`, `1h`), dynamic resizing, and crosshair overlays.
    - **Order Entry ([OrderEntry.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/orderentry/OrderEntry.tsx)):** Buy/Sell tabs, LIMIT/MARKET/IOC/FOK type switcher, price & quantity inputs, quick percentage fill chips (25%, 50%, 75%, 100%), available balance validation, and submission with `Idempotency-Key` UUID.
    - **Orders & History Tables ([OpenOrdersTable.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/orders/OpenOrdersTable.tsx), [OrderHistoryTable.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/orders/OrderHistoryTable.tsx), [TradeHistoryTable.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/orders/TradeHistoryTable.tsx)):** Tabbed bottom dock displaying open orders with interactive Cancel button, full order history with status chips, and personal trade execution logs.
    - **Account Panel ([AccountPanel.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/account/AccountPanel.tsx)):** Cards for USDT, BTC, ETH, SOL displaying available vs reserved balances with visual proportion meters.
    - **Notifications ([ToastContext.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/common/ToastContext.tsx)):** Toast feedback on order placement, fill, rejection, or cancellation.
  - **Auth & Terminal Pages ([frontend/src/app/](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/app/)):**
    - `/login`: Sleek dark login card with username/password and **"⚡ Try Demo (1-Click)"** instant access button.
    - `/register`: Trader signup form.
    - `/terminal`: Full assembled trading workspace.
    - `/`: Automatic redirect to `/terminal`.
  - **Build & Compilation Verification:**
    - TypeScript validation (`npm run type-check`) passed with 0 errors.
    - Next.js production build (`npm run build`) completed successfully, compiling all static and dynamic routes.

### Phase 10 — Observability Stack
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **OpenTelemetry & Micrometer Tracing Integration:**
    - Centralized dependencies in [libs.versions.toml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/gradle/libs.versions.toml) with `micrometer-tracing-bridge-otel` (1.3.4), `opentelemetry-exporter-otlp` (1.38.0), and `logstash-logback-encoder` (7.4).
    - Enabled across all microservices: `order`, `auth`, `account`, `matching-engine`, `risk`, `market-data`, `audit`, `gateway`, and `simulator`.
    - Configured W3C `traceparent` context propagation, 100% trace sampling probability (`management.tracing.sampling.probability: 1.0`), and OTLP trace export to Jaeger (`http://localhost:4318/v1/traces`).
  - **Custom Business & Infrastructure Metric Collectors:**
    - [OrderMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/metrics/OrderMetrics.java): `orders_placed_total` (tags: `instrument`, `side`, `type`), `orders_rejected_total` (tag: `reason`), `circuit_breaker_state` gauge, `kafka_consumer_lag` gauge, and `db_query_latency_seconds` timer.
    - [EngineMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/matching-engine/src/main/java/com/dete/matching/metrics/EngineMetrics.java): `trades_executed_total` (tag: `instrument`), `matching_latency_seconds` timer (nanosecond precision, P50/P95/P99 percentiles), `order_e2e_latency_seconds` timer, and `kafka_consumer_lag` gauge.
    - [AccountMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/account/src/main/java/com/dete/account/metrics/AccountMetrics.java): `ledger_entries_total` (tag: `type` = DEPOSIT, RESERVE, RELEASE, SETTLE), `db_query_latency_seconds` timer, and `kafka_consumer_lag` gauge.
    - [MarketDataMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/metrics/MarketDataMetrics.java): `websocket_connections_active` gauge (tracked dynamically via Spring STOMP `SessionConnectedEvent` and `SessionDisconnectEvent`), `websocket_messages_total` counter, `order_e2e_latency_seconds` timer, and `kafka_consumer_lag` gauge.
    - [GatewayMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/metrics/GatewayMetrics.java): `gateway_requests_total` counter (tags: `route`, `status`), `gateway_rate_limited_total` counter, and `circuit_breaker_state` gauge.
    - [RiskMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/risk/src/main/java/com/dete/risk/metrics/RiskMetrics.java): `orders_rejected_total` counter and `risk_check_duration_seconds` timer.
    - [AuditMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/audit/src/main/java/com/dete/audit/metrics/AuditMetrics.java): `audit_events_logged_total` counter, `db_query_latency_seconds` timer, and `kafka_consumer_lag` gauge.
    - [AuthMetrics](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/auth/src/main/java/com/dete/auth/metrics/AuthMetrics.java): `users_registered_total` counter, `users_login_total` counter, and `db_query_latency_seconds` timer.
  - **Structured JSON Logging & MDC Context ([logback-spring.xml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/resources/logback-spring.xml)):**
    - Uniform Logback configuration deployed across all 8 backend service modules.
    - Profiles: Human-readable ANSI color console logger for local development; structured `LogstashEncoder` JSON logger for `docker` and `prod` profiles.
    - Automatic MDC extraction and serialization: `traceId`, `spanId`, `service`, `correlationId`.
  - **Prometheus Scrape Configuration ([infra/prometheus/prometheus.yml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/infra/prometheus/prometheus.yml)):**
    - Corrected and validated scrape jobs for all 9 application services on their designated ports (`gateway:8080`, `auth:8081`, `account:8082`, `order:8083`, `matching-engine:8084`, `risk:8085`, `audit:8086`, `market-data:8087`, `simulator:8089`).
  - **8 Pre-Built & Provisioned Grafana Dashboards ([infra/grafana/dashboards/](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/infra/grafana/dashboards/)):**
    1. `system-overview.json`: Complete ecosystem health status, request volume, 5xx rate, P99 latency, and process CPU.
    2. `matching-engine.json`: In-memory trade fills/sec, instrument throughput, P50/P95/P99 matching latency percentiles, and E2E processing latency.
    3. `order-lifecycle.json`: Orders placed by instrument/side, order rejections by reason, order type distribution, and DB query latency.
    4. `kafka.json`: Max consumer lag, events consumed/sec, consumer lag by consumer group and topic, and listener processing latency.
    5. `account-ledger.json`: Total ledger entries, throughput, trade settlement rate, and operations breakdown (DEPOSIT, RESERVE, RELEASE, SETTLE).
    6. `jvm.json`: Heap and non-heap memory utilization, active thread count, GC pause durations, and JVM process CPU.
    7. `circuit-breakers.json`: Resilience4j circuit breaker state gauges (0=CLOSED, 1=OPEN, 2=HALF-OPEN), failure rates, and rate limiter rejections.
    8. `websocket.json`: Active client STOMP sessions, outbound broadcast message rates, and destination topic volume.
    - Configured auto-provisioning via [infra/grafana/provisioning/dashboards/dashboards.yml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/infra/grafana/provisioning/dashboards/dashboards.yml) and [infra/grafana/provisioning/datasources/datasources.yml](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/infra/grafana/provisioning/datasources/datasources.yml) pointing to Prometheus (`http://prometheus:9090`) and Jaeger (`http://jaeger:16686`).
  - **Verification & Test Suite:**
    - Unit tests created for metric collectors across services (`OrderMetricsTest`, `EngineMetricsTest`, `AccountMetricsTest`, `MarketDataMetricsTest`, `GatewayMetricsTest`).
    - Full project test suite passed across all modules (`.\gradlew test` succeeded with 0 failures).

### Phase 11 — Simulator / Market-Maker Bot Service
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Simulator Architecture & Bot Accounts ([services/simulator/](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator)):**
    - Spring Boot 3 microservice on port 8089 ([SimulatorApplication.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/main/java/com/dete/simulator/SimulatorApplication.java)) with task scheduling enabled (`@EnableScheduling`).
    - Multi-bot participant design ([BotAccountManager.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/main/java/com/dete/simulator/service/BotAccountManager.java)) featuring dedicated Maker (`bot_maker`) and Taker (`bot_taker`) personas to cleanly satisfy Self-Trade Prevention (STP) while simulating genuine cross-market participants.
    - Automated user registration, authentication, and JWT session handling via Auth Service (`POST /auth/login` and `POST /auth/register`) with automatic token renewal.
    - Large-scale balance provisioning: Maker and Taker accounts funded with 100M USD, 10,000 BTC, 100,000 ETH, and 1,000,000 SOL.
  - **Demo Account Seeding:**
    - Startup seed routine verifying or registering the `demo` user (`demo` / `DemoPassword123!`), and depositing `10,000 USD + 1 BTC + 5 ETH + 50 SOL` via Account Service (`POST /accounts/deposit`), guaranteeing immediate out-of-the-box trading readiness in the UI.
  - **Market-Maker Order Book Depth Engine ([InstrumentBot.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/main/java/com/dete/simulator/bot/InstrumentBot.java)):**
    - Configured for all three exchange instruments: `BTC-USD` (mid: 65,000, spread: 20 bps), `ETH-USD` (mid: 3,500, spread: 20 bps), `SOL-USD` (mid: 150, spread: 25 bps).
    - Populates 15 bid levels and 15 ask levels distributed across ±2% from target mid-price within 10 seconds of startup.
    - Scheduled replenishment loop every 4 seconds cancelling a subset of stale orders and refreshing depth to reflect latest price action.
  - **Continuous Fills & Realistic Mid-Price Drift:**
    - Mid-price random walk every 10 seconds: `mid_price += mid_price * random_delta` with delta uniformly in `[-0.3%, +0.3%]` and mean-reverting boundary pull (±15%).
    - Continuous fill generator every 3 seconds: Taker bot places crossing market/limit orders against resting maker orders, generating authentic fills, updating the live trade tape, driving ticker price updates, and generating real candlesticks.
    - Real end-to-end execution: bots use identical `POST /orders` endpoint as real traders, traversing JWT validation, risk checks, balance reservation, Kafka dispatch, matching engine execution, double-entry settlement, and STOMP WebSocket broadcasting.
  - **REST Control & Inspection API ([SimulatorController.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/main/java/com/dete/simulator/controller/SimulatorController.java)):**
    - `GET /simulator/status`: Returns simulator running state, initialization status, and per-instrument statistics (current mid price, active bid/ask counts).
    - `POST /simulator/start`: Resumes simulation loops.
    - `POST /simulator/stop`: Pauses simulation loops.
    - `POST /simulator/seed`: Re-seeds bot and demo accounts on demand.
  - **Verification & Test Suite (12 Passing Tests):**
    - Unit tests for configuration binding ([SimulatorPropertiesTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/test/java/com/dete/simulator/config/SimulatorPropertiesTest.java)).
    - Unit tests for order book population, drift, crossing order generation, and quote refresh ([InstrumentBotTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/test/java/com/dete/simulator/bot/InstrumentBotTest.java)).
    - Unit tests for bot authentication and demo account seeding ([BotAccountManagerTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/test/java/com/dete/simulator/service/BotAccountManagerTest.java)).
    - Web MVC tests for control and status endpoints ([SimulatorControllerTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/test/java/com/dete/simulator/controller/SimulatorControllerTest.java)).
    - Full Spring Boot context load integration test ([SimulatorIntegrationTest](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/simulator/src/test/java/com/dete/simulator/SimulatorIntegrationTest.java)).
    - Full mono-repo test suite verified passing (`.\gradlew test` succeeded across all 13 modules with 0 errors).

### Phase 12 — Frontend Phase B: System Dashboard, Replay & Audit
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Backend Telemetry & Replay Endpoints:**
    - [MetricsSnapshotController.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/controller/MetricsSnapshotController.java): Reactive Spring WebFlux endpoint `GET /internal/metrics/snapshot` querying live cluster actuator health across all 9 microservices, system throughput (trades/sec, orders/sec), matching engine P50/P95/P99 latency percentiles, Kafka consumer lag breakdown, JVM memory utilization, and active STOMP sessions.
    - [JwtAuthGlobalFilter.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/filter/JwtAuthGlobalFilter.java) & [RateLimiterGlobalFilter.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/filter/RateLimiterGlobalFilter.java): Excluded `/internal/metrics/` and `/simulator/` from public auth requirements and rate limiter throttling.
    - [MarketDataController.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/controller/MarketDataController.java): Added `GET /market-data/{instrument}/replay?from={ts}&to={ts}&limit={n}` backed by [MarketDataService.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/market-data/src/main/java/com/dete/marketdata/service/MarketDataService.java) and `MarketDataTradeRepository.findByInstrumentAndWindow(...)` returning chronological order execution history for frame-by-frame animation.
    - Aligned Gateway service route ports (`services/gateway/src/main/resources/application.yml`) to designated service topology: order (8083), account (8082), market-data (8087), ws (8087), audit (8086).
  - **Public System Telemetry Dashboard ([frontend/src/app/(dashboard)/dashboard/page.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/app/%28dashboard%29/dashboard/page.tsx)):**
    - Accessible publicly with zero login friction (`/dashboard`).
    - **Header & Metric Counters:** Live ecosystem health pill (ALL SYSTEMS OPERATIONAL), matching engine throughput (fills/sec), orders/sec, P99 matching latency (μs), active WebSocket sessions, and auto-refresh countdown ticker.
    - **9-Service Topology Health Grid:** Interactive status cards for `gateway`, `auth`, `account`, `order`, `matching-engine`, `risk`, `audit`, `market-data`, and `simulator` with ping response time and direct Prometheus/Actuator probe indicators.
    - **Performance Gauges & Latency Percentiles:** Visual latency breakdown for P50, P95, and P99 percentiles.
    - **Kafka Consumer Lag Monitor:** Tabular lag tracking across topics (`orders.in`, `trades.out`, `marketdata.events`, `ledger.entries`, `audit.events`) with consumer group IDs and lag thresholds.
    - **JVM Heap Memory Utilization:** Multi-service memory usage meters tracking used vs committed vs max heap in MB with warning thresholds.
    - **Live Trade Ticker & Simulator Controls:** Embedded real-time execution feed and interactive simulator control panel (Start, Pause, Re-Seed Bot & Demo accounts) with direct visual feedback.
  - **Historical Order Book Replay Viewer ([ReplayViewer.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/replay/ReplayViewer.tsx)):**
    - Mounted in `/terminal` under bottom dock tab **"Historical Replay"** and directly addressable via `/terminal?tab=replay`.
    - **Frame-by-Frame Engine:** 100ms interval playback engine iterating through chronological L2 trade events.
    - **Playback Controls:** Play, Pause, Step-Forward, Step-Backward, and interactive timeline scrubber slider.
    - **Speed Multipliers:** 1x, 2x, 5x, and 10x real-time replay velocity.
    - **Animated Order Book Ladder:** Live reconstructed Top 10 bids and asks with dynamic depth fill bars, animated flash highlights, spread calculator, and active trade event inspector.
  - **Regulatory Audit Log Viewer ([frontend/src/app/(admin)/admin/audit/page.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/app/%28admin%29/admin/audit/page.tsx)):**
    - Protected admin console (`/admin/audit`) with automatic demo credential fallback for development evaluation.
    - **Multi-Parameter Filter Dock:** Search by Subject ID (UUID/username), Event Type (`ORDER_PLACED`, `ORDER_CANCELLED`, `TRADE_EXECUTED`, `USER_REGISTERED`, `LOGIN_SUCCESS`, `FUNDS_DEPOSITED`), Instrument (`BTC_USDT`, `ETH_USDT`, `SOL_USDT`), and Trace ID.
    - **Expandable Payload Inspector:** Accordion rows expanding into syntax-highlighted, formatted JSON detail cards with 1-click clipboard copy.
    - **Clickable Distributed Tracing Links:** Direct external navigation link to Jaeger UI (`http://localhost:16686/trace/{traceId}`) for each recorded event.
    - **CSV Data Export:** Client-side CSV generator allowing instant export of filtered regulatory logs.
  - **Global Header Navigation ([Header.tsx](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/frontend/src/components/header/Header.tsx)):**
    - Seamless navigation pill bar connecting **Trading** (`/terminal`), **Telemetry** (`/dashboard`), and **Audit** (`/admin/audit`).
  - **Build & Verification:**
    - TypeScript compilation (`npm run type-check`) passed cleanly with 0 type errors.
    - Production build (`npm run build`) succeeded with all static and dynamic route manifests generated.

### Phase 13 — Resilience & Fault Tolerance
- **Status:** **Completed** (Oct 05, 2026)
- **Completed Components:**
  - **Resilience4j Circuit Breakers & Clean Failure Rejections:**
    - Order Service → Risk Service: Configured circuit breaker with sliding window 10, failure threshold 50%, and wait duration in open state 10s. When Risk Service fails or breaker trips to OPEN, orders are rejected fail-closed with [RiskServiceUnavailableException](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/exception/RiskServiceUnavailableException.java) and mapped to HTTP 503 `RISK_SERVICE_UNAVAILABLE`.
    - Order Service → Account Service: Synchronous balance reservations protected by circuit breaker. On downstream failure or open breaker, throws [AccountServiceUnavailableException](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/exception/AccountServiceUnavailableException.java) mapped to HTTP 503 `ACCOUNT_SERVICE_UNAVAILABLE`.
    - Gateway → Auth Service: Route circuit breaker configured in [FallbackController.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/fallback/FallbackController.java) returning HTTP 503 `AUTH_SERVICE_UNAVAILABLE` when Auth Service is down.
    - Actuator / Prometheus health gauges registered, updating Grafana's `circuit-breakers.json` dashboard dynamically (`0`=CLOSED, `1`=OPEN, `2`=HALF_OPEN).
  - **Bulkhead Downstream Isolation:**
    - Implemented reactive [BulkheadGlobalFilter.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/main/java/com/dete/gateway/filter/BulkheadGlobalFilter.java) in Gateway with independent concurrency pools per downstream:
      - `orderService`: 50 concurrent calls (20ms max wait)
      - `accountService`: 50 concurrent calls (20ms max wait)
      - `authService`: 50 concurrent calls (20ms max wait)
      - `marketDataService`: 100 concurrent calls (20ms max wait)
      - `auditService`: 30 concurrent calls (20ms max wait)
    - Proved through tests that when `orderService` concurrent calls are saturated, excess calls are rejected with `503 BULKHEAD_LIMIT_EXCEEDED`, while `accountService`, `authService`, and `marketDataService` remain 100% available without latency degradation.
    - Exposed bulkhead metrics to Micrometer: `resilience4j.bulkhead.available.concurrent.calls` and `resilience4j.bulkhead.max.allowed.concurrent.calls`.
  - **Kafka Retry & Dead-Letter Queue (DLQ) Pipeline:**
    - Enriched DLQ routing in [OrderEventConsumer.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/consumer/OrderEventConsumer.java) with diagnostic failure headers: `X-Original-Topic`, `X-Original-Partition`, `X-Original-Offset`, `X-Exception-Message`, `X-Failed-At`, and `X-Retry-Count`.
    - Provisioned standard DLQ topics: `order.commands.DLQ`, `order.events.DLQ`, `trade.executions.DLQ`, `ledger.events.DLQ`, `audit.events.DLQ`.
    - Expanded administrative REST endpoints in [DlqAdminController.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/main/java/com/dete/order/controller/DlqAdminController.java):
      - `GET /admin/dlq/topics`: Returns list of all provisioned DLQ topics.
      - `GET /admin/dlq/{topic}`: Peeks poisoned messages with diagnostic headers, partition, and offset.
      - `POST /admin/dlq/{topic}/reprocess`: Re-publishes messages from DLQ back to production topic for operational recovery.
    - Routed `/admin/dlq/**` via Spring Cloud Gateway with circuit breaker protection.
  - **Chaos Engineering Scenario & Benchmark Documentation:**
    - Created executable chaos automation scripts [scripts/chaos/kill_matching_engine.sh](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/scripts/chaos/kill_matching_engine.sh) and [scripts/chaos/kill_matching_engine.ps1](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/scripts/chaos/kill_matching_engine.ps1) for node termination, rising lag observation, engine restart, Kafka offset replay, and audit integrity verification.
    - Documented benchmark metrics in [benchmarks/chaos_results.md](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/benchmarks/chaos_results.md): recovery time (1,840 ms), consumer lag return to 0 (420 ms post-boot), 0 duplicate trade settlements, and contiguous sequence integrity.
  - **Verification & Test Suite:**
    - [BulkheadGlobalFilterTest.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/gateway/src/test/java/com/dete/gateway/filter/BulkheadGlobalFilterTest.java): Verified normal pass-through, actuator bypass, saturation rejection, and downstream bulkhead isolation (4/4 tests passed).
    - [DlqAdminControllerTest.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/controller/DlqAdminControllerTest.java): Verified topic discovery and graceful reprocessing handling.
    - [CircuitBreakerFallbackUnitTest.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/services/order/src/test/java/com/dete/order/client/CircuitBreakerFallbackUnitTest.java): Verified `RISK_SERVICE_UNAVAILABLE` and `ACCOUNT_SERVICE_UNAVAILABLE` exception throwing, preservation of `InsufficientFundsException`, and `GlobalExceptionHandler` 503 response mappings (6/6 tests passed).
    - Full mono-repo test class compilation (`.\gradlew.bat testClasses`) succeeded across all 13 modules in 53s.

### Phase 14 — Deployment: Docker & K3s (Helm)
- **Status:** **Completed** (Oct 07, 2026)
- **Completed Components:**
  - **Clean-Slate Oracle Cloud ARM64 Infrastructure Setup:**
    - Configured remote Linux VPS node (`oraclevps`, 141.148.223.82, Ubuntu 22.04 LTS, Ampere Neoverse-N1 2 OCPUs, 12 GB RAM, 200 GB NVMe).
    - Installed OpenJDK 21 LTS, Node.js 20, Docker engine, K3s Kubernetes cluster (`v1.36.5+k3s1`), and Helm (`v3.22.0`).
    - Configured swap allocation and host `iptables` ingress rules (ports 80, 443, 3000, 8080, 3001, 9090).
  - **Production Image Build & Containerd Import:**
    - Fast ARM64 multi-project host build (`./gradlew bootJar -x test --max-workers=2`) producing optimized Spring Boot executable jars.
    - Built lightweight Alpine/Jammy Docker containers for all 10 custom applications: `auth`, `account`, `order`, `matching-engine`, `risk`, `market-data`, `audit`, `gateway`, `simulator`, and Next.js `frontend`.
    - Imported container images directly into K3s containerd local cache (`sudo k3s ctr images import`).
  - **Helm Orchestration in `dete` Namespace:**
    - Deployed unified Helm chart release `dete` managing 16 total pods (10 application pods + Postgres 16, Redis 7, Kafka 7.6.2, ZooKeeper, Prometheus, and Grafana).
    - All 16 pods reached **1/1 Running** status.
    - Extreme resource efficiency achieved: **4.5 GiB used out of 11.4 GiB RAM (6.9 GiB free/available)**, 0 bytes swap used, ~13% disk used.
  - **Kafka Partition & Topic Synchronization:**
    - Stabilized Kafka 7.6.2 broker and ZooKeeper coordination with clean PVC persistent volumes.
    - Automated topic generation via `kafka-init-topics` provisioning all 14 operational and DLQ topics with identical cluster IDs.
  - **RS256 Deterministic JWT Security Alignment:**
    - Standardized RSA 2048-bit keypair utility ([RsaKeyUtils.java](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/libs/common-security/src/main/java/com/dete/common/security/RsaKeyUtils.java)) across all microservices, allowing seamless asymmetric JWT token verification across the API Gateway, Order, Account, Market Data, and Audit boundaries.
  - **Host Nginx Reverse Proxy (Unified Port 80):**
    - Configured host Nginx server on port 80 routing `/` to Next.js Trading Terminal (`frontend:3000`), `/auth`, `/orders`, `/accounts`, `/market-data` to API Gateway (`gateway:8080`), `/ws/` for live WebSockets, and `/grafana/` for telemetry dashboards.
  - **End-to-End Live Verification & Trading Benchmark:**
    - Created executable verification suite ([scripts/test_exchange_live.py](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/scripts/test_exchange_live.py)).
    - **Health Checks:** 9/9 services UP in 1-30ms.
    - **Prometheus Scrape:** 9/9 active scrape targets UP.
    - **Live Trading Benchmark:** 20/20 orders placed and matched across Gateway; 10 trades settled into double-entry ledger.
      - **Latency Percentiles:** Min: 90.85 ms, Avg: 200.06 ms, **P50: 211.18 ms, P95: 296.35 ms, P99: 296.35 ms**.
    - Full metrics and infrastructure report published in [benchmarks/vps_deployment_results.md](file:///e:/College_Documents/GITHUB/Distributed%20Electronic%20Trading%20Exchange/benchmarks/vps_deployment_results.md).
