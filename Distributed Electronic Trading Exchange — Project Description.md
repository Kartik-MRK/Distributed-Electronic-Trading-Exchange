# Distributed Electronic Trading Exchange

## Project Overview

I am building a full-stack, backend-heavy **Distributed Electronic Trading Exchange Simulator** designed to demonstrate strong practical knowledge of Java backend engineering, distributed systems, microservices, concurrency, database systems, event-driven architecture, real-time communication, testing, observability, and cloud deployment.

The application will simulate a simplified institutional crypto/stock exchange where users can register, authenticate, maintain trading balances, place and cancel orders, view a live order book, execute trades, monitor their orders and trades, and view real-time market data through a trading-terminal-style frontend.

This is not intended to be a real financial exchange or production HFT system. The objective is to build a technically serious exchange simulator that demonstrates low-latency system design and production-oriented backend engineering.

The system must be demonstrable live — including in interviews and via a public URL — without requiring any explanation from the author.

## Main Engineering Goals

The project should demonstrate:

- Microservice architecture
- Java and Spring Boot backend development
- Low-latency order matching
- Price-time-priority order matching
- Concurrent and high-throughput processing
- Event-driven architecture with Apache Kafka
- PostgreSQL transaction management
- Double-entry financial ledger
- Balance reservation and settlement
- Redis-based caching and idempotency
- REST APIs
- gRPC for internal synchronous communication
- WebSockets for real-time market data
- CQRS with explicit write-side and read-side separation
- Transactional Outbox and idempotent consumers
- Circuit breaker and bulkhead patterns for resilience
- Fault tolerance, chaos testing, and measurable recovery
- Immutable audit logging for compliance
- Automated testing at multiple levels
- Performance benchmarking backed by measured results
- Load testing
- Distributed tracing and metrics
- Docker and Kubernetes/K3s deployment
- CI/CD using GitHub Actions

## Infrastructure

Initial deployment target:

- Oracle Cloud VPS
- 2 vCPU
- 12 GB RAM
- Single-node K3s cluster

The architecture should therefore be designed to be scalable in principle while remaining realistic for a resource-constrained single-node deployment.

Kubernetes should not be treated as a magic scaling mechanism. The system should demonstrate logical scalability through service separation and instrument-level partitioning, while acknowledging that the initial physical deployment has only 2 CPU cores.

All performance benchmarks must be measured and documented against this exact hardware constraint. Claims like "X orders/sec at P99 Y ms on 2 vCPU" are far more credible than theoretical throughput numbers.

## Core Architecture

The major services will be:

1. Gateway Service
2. Authentication Service
3. Order Service
4. Risk Service
5. Account/Ledger Service
6. Matching Engine
7. Market Data Service
8. Audit Service
9. Simulator / Market-Maker Bot Service

The matching engine will be a specialized Java service rather than a normal CRUD Spring Boot service.

The matching engine should use a **single-writer model** for each order-book partition so that order processing remains deterministic.

Trading instruments can be partitioned independently, for example:

- BTC-USD → partition 0
- ETH-USD → partition 1
- SOL-USD → partition 2

This provides a path toward horizontal scalability without allowing multiple writers to modify the same order book concurrently.

## Financial Consistency Model

Balances must never rely on asynchronous settlement alone.

Before an order is accepted:

1. Validate the request.
2. Perform risk checks.
3. Verify available funds/assets.
4. Reserve the required amount.
5. Only then allow the order to enter the matching workflow.

The account/ledger system should maintain:

- available balance
- reserved balance
- immutable ledger entries
- double-entry accounting
- settlement records
- reconciliation information

The system should prevent:

- double spending
- duplicate trade settlement
- duplicate order creation
- duplicate event processing
- balance drift

## Matching Engine

The matching engine should implement:

- Price-time priority
- Limit orders
- Market orders
- IOC (Immediate-or-Cancel)
- FOK (Fill-or-Kill)
- GTC (Good-Till-Cancelled)
- Order cancellation
- Order modification
- Partial fills
- Full fills
- Self-trade prevention
- Deterministic sequence numbers

