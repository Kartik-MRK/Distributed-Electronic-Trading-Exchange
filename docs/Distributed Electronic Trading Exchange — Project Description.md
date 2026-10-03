# Distributed Electronic Trading Exchange

## Project Overview

I am building a full-stack, backend-heavy **Distributed Electronic Trading Exchange Simulator** designed to demonstrate strong practical knowledge of Java backend engineering, distributed systems, microservices, concurrency, database systems, event-driven architecture, real-time communication, testing, observability, and cloud deployment.

The application will simulate a simplified institutional crypto/stock exchange where users can register, authenticate, maintain trading balances, place and cancel orders, view a live order book, execute trades, monitor their orders and trades, and view real-time market data through a trading-terminal-style frontend.

This is not intended to be a real financial exchange or production HFT system. The objective is to build a technically serious exchange simulator that demonstrates low-latency system design and production-oriented backend engineering.

## Main Engineering Goals

The project should demonstrate:

- Microservice architecture
- Java and Spring Boot backend development
- Low-latency order matching
- Price-time-priority order matching
- Concurrent and high-throughput processing
- Event-driven architecture
- Apache Kafka
- PostgreSQL transaction management
- Double-entry financial ledger
- Balance reservation and settlement
- Redis-based caching and idempotency
- REST APIs
- gRPC for internal synchronous communication where appropriate
- WebSockets for real-time market data
- CQRS/read-model concepts where useful
- Transactional Outbox and idempotent consumers
- Fault tolerance and recovery
- Automated testing at multiple levels
- Performance benchmarking
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

## Core Architecture

The major services will be:

1. Gateway Service
2. Authentication Service
3. Order Service
4. Risk Service
5. Account/Ledger Service
6. Matching Engine
7. Market Data Service
8. Optional Analytics Service

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
- IOC
- FOK
- GTC
- Order cancellation
- Order modification
- Partial fills
- Full fills
- Self-trade prevention
- Deterministic sequence numbers

The hot path should minimize allocations and synchronization.

The implementation should use suitable low-level structures such as:

- price-level structures
- FIFO order queues
- direct order lookup
- preallocated structures where appropriate
- ring-buffer/message-passing concepts where beneficial
- fixed-point/integer representations rather than floating-point calculations

Performance claims must always be backed by actual benchmarks.

## Event-Driven Architecture

Apache Kafka will be the primary asynchronous event backbone.

Example topics:

- order.commands
- order.events
- trade.executions
- market-data
- ledger.events
- audit.events

The system should use:

- transactional outbox
- idempotent consumers
- event identifiers
- sequence numbers
- retries
- dead-letter topics
- replay capability

Kafka should be used because the system benefits from durable ordered event streams, replayability, and partitioning.

## Database

PostgreSQL is the authoritative persistent store for financial and transactional state.

Use:

- Flyway for migrations
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
- Canvas/WebGL where beneficial

It should eventually provide:

- authentication
- account/balance view
- order entry
- buy/sell controls
- live order book
- market trades
- price chart
- open orders
- order history
- trade history
- portfolio/balance information
- WebSocket connection status
- latency/diagnostic information

The frontend should feel like a trading terminal rather than a generic admin dashboard.

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
- P50
- P95
- P99
- matching latency
- Kafka consumer lag
- queue depth
- database latency
- connection-pool utilization
- JVM memory
- GC activity
- CPU
- memory usage
- WebSocket connections

Distributed tracing should allow a request to be followed across:

Gateway → Order Service → Risk → Account → Kafka → Matching Engine → Settlement → Market Data → WebSocket.

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
- Failure/chaos tests

Important system invariants include:

- no duplicated trade settlement
- no negative balances unless explicitly supported
- available + reserved = total balance
- debit/credit accounting remains balanced
- sequence numbers remain ordered
- executed quantity never exceeds submitted quantity
- cancelled quantity never exceeds remaining quantity
- duplicate events are safely ignored

## Deployment

The application will eventually be containerized and deployed using:

- Docker
- K3s
- Helm
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
- security scanning
- Docker builds
- image publishing
- Helm validation
- deployment
- smoke tests

## Performance Engineering

Performance should be measured rather than assumed.

Important measurements will include:

- matching-engine throughput
- matching-engine latency
- API latency
- end-to-end order latency
- P50/P95/P99
- allocation rate
- GC impact
- Kafka throughput
- database latency
- WebSocket delivery latency

Benchmark experiments should compare different implementations and document the results.

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

The final project should be something that can be demonstrated live and discussed deeply in a backend/system-design interview.