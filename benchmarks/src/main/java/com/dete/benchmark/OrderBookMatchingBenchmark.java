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
 * JMH Benchmark measuring pure matching engine throughput (orders/second) with zero Kafka overhead.
 * Target: > 100,000 orders/sec.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class OrderBookMatchingBenchmark {

  private OrderBook orderBook;
  private UUID makerAccount;
  private UUID takerAccount;
  private Instant fixedTimestamp;
  private long orderSeq;

  // Pre-cached event data for hot-path matching without SecureRandom/UUID generation overhead
  private static final int POOL_SIZE = 65536;
  private OrderPlacedEvent[] restingMakers;
  private OrderPlacedEvent[] aggressiveTakers;
  private int makerIndex;
  private int takerIndex;

  @Setup(Level.Trial)
  public void setupTrial() {
    makerAccount = new UUID(0x1000L, 0x1L);
    takerAccount = new UUID(0x2000L, 0x2L);
    fixedTimestamp = Instant.ofEpochMilli(1700000000000L);

    restingMakers = new OrderPlacedEvent[POOL_SIZE];
    aggressiveTakers = new OrderPlacedEvent[POOL_SIZE];

    for (int i = 0; i < POOL_SIZE; i++) {
      UUID makerId = new UUID(0xAA00L, i);
      UUID takerId = new UUID(0xBB00L, i);
      UUID eventId1 = new UUID(0xEE10L, i);
      UUID eventId2 = new UUID(0xEE20L, i);

      restingMakers[i] =
          new OrderPlacedEvent(
              eventId1,
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
              eventId2,
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
    orderSeq = 0;
    makerIndex = 0;
    takerIndex = 0;
  }

  /**
   * Measures complete match cycle: inserts a resting SELL order followed immediately by a crossing
   * aggressive BUY order which matches against it and clears the level.
   */
  @Benchmark
  public MatchResult thrptMakerTakerCycle() {
    int idx = (makerIndex++) & (POOL_SIZE - 1);
    orderBook.processOrder(restingMakers[idx]);
    return orderBook.processOrder(aggressiveTakers[idx]);
  }

  /**
   * Measures throughput of inserting non-crossing resting limit orders at shifting price levels.
   */
  @Benchmark
  public MatchResult thrptRestingLimitInsert() {
    long id = ++orderSeq;
    UUID orderId = new UUID(0xCC00L, id);
    long price = 40_000_00000000L + (id % 5000) * 10000000L;
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

  /** Measures O(1) order cancellation throughput. */
  @Benchmark
  public MatchResult thrptCancelOrder() {
    long id = ++orderSeq;
    UUID orderId = new UUID(0xDD00L, id);
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            orderId,
            orderId,
            makerAccount,
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            60_000_00000000L,
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
            .include(OrderBookMatchingBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(2)
            .measurementIterations(3)
            .build();
    new Runner(opt).run();
  }
}
