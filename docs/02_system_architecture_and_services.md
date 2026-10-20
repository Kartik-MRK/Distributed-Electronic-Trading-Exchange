# 02 — System Architecture & Microservice Breakdown

---

## 1. High-Level Architecture Overview

DETE is built as a **distributed microservice architecture** (*a system design where specialized software components run independently and communicate over network protocols, rather than bundling everything into one giant program*).

The exchange is composed of **10 specialized services** and **4 shared libraries**, glued together by **Apache Kafka** (*a distributed high-throughput event streaming message broker that acts as a central nervous system for events*) and **PostgreSQL 16** (*a relational database used for durable state and financial ledgers*).

```
 ┌────────────────────────────────────────────────────────────────────────┐
 │                              CLIENT LAYER                              │
 │  Next.js 14 Web Terminal  │  WebSocket Clients  │ Market-Maker Bot     │
 └───────────────────────────────────┬────────────────────────────────────┘
                                     │ (HTTP / WebSocket)
                                     ▼
 ┌────────────────────────────────────────────────────────────────────────┐
 │                      EDGE & SECURITY (API GATEWAY)                     │
 │  Port 80 Nginx Proxy ──► Spring Cloud Gateway (Reactive Netty)         │
 │  - Redis Token Bucket Rate Limiting (blocks abusive spam)              │
 │  - Asymmetric RS256 JWT Token Verification                             │
 └─────────────┬─────────────────────┬────────────────────┬───────────────┘
               │                     │                    │
               ▼                     ▼                    ▼
     ┌──────────────────┐  ┌──────────────────┐  ┌──────────────────┐
     │   Auth Service   │  │  Order Service   │  │ Market Data Svc  │
     │  (Port 8081)     │  │  (Port 8083)     │  │  (Port 8087)     │
     │  - RS256 Signing │  │  - Two-Tier Idem.│  │  - STOMP / WS    │
     │  - JWKS Endpoint │  │  - Trans. Outbox │  │  - L2 Depth / OHLC│
     └──────────────────┘  └────────┬─────────┘  └────────▲─────────┘
                                    │                     │
                     ┌──────────────┴──────────────┐      │
                     │ (gRPC ~0.5ms)  (HTTP Reserve│      │
                     ▼                             ▼      │
           ┌──────────────────┐          ┌────────────────┴─┐
           │   Risk Service   │          │  Account/Ledger  │
           │  (Port 8085)     │          │  (Port 8082)     │
           │  - 6 Risk Rules  │          │  - Double-Entry  │
           │  - Self-Trade P. │          │  - Reconciliation│
           └──────────────────┘          └────────┬─────────┘
                                                  │
 ═════════════════════════════════════════════════╪═════════════════════════
                       EVENT BUS (APACHE KAFKA)   │
   [order.commands] ──► [Matching Engine] ──────► [trade.executions]
                             (Port 8084)          [order.events]
                             Single-Writer RAM    [ledger.events]
 ═════════════════════════════════════════════════╪═════════════════════════
                                                  │
                                                  ▼
                                         ┌──────────────────┐
                                         │  Audit Service   │
                                         │  (Port 8086)     │
                                         │  - SHA-256 Chain │
                                         └──────────────────┘
```

---

## 2. The 10 Microservices Explained

### 1. API Gateway Service (Edge Router & Gatekeeper)
- **Role**: The single public entry point for all incoming HTTP requests.
- **Technology**: Spring Cloud Gateway powered by **Project Reactor** and **Netty** (*non-blocking asynchronous I/O frameworks that handle thousands of simultaneous client connections with minimal threads*).
- **Key Responsibilities**:
  - **Reverse Proxy Routing**: Maps incoming paths (e.g., `/api/v1/orders` $\rightarrow$ Order Service, `/api/v1/accounts` $\rightarrow$ Account Service).
  - **Distributed Rate Limiting**: Uses Redis with the **Token Bucket Algorithm** (*a rate-limiting mechanism where clients are given a bucket of tokens that refill over time; each request consumes a token, blocking abusive rapid-fire spam*).
  - **Authentication Filter**: Verifies the signature of incoming JWT tokens without calling the database on every request.
  - **Resilience**: Configured with **Bulkheads** (*a failure-isolation pattern named after watertight ship compartments, preventing a surge in slow requests to one service from consuming all server resources and knocking down the entire system*).

