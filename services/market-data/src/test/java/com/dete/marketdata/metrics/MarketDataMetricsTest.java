package com.dete.marketdata.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MarketDataMetricsTest {

  private MeterRegistry registry;
  private MarketDataMetrics marketDataMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    marketDataMetrics = new MarketDataMetrics(registry);
  }

  @Test
  @DisplayName("Should track active websocket connections dynamically via gauge")
  void shouldTrackWebsocketConnections() {
    marketDataMetrics.connectionEstablished();
    marketDataMetrics.connectionEstablished();
    assertThat(registry.get("websocket_connections_active").gauge().value()).isEqualTo(2.0);

    marketDataMetrics.connectionClosed();
    assertThat(registry.get("websocket_connections_active").gauge().value()).isEqualTo(1.0);
  }

  @Test
  @DisplayName("Should increment websocket_messages_total counter with destination tag")
  void shouldRecordWebsocketMessage() {
    marketDataMetrics.recordWebsocketMessage("/topic/orderbook.BTC_USDT");
    marketDataMetrics.recordWebsocketMessage("/topic/orderbook.BTC_USDT");

    double count =
        registry
            .get("websocket_messages_total")
            .tag("destination", "/topic/orderbook.BTC_USDT")
            .counter()
            .count();

    assertThat(count).isEqualTo(2.0);
  }

  @Test
  @DisplayName("Should record order end-to-end latency")
  void shouldRecordOrderE2eLatency() {
    marketDataMetrics.recordOrderE2eLatency(2_500_000L); // 2.5ms

    assertThat(registry.get("order_e2e_latency_seconds").timer().count()).isEqualTo(1);
  }
}
