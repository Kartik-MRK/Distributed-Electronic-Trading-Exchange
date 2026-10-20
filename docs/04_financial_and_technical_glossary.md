# 04 — Financial & Technical Glossary

This glossary provides plain-English definitions and concrete examples for every financial, mathematical, and distributed systems term used throughout the **Distributed Electronic Trading Exchange (DETE)**.

---

## 🏛️ Part 1: Financial & Trading Concepts

### 1. Order Book
* **What it is**: The organized, real-time list of all active buy and sell orders waiting in the market to be filled.
* **How it works**: The book is divided into two halves:
  - **Bids (Buy Orders)**: What buyers are willing to pay. Sorted with the **highest price at the top**.
  - **Asks (Sell Orders / Offers)**: What sellers are willing to accept. Sorted with the **lowest price at the top**.
* **Why it matters**: It provides market transparency, allowing traders to see how much supply and demand exists at every price level.

### 2. Limit Order vs. Market Order
* **Limit Order**: An instruction to buy or sell an asset at a **specific price or better**.
  - *Example*: "Buy 1 BTC at $60,000." If the current market price is $60,500, your order rests in the book waiting until someone is willing to sell at $60,000.
* **Market Order**: An instruction to execute **immediately at the current best available price**, regardless of the cost.
  - *Example*: "Buy 1 BTC right now at whatever price sellers are offering." It consumes existing resting orders on the book.

### 3. Bid-Ask Spread
* **What it is**: The gap between the highest price a buyer is willing to pay (**Best Bid**) and the lowest price a seller is willing to accept (**Best Ask**).
* *Example*: If the highest bid for Bitcoin is $60,000 and the lowest ask is $60,010, the spread is $10. A narrow spread indicates a healthy, highly liquid market.

### 4. Maker vs. Taker
* **Maker (Liquidity Provider)**: A trader who places a limit order that does not match immediately. Their order "rests" on the order book, creating market liquidity (they "make" the market).
* **Taker (Liquidity Consumer)**: A trader whose order matches immediately against an existing resting order, removing liquidity from the book (they "take" market depth).

### 5. Level 2 (L2) Market Depth
* **What it is**: An aggregated view of the order book that combines all orders at each individual price level.
* *Example*: Instead of showing 10 separate orders of 0.1 BTC at $60,000, L2 depth shows a single aggregated line: **$60,000 — Total Volume: 1.0 BTC (10 Orders)**. DETE broadcasts the top 20 L2 levels in real-time over WebSockets.

### 6. Slippage
* **What it is**: The difference between the expected price of a market order and the actual price at which it executes.
* *Why it happens*: If you place a large market order to buy 10 BTC, and the lowest ask has only 2 BTC at $60,000, your order will consume those 2 BTC and then buy the remaining 8 BTC at higher prices ($60,050, $60,100), increasing your average execution price.

### 7. Fat-Finger Error & Price Collars
* **Fat-Finger Error**: A human typo where a trader accidentally types an extreme price (e.g., trying to sell Bitcoin at $6,000 instead of $60,000).
* **Price Collar**: A safety rule enforced by the DETE Risk Service that automatically rejects any order whose price deviates by more than 10% from the last traded market price, protecting traders from catastrophic mistakes.

### 8. Self-Trade Prevention (STP) & Wash Trading
* **Wash Trading**: An illegal market abuse where a trader buys and sells with themselves to fabricate artificial volume and mislead other participants.
* **Self-Trade Prevention**: A mechanism in DETE that detects if an incoming order would match against an existing resting order belonging to the exact same trader account. If detected, the incoming order is safely rejected.

### 9. Notional Value
* **What it is**: The total dollar value of a financial position or trade.
* *Formula*: $\text{Notional Value} = \text{Price} \times \text{Quantity}$
* *Example*: An order to buy 2.5 BTC at $60,000 has a notional value of $150,000 USD.