**Order modification** is implemented as a cancel-and-reinsert operation. The original order is cancelled, releasing its position in the time-priority queue, and a new order is inserted with a fresh timestamp. This correctly preserves price-time priority semantics and avoids the ambiguity of in-place mutation of a live order.

The hot path should minimize allocations and synchronization.

The implementation should use suitable low-level structures such as:

- price-level structures
- FIFO order queues
- direct order lookup by order ID
- preallocated structures where appropriate
- ring-buffer/message-passing concepts where beneficial
- fixed-point/integer representations rather than floating-point calculations

Performance claims must always be backed by actual JMH benchmarks run on the target hardware.

## CQRS Architecture

The system applies a concrete CQRS separation:

**Write side (command path):**

Order Service → Risk Service → Account/Ledger Service (reserve funds) → Kafka `order.commands` → Matching Engine → Kafka `trade.executions` + `order.events` → Settlement (Account/Ledger Service) → Kafka `ledger.events` + `audit.events`

**Read side (query path):**

Market Data Service consumes `order.events`, `trade.executions`, and `market-data` topics from Kafka and maintains a read-optimized materialized view: current order book snapshot, recent trade tape, and OHLCV candlestick data. These are served to the frontend over WebSocket and REST without touching the authoritative PostgreSQL write database.

This separation ensures that high-frequency read traffic (order book polling, price feed subscriptions) does not contend with transactional write operations.

## Event-Driven Architecture

Apache Kafka will be the primary asynchronous event backbone.

Topics:

- `order.commands` — inbound order placement and cancellation commands
- `order.events` — order lifecycle events (accepted, partially filled, filled, cancelled)
- `trade.executions` — matched trade records with buyer/seller IDs and quantities
- `market-data` — order book snapshots and price ticks for the read side
- `ledger.events` — balance changes and settlement confirmations
- `audit.events` — immutable audit trail of all significant state transitions
- `simulator.commands` — internal commands for the market-maker bot service

The system should use:

- transactional outbox
- idempotent consumers
- event identifiers
- sequence numbers
- retries with exponential backoff
- dead-letter topics
- replay capability

Kafka replay capability will be demonstrated in the UI via a historical order book reconstruction feature.

## Resilience Patterns

The system should implement standard distributed-system resilience patterns:

- **Circuit breakers** using Resilience4j on all synchronous gRPC calls (e.g., Risk Service → Account Service, Order Service → Risk Service). If the Account Service is slow or unavailable, the circuit trips and orders are rejected cleanly rather than timing out across the stack.
- **Bulkheads** between the Gateway and downstream services, preventing a slow downstream service from exhausting the shared thread pool.
- **Retry with backoff** on Kafka consumer processing failures before routing to the dead-letter topic.
- **Idempotent consumers** throughout so that Kafka message redelivery never causes duplicate balance changes or duplicate trade settlements.
- **Graceful degradation**: if the Market Data Service is unavailable, the trading terminal falls back to displaying the last known order book snapshot rather than failing entirely.

Fault tolerance must be measurable, not just described. A chaos test scenario (pod kill + recovery time measurement in Grafana) should be documented and reproducible.

## Audit Service

The Audit Service is a mandatory, first-class service — not optional.

It consumes the `audit.events` Kafka topic and writes every event to an append-only audit log in a dedicated PostgreSQL schema. Records are never updated or deleted.

The audit log captures:

- every order state transition (submitted, accepted, partially filled, filled, cancelled, rejected)
- every balance change (reservation, release, debit, credit, settlement)
- every trade execution (buyer ID, seller ID, instrument, price, quantity, timestamp, sequence number)
- every authentication event (login, logout, token refresh)

The Audit Service exposes an `/admin/audit` endpoint that allows browsing the full history of any order, account, or instrument over a selected time window.

This demonstrates understanding of regulatory compliance patterns common in fintech systems.

## Database

PostgreSQL is the authoritative persistent store for financial and transactional state.

Use:

- Flyway for schema migrations
- explicit database constraints
- indexes
- transactions
- appropriate isolation levels
- row locking where required
- unique constraints
- batch operations
- query optimization

The project should expose real knowledge of PostgreSQL rather than hiding all database behavior behind abstractions.

