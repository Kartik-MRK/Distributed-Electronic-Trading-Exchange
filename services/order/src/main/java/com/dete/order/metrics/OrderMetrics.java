package com.dete.order.metrics;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, AtomicInteger> circuitBreakerStates =
      new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, AtomicLong> consumerLags = new ConcurrentHashMap<>();

  public OrderMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordOrderPlaced(Instrument instrument, OrderSide side, OrderType type) {
    Counter.builder("orders_placed_total")
        .description("Total number of orders placed")
        .tag("instrument", instrument != null ? instrument.symbol() : "UNKNOWN")
        .tag("side", side != null ? side.name() : "UNKNOWN")
        .tag("type", type != null ? type.name() : "UNKNOWN")
        .register(registry)
        .increment();
  }

  public void recordOrderRejected(String reason) {
    Counter.builder("orders_rejected_total")
        .description("Total number of orders rejected")
        .tag("reason", reason != null ? reason : "UNKNOWN")
        .register(registry)
        .increment();
  }

  public void recordCircuitBreakerState(String breakerName, int state) {
    circuitBreakerStates
        .computeIfAbsent(
            breakerName,
            name -> {
              AtomicInteger stateHolder = new AtomicInteger(state);
              registry.gauge(
                  "circuit_breaker_state",
                  io.micrometer.core.instrument.Tags.of("name", name),
                  stateHolder);
              return stateHolder;
            })
        .set(state);
  }

  public void recordConsumerLag(String topic, String group, long lag) {
    String key = topic + ":" + group;
    consumerLags
        .computeIfAbsent(
            key,
            k -> {
              AtomicLong lagHolder = new AtomicLong(lag);
              registry.gauge(
                  "kafka_consumer_lag",
                  io.micrometer.core.instrument.Tags.of("topic", topic, "group", group),
                  lagHolder);
              return lagHolder;
            })
        .set(lag);
  }

  public Timer.Sample startDbTimer() {
    return Timer.start(registry);
  }

  public void stopDbTimer(Timer.Sample sample, String queryType) {
    sample.stop(
        Timer.builder("db_query_latency_seconds")
            .description("Database query execution latency in seconds")
            .tag("query", queryType)
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry));
  }
}
