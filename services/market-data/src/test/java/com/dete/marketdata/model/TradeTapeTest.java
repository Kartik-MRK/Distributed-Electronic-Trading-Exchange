package com.dete.marketdata.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.trade.TradeExecutedEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TradeTapeTest {

  @Test
  @DisplayName("Should maintain bounded capacity and return most recent trades first")
  void testTradeTapeCapacityAndOrdering() {
    TradeTape tape = new TradeTape(5);

    for (int i = 1; i <= 8; i++) {
      TradeExecutedEvent trade =
          new TradeExecutedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Instrument.BTC_USD,
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              UUID.randomUUID(),
              50_000L + i,
              100L,
              i,
              Instant.now(),
              1);
      tape.addTrade(trade);
    }

    assertThat(tape.size()).isEqualTo(5);

    List<TradeExecutedEvent> recent = tape.getRecentTrades(3);
    assertThat(recent).hasSize(3);
    assertThat(recent.get(0).sequenceNumber()).isEqualTo(8L);
    assertThat(recent.get(1).sequenceNumber()).isEqualTo(7L);
    assertThat(recent.get(2).sequenceNumber()).isEqualTo(6L);
  }
}