### 10. Available Balance vs. Reserved Balance
* **Available Balance**: Money or crypto currently free to be used for new orders or withdrawals.
* **Reserved Balance**: Money or crypto locked by the exchange because it is currently tied up in open, unfilled limit orders.
* *Why it matters*: Prevents double-spending. If you have $10,000 and place a $6,000 buy order, your available balance becomes $4,000 and your reserved balance becomes $6,000.

### 11. Clearing & Settlement
* **Clearing**: The process of calculating what each party owes after a trade executes (reconciling obligations).
* **Settlement**: The actual transfer of funds and assets between the buyer and seller accounts in the database ledger.

### 12. Double-Entry Bookkeeping
* **What it is**: An accounting standard where every financial transaction requires balanced, opposing entries: every **Debit** must be matched by an equal **Credit**.
* *Rule*: Money cannot be created or destroyed. In DETE, when a trade settles, 4 entries are recorded simultaneously: buyer's reserved cash is debited, buyer's crypto is credited, seller's reserved crypto is debited, and seller's cash is credited.

### 13. Balance Drift & Reconciliation
* **Balance Drift**: A catastrophic bug where the sum of user balances does not equal the historical sum of ledger entries (meaning money was silently lost or invented).
* **Reconciliation**: A mathematical audit process that continuously checks that total balances equal net ledger transactions. If drift is non-zero, critical alarms are triggered.

### 14. Candlestick (OHLCV) Chart
* **What it is**: A standard visual chart displaying price movements over a specific timeframe (e.g., 1 minute, 5 minutes, 1 hour).
* **The 5 Data Points**:
  - **O (Open)**: First price traded in that window.
  - **H (High)**: Highest price reached in that window.
  - **L (Low)**: Lowest price reached in that window.
  - **C (Close)**: Final price traded in that window.
  - **V (Volume)**: Total quantity of assets traded during the window.

---

## 💻 Part 2: Technical & Distributed Systems Concepts

### 1. Single-Writer Thread Model
* **What it is**: An architectural pattern where only one dedicated thread is permitted to modify a specific piece of in-memory data (in DETE, each trading pair like `BTC_USD` has its own thread).
* **Why it matters**: Eliminates the need for locks (`synchronized`, `ReentrantLock`). Because multiple threads never compete for the same memory, there is zero lock contention, zero race conditions, and massive execution speed (>1.5 million ops/sec).

### 2. Fixed-Point Arithmetic (vs. Floating-Point)
* **What it is**: Representing decimal numbers as integers scaled by a fixed multiplier.
* **The Multiplier in DETE**: $\text{SCALE} = 100,000,000L$ ($10^8$, 8 decimal places).
* *Comparison*:
  - **Floating-point (`double`)**: Fast, but causes precision rounding errors (`0.1 + 0.2 = 0.30000000000000004`), which can cause financial loss.
  - **`BigDecimal`**: Exact, but creates objects on the heap, triggering frequent Garbage Collection (GC) pauses that ruin low latency.
  - **Fixed-Point (`long`)**: 100% exact, operates on primitive 64-bit CPU registers with zero heap allocation, running 152x faster than `BigDecimal`.

### 3. Event Sourcing
* **What it is**: A design pattern where the state of a system is recorded as a continuous sequence of immutable events in an append-only log (Apache Kafka), rather than simply overwriting the current state in a database.
* **Why it matters**: Allows complete auditability, time-travel debugging, and instant disaster recovery (replaying past events to restore the in-memory order book after a crash).

### 4. CQRS (Command Query Responsibility Segregation)
* **What it is**: Separating the path that modifies data (**Command / Write Path**) from the path that reads data (**Query / Read Path**).
* **In DETE**:
  - **Write Path**: Order Service $\rightarrow$ Kafka $\rightarrow$ Matching Engine (authoritative state).
  - **Read Path**: Market Data Service consumes Kafka events to build a read-optimized in-memory materialized view that serves thousands of WebSocket clients without putting load on the core engine.

