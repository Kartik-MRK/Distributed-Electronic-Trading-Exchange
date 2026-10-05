package com.dete.audit.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class AuditMetrics {

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, AtomicLong> consumerLags = new ConcurrentHashMap<>();

  public AuditMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordAuditEventLogged(String eventType, String service) {
    Counter.builder("audit_events_logged_total")
        .description("Total number of immutable audit log entries appended")
        .tag("eventType", eventType != null ? eventType : "UNKNOWN")
        .tag("service", service != null ? service : "UNKNOWN")
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
