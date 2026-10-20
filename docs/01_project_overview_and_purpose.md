# 01 — Project Overview & Purpose: Distributed Electronic Trading Exchange (DETE)

---

## 1. What Is This Project?

**DETE (Distributed Electronic Trading Exchange)** is a full-scale, distributed financial exchange simulator built from the ground up in **Java 21, Spring Boot 3, Apache Kafka, PostgreSQL 16, Redis 7, and Next.js 14**.

At its core, DETE works just like real-world financial platforms—such as **Nasdaq**, the **New York Stock Exchange (NYSE)**, or cryptocurrency exchanges like **Binance** and **Coinbase**. It provides an institutional trading platform for three major cryptocurrency pairs:
- `BTC_USD` (Bitcoin priced in US Dollars)
- `ETH_USD` (Ethereum priced in US Dollars)
- `SOL_USD` (Solana priced in US Dollars)

On this platform, users can:
1. **Register and authenticate securely** with digital tokens (JSON Web Tokens using public/private key cryptography).
2. **Deposit and manage funds** across multiple currencies (USD, BTC, ETH, SOL).
3. **Submit trading orders** to buy or sell assets at specific prices (*Limit Orders*) or immediate prevailing prices (*Market Orders*).
4. **Watch real-time market updates**—including the live *Order Book* (the visible list of outstanding buy and sell offers waiting to be matched), a live scrolling *Trade Tape* (history of recently executed deals), and interactive candlestick price charts.
5. **Rely on guaranteed financial consistency**—meaning not a single cent can vanish, be duplicated, or be spent twice.

---

## 2. Why Was DETE Built? The Engineering Motivation

Most portfolio projects are simple **CRUD applications** (*Create, Read, Update, Delete*—like basic e-commerce shops, blog platforms, or todo lists). In CRUD apps, the database handles almost everything, requests are independent, and if a web page takes 200 milliseconds to load, nobody notices.

Financial exchanges are the **exact opposite**. They present some of the hardest computer science and distributed systems challenges in existence:

### A. The Extreme Concurrency & Race Condition Problem
In an active market, thousands of traders may attempt to buy the exact same asset at the exact same microsecond (*race condition*—a situation where the outcome depends on the unpredictable order in which concurrent operations finish). If two buyers both try to purchase the last available 0.5 Bitcoin:
- A naive database setup using regular SQL row locks will either freeze up (*deadlock*—threads permanently waiting on each other) or run painfully slow (taking 10 to 50 milliseconds per trade).
- If you don't lock carefully, you get a catastrophic *double-fill* (selling the same asset to two different people).

**DETE was built to demonstrate how to achieve zero locks and zero race conditions on the matching path using a Single-Writer Thread Model** (running matching on a dedicated CPU thread per market that processes orders one after another deterministically at over 1.5 million operations per second).

### B. The Floating-Point Math Catastrophe
In everyday programming, developers use floating-point numbers (`float` or `double`). However, in computer hardware, binary floating-point numbers cannot accurately represent numbers like `0.1` or `0.01`. In Java:
```
0.1 + 0.2 = 0.30000000000000004
```
In banking and exchanges, these rounding anomalies cause "phantom money," balance sheet drift, and regulatory audits.

**DETE was built to demonstrate 100% mathematically exact finance using 64-bit Fixed-Point Arithmetic** (storing every dollar and crypto token as an integer scaled by $10^8$, meaning 8 decimal places: $1.00000000\text{ USD} = 100,000,000\text{ raw units}$, eliminating rounding bugs forever).

### C. The Double-Spending & Balance Reservation Problem
If a trader has $\$10,000$ in their account, what stops them from opening two browser tabs and clicking "Buy $\$10,000$ of Bitcoin" on both tabs at the exact same millisecond?

If the exchange doesn't coordinate state instantly, both orders might get accepted, spending $\$20,000$ on a $\$10,000$ balance (*double-spending*).

**DETE solves this with Synchronous Two-Phase Balance Reservation**:
1. Before any order is ever sent to the matching engine, the Order Service calls the Account Service to lock the required money.
2. The account splits money into two buckets:
   - **Available Balance**: Funds free to trade or withdraw.
   - **Reserved Balance**: Funds locked because they are currently tied up in open, resting orders on the market.
