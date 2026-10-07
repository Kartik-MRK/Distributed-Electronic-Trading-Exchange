package com.dete.benchmark;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
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
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

/**
 * JMH Benchmark measuring latency percentiles (P50, P90, P99, Max) of core matching operations.
 * Target: P99 < 1ms (1000 microseconds).
 */
@BenchmarkMode({Mode.AverageTime, Mode.SampleTime})
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class MatchingLatencyBenchmark {

  private OrderBook orderBook;
  private UUID makerAccount;
  private UUID takerAccount;
  private Instant fixedTimestamp;
  private long seqCounter;

  private static final int POOL_SIZE = 32768;
  private OrderPlacedEvent[] restingMakers;
  private OrderPlacedEvent[] aggressiveTakers;
  private int index;

  @Setup(Level.Trial)
  public void setupTrial() {
    makerAccount = new UUID(0x3000L, 0x1L);
    takerAccount = new UUID(0x4000L, 0x2L);
    fixedTimestamp = Instant.ofEpochMilli(1700000000000L);

    restingMakers = new OrderPlacedEvent[POOL_SIZE];
    aggressiveTakers = new OrderPlacedEvent[POOL_SIZE];

    for (int i = 0; i < POOL_SIZE; i++) {
      UUID makerId = new UUID(0x1111L, i);
      UUID takerId = new UUID(0x2222L, i);
      restingMakers[i] =
          new OrderPlacedEvent(
              makerId,
              makerId,
              makerAccount,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              50_000_00000000L,
              1_00000000L,
              makerId,
              fixedTimestamp,
              1);
      aggressiveTakers[i] =
          new OrderPlacedEvent(
              takerId,
              takerId,
              takerAccount,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              50_000_00000000L,
              1_00000000L,
              takerId,
              fixedTimestamp,
              1);
    }
  }

  @Setup(Level.Iteration)
  public void setupIteration() {
    orderBook = new OrderBook(Instrument.BTC_USD);
    seqCounter = 0;
    index = 0;
  }

  /**
   * Measures the full latency distribution of matching an aggressive taker order against a resting
   * maker order in the book.
   */
  @Benchmark
  public MatchResult measureCrossingMatchLatency() {
    int idx = (index++) & (POOL_SIZE - 1);
    orderBook.processOrder(restingMakers[idx]);
    return orderBook.processOrder(aggressiveTakers[idx]);
  }

  /** Measures the latency distribution of inserting a resting limit order onto the price ladder. */
  @Benchmark
  public MatchResult measureRestingInsertLatency() {
    long id = ++seqCounter;
    UUID orderId = new UUID(0x5555L, id);
    long price = 48_000_00000000L + (id % 2000) * 10000000L;
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            orderId,
            orderId,
            makerAccount,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            price,
            1_00000000L,
            orderId,
            fixedTimestamp,
            1);
    return orderBook.processOrder(event);
  }

  /** Measures the latency distribution of cancelling an active resting order in O(1) time. */
  @Benchmark
  public MatchResult measureCancelLatency() {
    long id = ++seqCounter;
    UUID orderId = new UUID(0x6666L, id);
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            orderId,
            orderId,
            makerAccount,
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            55_000_00000000L,
            1_00000000L,
            orderId,
            fixedTimestamp,
            1);
    orderBook.processOrder(event);
    return orderBook.cancelOrder(orderId, makerAccount);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(MatchingLatencyBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .build();
    new Runner(opt).run();
  }
}