### 2. Authentication Service (Identity & Credential Provider)
- **Role**: Manages user registration, login, and identity verification.
- **Security Design**:
  - Passwords are encrypted using **BCrypt** (*a slow, salted cryptographic hash function designed to resist brute-force hardware cracking*).
  - Issues **RS256 JSON Web Tokens (JWT)** (*asymmetric digital identity passes where the Auth Service signs tokens using a private RSA cryptographic key, while any downstream service can independently verify tokens using a shared public key*).
  - Exposes a **JWKS (JSON Web Key Set) endpoint** (`/.well-known/jwks.json`) so other services can automatically fetch and refresh public keys without sharing sensitive private credentials.

### 3. Order Service (Order Lifecycle & Orchestration)
- **Role**: Accepts, validates, tracks, and persists all trading orders.
- **Key Responsibilities**:
  - **Two-Tier Idempotency**: Guarantees that if a network glitch causes a user to submit the same order twice, it is only executed once (*idempotency*—an operation that produces the same result no matter how many times it is repeated). First checks a 24-hour Redis cache; falls back to unique PostgreSQL database constraints.
  - **Pre-Trade Risk & Balance Coordination**: Calls the Risk Service (via gRPC) and the Account Service (via REST) to ensure the trader has both the permission and the money to trade.
  - **Order State Machine**: Manages the life stages of an order: `SUBMITTED` $\rightarrow$ `ACCEPTED` $\rightarrow$ `PARTIALLY_FILLED` $\rightarrow$ `FILLED` (or `CANCELLED` / `REJECTED`).
  - **Transactional Outbox Pattern**: Solves the *dual-write problem* (*the dangerous scenario where a service updates its SQL database and tries to publish to Kafka, but a crash causes one to succeed while the other fails*). It writes the order and the Kafka message together in one single atomic SQL database transaction, which background workers safely dispatch.

### 4. Pre-Trade Risk Service (Real-Time Safety Barrier)
- **Role**: The exchange's "safety inspector," evaluating orders before they touch the matching engine.
- **Communication**: Uses **gRPC over HTTP/2** (*a high-performance binary remote procedure call system that transmits compact protobuf messages over long-lived multiplexed connections in under 0.5 milliseconds, avoiding the overhead of heavy JSON HTTP/1.1 requests*).
- **The 6 Pre-Trade Risk Rules Evaluated**:
  1. **Max Order Size**: Prevents a trader from submitting an order with an enormous quantity that could destabilize the market.
  2. **Max Notional Limit**: Caps total order value (*notional value* = $\text{price} \times \text{quantity}$, the total dollar value of the trade).
  3. **Price Collar / Deviation Guard**: Protects against "fat-finger errors" (*accidental typos where a trader accidentally types \$100,000 instead of \$10,000*). Rejects limit orders placed more than 10% away from the last traded price.
  4. **Max Open Orders**: Restricts the maximum number of active resting orders a single account can maintain.
  5. **Max Gross Account Exposure**: Calculates the total financial risk of an account across all its open orders combined.
  6. **Self-Trade Prevention (STP)**: Prevents wash trading (*an illegal manipulation where a trader buys and sells against their own orders to artificially fabricate fake trading volume*).

### 5. Matching Engine Service (The Beating Heart of the Exchange)
- **Role**: Matches buyers and sellers using an in-memory electronic order book.
- **Execution Model**:
  - **Single-Writer Thread Model**: Each market pair (e.g., `BTC_USD`) is handled exclusively by one dedicated CPU thread. Because only one thread touches the data, there are **zero locks, zero synchronized blocks, and zero thread-contention delays**.
  - **Price-Time Priority (FIFO)**: The universal golden rule of financial markets.
    - **Price Priority**: The highest buy offer (*bid*) and the lowest sell offer (*ask*) get matched first.
    - **Time Priority (First-In, First-Out)**: If two traders offer the same price, the order that arrived first gets executed first.
  - **Data Structure**:
    - **Price Ladder**: A `TreeMap` sorted by price (descending for bids, ascending for asks), providing $O(\log P)$ price level lookups.
    - **Time Queue**: At each price level, orders sit in an `ArrayDeque` (double-ended queue), guaranteeing true $O(1)$ first-come, first-served execution.
    - **Order Index**: A `HashMap<UUID, BookOrder>` allowing immediate $O(1)$ lookups for order cancellations.
  - **Speed**: Matches orders in **2.2 microseconds** ($0.0022\text{ milliseconds}$) and processes over **1.59 million resting order insertions per second**.
  - **Zero Database Roundtrips**: Runs 100% in memory (RAM).
  - **Kafka Log Replay Recovery**: If the node reboots, it reads historical events from Kafka to rebuild the exact in-memory order book state from scratch.