jOOQ/JDBC is preferred for important transactional and performance-sensitive paths.

The audit log uses a separate PostgreSQL schema to enforce the separation between mutable operational state and immutable compliance records.

## Redis

Redis should be used for fast, ephemeral state such as:

- API rate limiting
- idempotency-key caching
- short-lived caching
- WebSocket connection metadata
- other non-authoritative low-latency state

Redis must not be the source of truth for financial balances.

## Frontend

The frontend will be a proper full-stack trading terminal built using:

- Next.js
- TypeScript
- Tailwind CSS
- WebSockets

The interface should feel like a professional trading terminal, not a generic admin dashboard.

### Trading Terminal Panels

| Panel | Description |
|---|---|
| **Live Order Book** | Real-time bid/ask depth ladder with animated price-level updates |
| **Trade Tape** | Scrolling feed of recent executions (price, quantity, direction, timestamp) |
| **Price Chart** | Candlestick chart built from live OHLCV data streamed over WebSocket |
| **Order Entry** | Buy/sell form supporting limit, market, IOC, FOK, and GTC order types |
| **Open Orders** | Table of active orders with live status updates and cancel action |
| **Order History** | Full order lifecycle: submitted → partial fills → filled/cancelled |
| **Trade History** | Personal executed trades with fill price, quantity, and fee |
| **Account Overview** | Available balance, reserved balance, open P&L per instrument |
| **WebSocket Status** | Connection indicator with round-trip latency and reconnection state |

### System Dashboard (publicly visible without login)

A separate dashboard tab accessible without authentication, intended for demos and live showcasing:

| Panel | Description |
|---|---|
| **Matching Throughput** | Live orders/sec and fills/sec from the matching engine |
| **End-to-End Latency** | P50 / P95 / P99 order latency histogram updated in real time |
| **Kafka Consumer Lag** | Per-topic consumer lag showing event pipeline health |
| **Service Health** | Traffic-light status (green/amber/red) for each microservice |
| **Active WebSocket Connections** | Current count of connected clients |
| **Recent Trade Executions** | Live global trade feed across all instruments |
| **JVM Metrics** | Heap usage, GC pause time, and thread count for key services |

### Historical Replay Viewer

A UI panel that reconstructs the order book for any selected time window by replaying stored Kafka events. This demonstrates the replayability of the event stream concretely and visually.

### Audit Log Viewer

An admin panel showing the immutable audit trail for any order, account, or instrument. Filterable by time range, instrument, user, and event type.

## Demo & Observability Mode

The system will include a self-contained demonstration mode that ensures the exchange is always live and interactive without any manual setup.

### Automated Market-Maker Bot Service

A dedicated Simulator Service runs three simulated trader bots, one per instrument (BTC-USD, ETH-USD, SOL-USD). Each bot:

- continuously places and cancels limit orders at randomized prices around a reference mid-price
- occasionally places market orders to generate fills
- maintains a realistic-looking order book depth at all times

The bots use the same REST and Kafka paths as real users — they are not internal shortcuts — so they exercise the full system end-to-end.

### Guest Demo Account

A pre-funded demo account is available via a single-click "Try Demo" button on the login page. The demo user receives:

- a pre-loaded balance (e.g., 10,000 USD + 1 BTC + 5 ETH)
- immediate access to place orders against the live bots
- full visibility into matching, settlement, balance changes, and audit events

No registration is required to experience the core system.

### Pre-Built Grafana Dashboards

Grafana dashboards are shipped as part of the Helm chart and provisioned automatically on deployment. They cover:

- matching engine throughput and latency
- Kafka topic throughput and consumer lag
- PostgreSQL query latency and connection pool utilization
- Redis hit/miss rate
- JVM memory and GC activity
- per-service HTTP request rate, error rate, and P99 latency
- end-to-end order latency from Gateway receipt to WebSocket delivery

## Observability

The system should use:

- Spring Boot Actuator
- Prometheus
- Grafana
- OpenTelemetry

Metrics should include:

