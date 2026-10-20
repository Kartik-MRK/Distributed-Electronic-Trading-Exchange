package com.dete.benchmark;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.OrderBook;
import com.dete.matching.engine.model.MatchResult;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark measuring memory allocation profile and throughput on the critical path. Can be run
 * with GC profiler (-prof gc) to track B/op and allocation rates. Target: Near-zero allocs.
 */


/*
1. What Is Your Project? (The Big Picture)
Your project is named DETE (Distributed Electronic Trading Exchange).

At its core, it is a full-scale, institutional-grade cryptocurrency exchange simulator engineered in Java 21, Spring Boot 3, Apache Kafka, PostgreSQL 16, Redis 7, and Next.js 14.

Think of it like building your own Nasdaq, Binance, or Coinbase:

It allows users to register, log in, deposit currencies (USD, Bitcoin, Ethereum, Solana), place buy and sell orders, and watch trades execute in real time.
It features an in-memory Matching Engine (the central algorithmic computer that pairs buyers and sellers at mutually agreed prices).
It provides a live trading terminal (a dark-mode web dashboard showing real-time price charts, scrolling trade tickers, and the depth of the market).
It operates on three major markets: BTC_USD (Bitcoin), ETH_USD (Ethereum), and SOL_USD (Solana).
2. Why Did You Build It? (The Engineering Motivation)
Most developer portfolio projects are simple CRUD applications (Create, Read, Update, Delete—like blog websites, e-commerce stores, or to-do lists). In CRUD apps, if a database query takes 100 milliseconds, nobody cares, and data consistency is handed off to standard database transactions.

Financial exchanges represent the exact opposite: they face some of the hardest computer science and distributed systems problems in software engineering. You built DETE to solve these specific challenges:

A. The Concurrency & Race Condition Problem
In financial markets, thousands of traders may try to buy the exact same asset at the exact same microsecond (race condition—when the outcome depends on the unpredictable timing of competing operations).

If you use traditional database locks (SELECT ... FOR UPDATE), your exchange slows down to a crawl (taking 20–50 milliseconds per trade) and risks deadlocks (when two threads freeze permanently waiting for each other to release a lock).
If you don't lock, you risk double-fills (selling the same Bitcoin to two different buyers).
Why you built it: To prove how to match trades with zero locks and zero race conditions by using a Single-Writer Thread Model (assigning each market to a single, dedicated CPU thread that executes orders one after another sequentially in memory at over 1.5 million operations per second).
B. The Floating-Point Math Trap
In standard computer programming, floating-point numbers (float and double) cannot accurately represent decimal fractions in binary hardware. In Java:

0.1 + 0.2 = 0.30000000000000004
In banking and trading, these tiny rounding fractions cause "phantom pennies," broken balance sheets, and regulatory audit failures.

Why you built it: To demonstrate 100% mathematically exact finance using 64-bit Fixed-Point Arithmetic (representing every monetary value as an integer scaled by $10^8$, so $1.00 is stored as 100,000,000 raw units, completely eliminating rounding errors while running 152x faster than Java's BigDecimal).
C. The Double-Spending Dilemma
If a user has $10,000 in their account and clicks "Buy $10,000 of BTC" across two separate browser tabs at the exact same millisecond, what stops both orders from going through and spending $20,000 (double-spending)?

Why you built it: To demonstrate Two-Phase Balance Reservation (splitting money into "Available" spendable funds and "Reserved" funds that are locked while an order is open on the market). Before any order touches the matching engine, the money is locked synchronously, preventing double spending.
D. Microsecond Speed vs. Crash Durability
Writing every trade to a relational database disk takes 5 to 20 milliseconds (too slow for trading). Running purely in computer memory (RAM) takes less than 3 microseconds, but if the power flickers or the container restarts, all orders vanish!

Why you built it: To demonstrate Event Sourcing with Apache Kafka (logging every single order and trade as an immutable, append-only event on disk). The engine processes orders in RAM at microsecond speeds, but if it restarts, it replays past Kafka events to reconstruct the identical order book state from scratch.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class AllocationRateBenchmark {

  private OrderBook orderBook;
  private UUID makerAccount;
  private UUID takerAccount;
  private Instant fixedInstant;

  private static final int POOL_SIZE = 16384;
  private OrderPlacedEvent[] makers;
  private OrderPlacedEvent[] takers;
  private int index;

  private long priceA;
  private long qtyB;

  @Setup(Level.Trial)
  public void setupTrial() {
    makerAccount = new UUID(0x7000L, 0x1L);
    takerAccount = new UUID(0x8000L, 0x2L);
    fixedInstant = Instant.ofEpochMilli(1700000000000L);
    priceA = 65_000_00000000L;
    qtyB = 1_50000000L;

    makers = new OrderPlacedEvent[POOL_SIZE];
    takers = new OrderPlacedEvent[POOL_SIZE];
    for (int i = 0; i < POOL_SIZE; i++) {
      UUID mId = new UUID(0x9000L, i);
      UUID tId = new UUID(0xA000L, i);
      makers[i] =
          new OrderPlacedEvent(
              mId,
              mId,
              makerAccount,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              50_000_00000000L,
              1_00000000L,
              mId,
              fixedInstant,
              1);
      takers[i] =
          new OrderPlacedEvent(
              tId,
              tId,
              takerAccount,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              50_000_00000000L,
              1_00000000L,
              tId,
              fixedInstant,
              1);
    }
  }

  @Setup(Level.Iteration)
  public void setupIteration() {
    orderBook = new OrderBook(Instrument.BTC_USD);
    index = 0;
  }

  /** Measures hot-path primitive arithmetic with zero heap allocation. */
  @Benchmark
  public void primitiveScaledMath(Blackhole bh) {
    long notional = (priceA * qtyB) / FixedPoint.SCALE;
    long add = priceA + qtyB;
    long sub = priceA - qtyB;
    bh.consume(notional);
    bh.consume(add);
    bh.consume(sub);
  }

  /** Measures FixedPoint utility arithmetic allocation. */
  @Benchmark
  public void fixedPointMultiplyMath(Blackhole bh) {
    long result = FixedPoint.multiply(priceA, qtyB);
    bh.consume(result);
  }

  /** Measures OrderBook matching loop allocation. */
  @Benchmark
  public MatchResult orderBookProcessMatching() {
    int idx = (index++) & (POOL_SIZE - 1);
    orderBook.processOrder(makers[idx]);
    return orderBook.processOrder(takers[idx]);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(AllocationRateBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .addProfiler("org.openjdk.jmh.profile.GCProfiler")
            .build();
    new Runner(opt).run();
  }
}
