package com.dete.gateway.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class GatewayMetricsTest {

  private MeterRegistry registry;
  private GatewayMetrics gatewayMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    gatewayMetrics = new GatewayMetrics(registry);
  }

  @Test
  @DisplayName("Should increment gateway_requests_total with route and status")
  void shouldRecordRequest() {
    gatewayMetrics.recordRequest("/orders", 200);
    gatewayMetrics.recordRequest("/orders", 429);

    double count200 =
        registry
            .get("gateway_requests_total")
            .tag("route", "/orders")
            .tag("status", "200")
            .counter()
            .count();

    double count429 =
        registry
            .get("gateway_requests_total")
            .tag("route", "/orders")
            .tag("status", "429")
            .counter()
            .count();

    assertThat(count200).isEqualTo(1.0);
    assertThat(count429).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should increment gateway_rate_limited_total with limit type")
  void shouldRecordRateLimited() {
    gatewayMetrics.recordRateLimited("USER_RATE_LIMIT");

    double count =
        registry.get("gateway_rate_limited_total").tag("type", "USER_RATE_LIMIT").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should track circuit breaker state gauge")
  void shouldRecordCircuitBreakerState() {
    gatewayMetrics.recordCircuitBreakerState("orderService", 0);
    assertThat(registry.get("circuit_breaker_state").tag("name", "orderService").gauge().value())
        .isEqualTo(0.0);

    gatewayMetrics.recordCircuitBreakerState("orderService", 1);
    assertThat(registry.get("circuit_breaker_state").tag("name", "orderService").gauge().value())
        .isEqualTo(1.0);
  }
}