### 6. Account & Ledger Service (The Financial Bank & Bookkeeper)
- **Role**: Manages balances, holds funds, and records all monetary movements with zero error tolerance.
- **Key Concepts**:
  - **Two-Phase Fund Reservation**: Funds are split into *Available* (spendable) and *Reserved* (locked in active orders).
  - **Append-Only Double-Entry Ledger**: Implements ancient, battle-tested accounting (*every financial transaction must have equal and opposite entries: debits must equal credits*).
    - When a trade executes, 4 immutable ledger entries are generated:
      1. Buyer's reserved USD is **Debited** (decreased).
      2. Buyer's available BTC is **Credited** (increased).
      3. Seller's reserved BTC is **Debited** (decreased).
      4. Seller's available USD is **Credited** (increased).
  - **PostgreSQL Database Triggers**: Special database rules strictly forbid `UPDATE` or `DELETE` SQL statements on the ledger table. Once written, financial history cannot be altered or erased by any user, developer, or bug.
  - **Automated Mathematical Reconciliation**: A nightly background job computes:
    $$\sum (\text{available} + \text{reserved}) = \sum (\text{deposits} + \text{credits}) - \sum (\text{withdrawals} + \text{debits})$$
    If there is even a $0.00000001$ discrepancy (*balance drift*), it fires a critical alert to Kafka.

### 7. Market Data Service (Real-Time Feed & Candlesticks)
- **Role**: Ingests high-speed trade events from Kafka and broadcasts them to the outside world.
- **Key Responsibilities**:
  - **L2 Order Book Aggregation**: Condenses individual orders into public price ladders (*Level 2 market depth*—showing the top 20 aggregated buy and sell price levels with total available volumes).
  - **OHLCV Candlestick Aggregation**: Continuously rolls trade ticks into financial candlestick bars (*Open, High, Low, Close, Volume*) across timeframes (1-minute, 5-minute, 1-hour).
  - **WebSocket Streaming**: Uses **STOMP over WebSockets** (*a bidirectional, persistent TCP connection protocol allowing the server to push live price updates to web browsers instantly without browsers having to poll or refresh*). Capable of streaming thousands of depth updates per second.

### 8. Audit & Compliance Service (The Tamper-Proof Black Box)
- **Role**: Records an unalterable history of every single state transition in the system for regulatory and security oversight.
- **Cryptographic Hash Chaining**:
  - Works like a private blockchain. Every new audit record calculates an SHA-256 cryptographic hash based on its own data **plus the hash of the preceding record** (`previous_hash` + `current_record_payload` = `entry_hash`).
  - If a rogue database administrator attempts to quietly alter a past transaction or balance in PostgreSQL, the cryptographic chain instantly breaks, exposing the exact record that was modified.

