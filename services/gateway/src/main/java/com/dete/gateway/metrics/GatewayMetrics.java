package com.dete.gateway.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class GatewayMetrics {

  private final MeterRegistry registry;
  private final ConcurrentHashMap<String, AtomicInteger> circuitBreakerStates =
      new ConcurrentHashMap<>();

  public GatewayMetrics(MeterRegistry registry) {
    this.registry = registry;
  }

  public void recordRequest(String route, int statusCode) {
    Counter.builder("gateway_requests_total")
        .description("Total number of requests routed through the gateway")
        .tag("route", route != null ? route : "UNKNOWN")
        .tag("status", String.valueOf(statusCode))
        .register(registry)
        .increment();
  }

  public void recordRateLimited(String limitType) {
    Counter.builder("gateway_rate_limited_total")
        .description("Total number of requests rejected by gateway rate limiter")
        .tag("type", limitType != null ? limitType : "UNKNOWN")
        .register(registry)
        .increment();
  }

  public void recordCircuitBreakerState(String name, int state) {
    circuitBreakerStates
        .computeIfAbsent(
            name,
            n -> {
              AtomicInteger stateHolder = new AtomicInteger(state);
              registry.gauge(
                  "circuit_breaker_state",
                  io.micrometer.core.instrument.Tags.of("name", n),
                  stateHolder);
              return stateHolder;
            })
        .set(state);
  }
}