### 5. Transactional Outbox Pattern & `SKIP LOCKED`
* **What it is**: A pattern that solves the dual-write problem by saving an application entity and its outbound Kafka event inside the same atomic relational database transaction.
* **`SELECT ... FOR UPDATE SKIP LOCKED`**: An advanced SQL concurrency feature. When multiple background polling threads read from the outbox table, `SKIP LOCKED` tells each thread to automatically ignore rows that other threads are currently processing, preventing duplicate publishing and lock contention.

### 6. Idempotency & Two-Tier Idempotency
* **What it is**: A property of an operation where performing it once produces the exact same result as performing it multiple times.
* **Two-Tier Idempotency in DETE**:
  - **Tier 1 (Redis)**: Rapidly checks whether the client's `Idempotency-Key` was submitted in the last 24 hours.
  - **Tier 2 (PostgreSQL)**: Enforces a `UNIQUE` database constraint on the `idempotency_key` column so even if Redis restarts, duplicate orders are rejected.

### 7. gRPC over HTTP/2
* **What it is**: A modern, high-performance binary Remote Procedure Call framework developed by Google.
* **Why it is faster than REST/JSON**:
  - Uses **HTTP/2 multiplexing**: multiple requests travel over a single persistent TCP connection.
  - Uses **Protocol Buffers**: data is serialized into compact binary bytes instead of bulky human-readable JSON text strings.
  - In DETE, gRPC completes pre-trade risk checks in under **0.5 milliseconds**.

### 8. STOMP over WebSockets
* **WebSocket**: A persistent, bidirectional, full-duplex TCP communication channel between client browsers and the backend server.
* **STOMP (Simple Text Oriented Messaging Protocol)**: A lightweight messaging protocol running on top of WebSockets that provides publish/subscribe topics (e.g., subscribing to `/ws/topic/market-data.BTC_USD`).

### 9. Token Bucket Rate Limiting
* **What it is**: An algorithm used to control network traffic and prevent API abuse.
* **How it works**: A virtual bucket holds a fixed number of tokens. Every second, new tokens are added up to a maximum capacity. Each API request consumes one token. If the bucket is empty, incoming requests are rejected with HTTP 429 (*Too Many Requests*).

### 10. RS256 Asymmetric JWT & JWKS
* **JWT (JSON Web Token)**: A digitally signed security credential carrying user identity and permissions.
* **RS256 (RSA Signature with SHA-256)**: An asymmetric encryption algorithm:
  - The Auth Service signs the token using its secret **Private Key**.
  - All other microservices verify the token using the public **Public Key**.
* **JWKS (JSON Web Key Set)**: A standardized web endpoint (`/.well-known/jwks.json`) where microservices automatically fetch public keys without needing hardcoded credentials.

### 11. Cryptographic Hash Chaining (SHA-256)
* **What it is**: Linking database rows together cryptographically, where each new row's hash includes the hash of the row that came before it:
  $$\text{Current Hash} = \text{SHA-256}(\text{Previous Hash} + \text{Record Data})$$
* **Why it matters**: Provides tamper evidence. If an attacker or rogue administrator modifies any historical row in the database, the hash chain breaks from that point forward, immediately flagging the tampering.

### 12. JVM Ergonomics & G1GC Tuning
* **JVM Ergonomics**: The automated process where the Java Virtual Machine detects available CPU and memory to configure its internal settings. In Docker containers with fractional CPU quotas (e.g., `0.5` cores), Java can miscalculate and pick the single-threaded `SerialGC`, causing long latency pauses.
* **G1GC Tuning in DETE**: Explicitly configuring the **Garbage-First (G1) Garbage Collector** with `-XX:+UseG1GC` and `-XX:MaxGCPauseMillis=20` to guarantee memory cleanups complete in under 20 milliseconds, preserving low-latency trading guarantees.

### 13. JMH (Java Microbenchmark Harness)
* **What it is**: An official OpenJDK tool designed to measure the nanosecond-level performance of Java code while avoiding JVM warm-up traps, dead-code elimination, and just-in-time (JIT) compilation anomalies.