3. If you have $\$10,000$ available and place a $\$10,000$ buy order, your available balance immediately drops to $\$0$, and your reserved balance becomes $\$10,000$. The second tab's order is rejected with `Insufficient Funds`.

### D. The Distributed Consistency Dilemma: Speed vs. Durability
A relational database (like PostgreSQL) is rock-solid for storing money, but writing to disk on every single price update takes several milliseconds—far too slow for a high-speed exchange. On the other hand, storing orders purely in computer memory (RAM) is lightning fast (sub-microsecond), but if the server crashes or loses power, all orders vanish!

**DETE bridges this gap using Event Sourcing with Apache Kafka**:
- Orders and trades are published to an immutable, append-only log in Apache Kafka (*event stream*).
- The Matching Engine operates purely in high-speed RAM.
- If the Matching Engine crashes, it instantly recovers its exact state by replaying past events from the Kafka log from its last known checkpoint (*deterministic event log replay*).

---

## 3. The Big Picture: How DETE Solves the Entire Lifecycle

Here is the high-level life of a trade inside DETE:

```
[Trader / Bot]
      │
      ▼ (Submits Order via REST API with Idempotency-Key)
[API Gateway]  ──(Checks Redis Token Bucket Rate Limit & RS256 JWT Token)──► [Valid]
      │
      ▼ (HTTP/2 gRPC Call: ~0.5ms)
[Pre-Trade Risk Service]  ──(Checks 6 Risk Rules: Size, Notional, Price Collars, STP)──► [Passed]
      │
      ▼ (Synchronous Balance Reservation)
[Account Service]  ──(Moves Funds from Available to Reserved)──► [Reserved]
      │
      ▼ (Writes to PostgreSQL Outbox table with SKIP LOCKED)
[Order Service]
      │
      ▼ (Kafka topic: "order.commands")
[Matching Engine (RAM)]  ──(Single-Writer FIFO Match: ~2 microseconds)──► [Matched!]
      │
      ├──► Kafka topic: "trade.executions"
      └──► Kafka topic: "order.events"
            │
            ├─────────────────────────┬─────────────────────────┐
            ▼                         ▼                         ▼
   [Account / Ledger]        [Market Data Service]       [Audit Service]
 (Double-Entry Bookkeeping)  (WebSocket L2 Depth/OHLCV) (Cryptographic SHA-256)
```

1. **User Authenticates**: Obtains a secure digital passport (*JWT - JSON Web Token* signed with RS256 asymmetric cryptography).
2. **User Submits an Order**: The order passes through an API Gateway, checking rate limits (*Token Bucket algorithm* in Redis) to block denial-of-service spam.
3. **Risk Verification**: The Risk Service performs 6 instant pre-trade checks over ultra-fast gRPC (e.g., verifying you aren't trading with yourself—*Self-Trade Prevention*—or submitting an absurd price—*Fat-Finger Price Collar*).
4. **Fund Reservation**: The Account Service reserves the needed currency in the database so it cannot be double-spent.
5. **Matching**: The order is handed via Kafka to the in-memory Matching Engine. The engine checks the opposite side of the book (bids vs. asks). If prices cross, a trade executes in **under 2.5 microseconds**.
6. **Settlement**: The trade execution event triggers the Account Service to perform **Double-Entry Bookkeeping** (crediting and debiting both accounts across four atomic ledger entries).
7. **Broadcast**: The Market Data Service broadcasts the new price and updated order book to thousands of connected web browsers in real-time over **WebSockets**.
8. **Compliance Audit**: The Audit Service writes an immutable record sealed with **cryptographic SHA-256 hashing**, making any data tampering mathematically impossible to hide.

---

## 4. Key Takeaways

| Question | Answer |
|---|---|
| **What is it?** | A high-performance, distributed electronic crypto exchange simulator with microservices, an in-memory matching engine, a double-entry ledger, and a real-time web terminal. |
| **Why was it built?** | To solve the core engineering challenges of high-frequency finance: microsecond order matching, concurrency without database locks, exact mathematical precision, double-spending prevention, and multi-service fault tolerance. |
| **What makes it realistic?** | It enforces institutional trading rules: Price-Time priority, Self-Trade Prevention, strict double-entry ledger triggers, and automated mathematical balance reconciliation. |
