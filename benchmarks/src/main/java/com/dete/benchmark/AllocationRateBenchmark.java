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
