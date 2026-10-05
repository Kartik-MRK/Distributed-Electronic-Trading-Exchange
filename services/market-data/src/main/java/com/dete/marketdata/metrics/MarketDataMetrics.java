package com.dete.marketdata.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class MarketDataMetrics {

  private final MeterRegistry registry;
  private final AtomicInteger activeConnections = new AtomicInteger(0);
  private final ConcurrentHashMap<String, AtomicLong> consumerLags = new ConcurrentHashMap<>();

  public MarketDataMetrics(MeterRegistry registry) {
    this.registry = registry;
    registry.gauge("websocket_connections_active", activeConnections);
  }

  public void connectionEstablished() {
    activeConnections.incrementAndGet();
  }

  public void connectionClosed() {
    activeConnections.updateAndGet(c -> Math.max(0, c - 1));
  }

  public int getActiveConnections() {
    return activeConnections.get();
  }

  public void recordWebsocketMessage(String destination) {
    Counter.builder("websocket_messages_total")
        .description("Total number of outbound WebSocket messages broadcasted")
        .tag("destination", destination != null ? destination : "UNKNOWN")
        .register(registry)
        .increment();
  }

  public void recordOrderE2eLatency(long durationNanos) {
    Timer.builder("order_e2e_latency_seconds")
        .description("Order end-to-end latency from submission to market data broadcast")
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
