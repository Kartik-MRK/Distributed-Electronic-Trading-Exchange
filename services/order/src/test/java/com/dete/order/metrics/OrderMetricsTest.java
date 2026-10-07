package com.dete.order.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderMetricsTest {

  private MeterRegistry registry;
  private OrderMetrics orderMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    orderMetrics = new OrderMetrics(registry);
  }

  @Test
  @DisplayName("Should increment orders_placed_total counter with correct tags")
  void shouldRecordOrdersPlaced() {
    orderMetrics.recordOrderPlaced(Instrument.BTC_USD, OrderSide.BUY, OrderType.LIMIT);
    orderMetrics.recordOrderPlaced(Instrument.BTC_USD, OrderSide.BUY, OrderType.LIMIT);

    double count =
        registry
            .get("orders_placed_total")
            .tag("instrument", "BTC-USD")
            .tag("side", "BUY")
            .tag("type", "LIMIT")
            .counter()
            .count();

    assertThat(count).isEqualTo(2.0);
  }

  @Test
  @DisplayName("Should increment orders_rejected_total counter with reason tag")
  void shouldRecordOrdersRejected() {
    orderMetrics.recordOrderRejected("INSUFFICIENT_FUNDS");

    double count =
        registry.get("orders_rejected_total").tag("reason", "INSUFFICIENT_FUNDS").counter().count();

    assertThat(count).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should gauge circuit breaker states dynamically")
  void shouldRecordCircuitBreakerState() {
    orderMetrics.recordCircuitBreakerState("accountService", 0);
    assertThat(registry.get("circuit_breaker_state").tag("name", "accountService").gauge().value())
        .isEqualTo(0.0);

    orderMetrics.recordCircuitBreakerState("accountService", 1);
    assertThat(registry.get("circuit_breaker_state").tag("name", "accountService").gauge().value())
        .isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should record and update consumer lag gauge")
  void shouldRecordConsumerLag() {
    orderMetrics.recordConsumerLag("order.events", "order-group", 42L);
    assertThat(
            registry
                .get("kafka_consumer_lag")
                .tag("topic", "order.events")
                .tag("group", "order-group")
                .gauge()
                .value())
        .isEqualTo(42.0);
  }
}
