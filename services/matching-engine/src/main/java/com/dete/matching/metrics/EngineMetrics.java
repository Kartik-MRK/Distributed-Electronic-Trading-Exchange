package com.dete.matching.metrics;

import com.dete.common.domain.enums.Instrument;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class EngineMetrics {

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, AtomicLong> consumerLags = new ConcurrentHashMap<>();

  public EngineMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordTradeExecuted(Instrument instrument, int count) {
    Counter.builder("trades_executed_total")
        .description("Total number of trades executed by the matching engine")
        .tag("instrument", instrument != null ? instrument.symbol() : "UNKNOWN")
        .register(registry)
        .increment(count);
  }

  public void recordMatchingLatency(Instrument instrument, long durationNanos) {
    Timer.builder("matching_latency_seconds")
        .description("In-memory matching latency in seconds")
        .tag("instrument", instrument != null ? instrument.symbol() : "UNKNOWN")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
  }

  public void recordOrderE2eLatency(Instrument instrument, long durationNanos) {
    Timer.builder("order_e2e_latency_seconds")
        .description("Order end-to-end processing latency in seconds")
        .tag("instrument", instrument != null ? instrument.symbol() : "UNKNOWN")
        .publishPercentiles(0.5, 0.95, 0.99)
        .register(registry)
        .record(durationNanos, TimeUnit.NANOSECONDS);
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
}
