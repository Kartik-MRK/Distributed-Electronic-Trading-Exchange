package com.dete.matching.benchmark;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.OrderBook;
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
 * JMH Microbenchmark measuring the latency and throughput of core OrderBook operations: 1. Limit
 * order resting insertion 2. Crossing limit order matching 3. O(1) order cancellation
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 1)
@Measurement(iterations = 2, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class OrderBookBenchmark {

  private OrderBook orderBook;
  private UUID account1;
  private UUID account2;
  private long priceCounter;

  @Setup(Level.Trial)
  public void setupTrial() {
    account1 = UUID.randomUUID();
    account2 = UUID.randomUUID();
  }

  @Setup(Level.Invocation)
  public void setupInvocation() {
    orderBook = new OrderBook(Instrument.BTC_USD);
    priceCounter = 50_000_00000000L;
  }

  @Benchmark
  public void benchmarkLimitOrderInsert() {
    UUID orderId = UUID.randomUUID();
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            orderId,
            account1,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            priceCounter--,
            1_00000000L,
            UUID.randomUUID(),
            Instant.now(),
            1);
    orderBook.processOrder(event);
  }

  @Benchmark
  public void benchmarkLimitOrderMatch() {
    UUID makerId = UUID.randomUUID();
    OrderPlacedEvent maker =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            makerId,
            account1,
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000_00000000L,
            1_00000000L,
            UUID.randomUUID(),
            Instant.now(),
            1);
    orderBook.processOrder(maker);

    UUID takerId = UUID.randomUUID();
    OrderPlacedEvent taker =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            takerId,
            account2,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000_00000000L,
            1_00000000L,
            UUID.randomUUID(),
            Instant.now(),
            1);
    orderBook.processOrder(taker);
  }

  @Benchmark
  public void benchmarkCancelOrder() {
    UUID orderId = UUID.randomUUID();
    OrderPlacedEvent event =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            orderId,
            account1,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000_00000000L,
            1_00000000L,
            UUID.randomUUID(),
            Instant.now(),
            1);
    orderBook.processOrder(event);
    orderBook.cancelOrder(orderId, account1);
  }

  public static void main(String[] args) throws Exception {
    Options opt =
        new OptionsBuilder()
            .include(OrderBookBenchmark.class.getSimpleName())
            .forks(1)
            .warmupIterations(1)
            .measurementIterations(2)
            .build();
    new Runner(opt).run();
  }
}
