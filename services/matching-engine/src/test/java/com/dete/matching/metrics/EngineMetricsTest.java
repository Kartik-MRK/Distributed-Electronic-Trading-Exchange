package com.dete.matching.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EngineMetricsTest {

  private MeterRegistry registry;
  private EngineMetrics engineMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    engineMetrics = new EngineMetrics(registry);
  }

  @Test
  @DisplayName("Should increment trades_executed_total with instrument tag")
  void shouldRecordTradesExecuted() {
    engineMetrics.recordTradeExecuted(Instrument.BTC_USD, 3);

    double count =
        registry
            .get("trades_executed_total")
            .tag("instrument", "BTC-USD")
            .counter()
            .count();

    assertThat(count).isEqualTo(3.0);
  }

  @Test
  @DisplayName("Should record matching latency and e2e latency timers")
  void shouldRecordLatencies() {
    engineMetrics.recordMatchingLatency(Instrument.ETH_USD, 150_000L); // 150 microseconds
    engineMetrics.recordOrderE2eLatency(Instrument.ETH_USD, 1_200_000L); // 1.2 milliseconds

    assertThat(
            registry
                .get("matching_latency_seconds")
                .tag("instrument", "ETH-USD")
                .timer()
                .count())
        .isEqualTo(1);

    assertThat(
            registry
                .get("order_e2e_latency_seconds")
                .tag("instrument", "ETH-USD")
                .timer()
                .count())
        .isEqualTo(1);
  }

  @Test
  @DisplayName("Should record consumer lag gauge")
  void shouldRecordConsumerLag() {
    engineMetrics.recordConsumerLag("order.commands", "engine-group", 12L);
    assertThat(
            registry
                .get("kafka_consumer_lag")
                .tag("topic", "order.commands")
                .tag("group", "engine-group")
                .gauge()
                .value())
        .isEqualTo(12.0);
  }
}
