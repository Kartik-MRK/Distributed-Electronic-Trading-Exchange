package com.dete.account.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class AccountMetrics {

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, AtomicLong> consumerLags = new ConcurrentHashMap<>();

  public AccountMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordLedgerEntry(String entryType) {
    Counter.builder("ledger_entries_total")
        .description("Total number of immutable double-entry ledger records created")
        .tag("type", entryType != null ? entryType : "UNKNOWN")
        .register(registry)
        .increment();
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