- request count
- error count
- response latency
- P50, P95, P99
- matching latency
- Kafka consumer lag per topic
- queue depth
- database latency
- connection-pool utilization
- JVM memory and GC activity
- CPU and memory usage
- WebSocket connection count
- circuit breaker state (closed / open / half-open)

Distributed tracing should allow a single request to be followed across the entire stack:

Gateway → Order Service → Risk → Account → Kafka → Matching Engine → Settlement → Market Data → WebSocket

Trace IDs should be visible in logs and linkable from the audit viewer.

## Operational Runbook

The repository should include a `docs/runbook.md` covering at minimum:

- How to restart a crashed Matching Engine and resume processing from the correct Kafka offset without losing or duplicating events
- How to detect balance drift and run the reconciliation check
- How to replay a failed trade settlement from the dead-letter topic
- How to rotate secrets without downtime
- How to add a new trading instrument
- How to interpret the Grafana dashboards and identify common failure modes

This document demonstrates operational thinking and is useful to reference in interviews when discussing how you would run this system in production.

## Testing

Testing is a first-class requirement.

The project should eventually contain:

- Unit tests
- Integration tests
- Testcontainers tests
- Contract tests
- Architecture tests using ArchUnit
- Concurrency tests using JCStress
- Performance benchmarks using JMH
- Property/invariant-based tests for the matching engine
- k6 load tests
- Failure/chaos tests (pod kill, network partition, Kafka broker restart)

### Chaos Engineering Scenario

At least one reproducible chaos scenario should be implemented and documented:

1. Kill the Matching Engine pod mid-trade.
2. Observe the circuit breakers trip in the Order Service.
3. Observe Kafka consumer lag increase.
4. Observe automatic recovery as the pod restarts and resumes from the correct offset.
5. Verify via the audit log that no trade was duplicated or lost.
6. Show the recovery timeline in Grafana.

This scenario should be runnable with a single script and the results should be committed as a documented benchmark.

### Important System Invariants

- no duplicated trade settlement
- no negative balances unless explicitly supported
- available + reserved = total balance
- debit/credit accounting remains balanced
- sequence numbers remain ordered
- executed quantity never exceeds submitted quantity
- cancelled quantity never exceeds remaining quantity
- duplicate events are safely ignored
- circuit breaker state is observable in metrics

## Deployment

The application will eventually be containerized and deployed using:

- Docker
- K3s
- Helm (dashboards and configs shipped as part of the chart)
- Kubernetes Deployments
- Services
- Ingress
- ConfigMaps
- Secrets
- readiness probes
- liveness probes
- resource requests/limits
- HPA where useful
- Pod Disruption Budgets where meaningful
- NetworkPolicies where practical

## CI/CD

GitHub Actions should eventually perform:

- formatting/linting
- compilation
- unit tests
- integration tests
- architecture tests
- security scanning (dependency vulnerability check)
- Docker image builds
- image publishing to registry
- Helm chart validation
- deployment to K3s
- smoke tests (place an order, verify it fills, verify balance updates)
- performance regression check (JMH benchmark result comparison against baseline)

## Performance Engineering

Performance should be measured rather than assumed.

All benchmarks must be run on the actual deployment hardware (2 vCPU / 12 GB) and results must be committed to the repository.

Important measurements will include:

- matching-engine throughput (orders/sec)
- matching-engine latency (P50, P95, P99)
- API latency per endpoint
- end-to-end order latency (Gateway receipt → WebSocket delivery of fill notification)
- allocation rate during hot path
- GC pause impact on latency tail
- Kafka producer/consumer throughput
- database query latency under load
- WebSocket delivery latency

Benchmark experiments should compare different implementation approaches (e.g., TreeMap vs custom skip-list for order book price levels) and document results with reasoning.

## Development Philosophy

The project should be built incrementally.

Do not introduce a technology merely to make the architecture diagram look impressive.

Every component should have a clear responsibility.

The main focus should remain:

1. Correctness
2. Backend engineering
3. Distributed-system design
4. Performance
5. Testability
6. Observability
7. Deployability
8. Frontend usability

The final project should be something that can be demonstrated live — with a public URL, live order book movement, observable metrics, and a working demo account — and discussed deeply in any backend or system-design interview.