### 9. Market-Maker Bot & Simulator Service (Realistic Liquidity Provider)
- **Role**: Acts as automated market participants to keep the exchange alive with continuous buy and sell orders.
- **Algorithm**: Implements the **Avellaneda-Stoikov Market-Making Model** (*a classic quantitative finance algorithm that quotes both bids and asks around a fair asset price, dynamically adjusting the spread to manage its own inventory risk so it doesn't run out of cash or crypto*).
- **Benefit**: Ensures anyone loading the web terminal immediately sees a dynamic, living market with live price movements, fluctuating depth, and ongoing trade executions.

### 10. Frontend Trading Terminal (User Interface)
- **Technology**: **Next.js 14**, React, TailwindCSS, and **TradingView Lightweight Charts**.
- **Features**:
  - **Live Order Book Depth Ladder**: Animated bid/ask depth chart reflecting real-time market liquidity.
  - **Live Trade Tape**: High-frequency scrolling feed displaying every executed trade tick.
  - **Order Entry Form**: Allows users to place Market or Limit orders with instant feedback.
  - **Interactive Candlestick Chart**: Zoomable, pannable financial charts with volume bars.
  - **System Health & Telemetry Dashboard**: Live monitoring panel displaying Kafka consumer lag, end-to-end P99 order latency, active WebSocket connections, and microservice status.

---

## 3. Shared Libraries (`/libs`)

To keep code DRY (*Don't Repeat Yourself*) and prevent microservices from having conflicting data definitions, the system shares four compiled Java modules:

1. **`common-domain`**:
   - Contains core domain enums (`Instrument`, `OrderSide`, `OrderStatus`, `OrderType`).
   - Houses the **`FixedPoint`** mathematical class (enforcing zero floating-point arithmetic across all calculations).
2. **`common-events`**:
   - Holds strongly-typed Kafka event records (`OrderPlacedEvent`, `OrderAcceptedEvent`, `TradeExecutedEvent`, `BalanceSettledEvent`, `AuditEvent`).
3. **`common-security`**:
   - Houses the asymmetric RS256 JWT decoding filters, public key verification utilities, and Spring Security configurations.
4. **`common-test`**:
   - Shared testing fixtures, mock Kafka producers/consumers, Testcontainers configuration, and test data generators.

---

## 4. End-to-End Walkthrough of a Single Trade

To see how all 10 services interact in harmony, follow an order's journey from click to settlement:

```
Step 1: Alice clicks "Buy 0.5 BTC at $60,000" in the Web Terminal.
        Frontend attaches a unique Idempotency-Key and Alice's JWT Bearer token.

Step 2: API Gateway receives the HTTP POST request.
        - Checks Redis rate limit bucket (Alice hasn't exceeded 50 req/sec).
        - Verifies Alice's JWT signature using the public RSA key.
        - Routes request to Order Service.

Step 3: Order Service performs Fast-Path Idempotency check.
        - Checks Redis: has this Idempotency-Key been processed in the last 24h? (No).

Step 4: Order Service calls Pre-Trade Risk Service via gRPC HTTP/2.
        - Risk Service checks 6 rules (Order size <= max limit, price is within 10% collar, Alice isn't wash-trading against her own sell order).
        - Risk check passes in 0.4 ms.

Step 5: Order Service calculates required reserve amount:
        - 0.5 BTC * $60,000 = $30,000 USD.
        - Calls Account Service: Alice's available balance is reduced by $30,000; her reserved balance increases by $30,000.

Step 6: Order Service saves order to PostgreSQL in SUBMITTED state.
        - Simultaneously inserts an OrderPlacedEvent into its SQL Outbox table inside the same transaction.

Step 7: Outbox Poller queries the table using SELECT ... FOR UPDATE SKIP LOCKED.
        - Publishes the event to Kafka topic "order.commands" partitioned by instrument ("BTC_USD").

Step 8: Matching Engine's dedicated BTC_USD thread reads the event from Kafka.
        - Checks the Ask side of the book (sellers).
        - Finds Bob's resting limit order: "Sell 0.5 BTC at $60,000".
        - MATCH! The engine generates a TradeExecutedEvent (0.5 BTC @ $60,000).
        - Publishes TradeExecutedEvent to Kafka topic "trade.executions".

Step 9: Downstream Services consume TradeExecutedEvent in parallel:
        A. Account Service:
           - Settle funds: Debits Alice's reserved $30,000; credits Alice's available 0.5 BTC.
           - Debits Bob's reserved 0.5 BTC; credits Bob's available $30,000.
           - Writes 4 immutable double-entry ledger rows.
        B. Market Data Service:
           - Updates current market price to $60,000.
           - Adds trade to recent trade tape.
           - Broadcasts updated L2 depth ladder over WebSockets to Alice, Bob, and all other traders.
        C. Order Service:
           - Transitions Alice and Bob's orders to FILLED status in PostgreSQL.
        D. Audit Service:
           - Appends trade record to audit table with SHA-256 hash chained to the previous block.
```
