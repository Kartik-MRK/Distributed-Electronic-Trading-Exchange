package com.dete.marketdata.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.common.domain.enums.*;
import com.dete.common.events.marketdata.OrderBookSnapshotEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.Interval;
import com.dete.marketdata.repository.MarketDataTradeRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MarketDataServiceTest {
  private final MarketDataTradeRepository repository = mock(MarketDataTradeRepository.class);
  private final MarketDataWebSocketBroadcaster broadcaster =
      mock(MarketDataWebSocketBroadcaster.class);
  private final MarketDataService service =
      new MarketDataService(100, new OHLCVManager(100), repository, broadcaster);

  private TradeExecutedEvent trade() {
    return new TradeExecutedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instrument.BTC_USD,
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        UUID.randomUUID(),
        100_000_000L,
        100_000_000L,
        1L,
        Instant.now(),
        1);
  }

  @Test
  void redeliveryDoesNotInflateVolume() {
    var trade = trade();
    when(repository.saveTrade(trade)).thenReturn(true, false);
    service.handleTradeExecuted(trade);
    service.handleTradeExecuted(trade);
    assertThat(service.getRecentTrades(Instrument.BTC_USD, 10)).hasSize(1);
    assertThat(service.getCandles(Instrument.BTC_USD, Interval.ONE_MINUTE, 10).getFirst().volume())
        .isEqualTo(trade.quantity());
    verify(broadcaster, times(1)).broadcastTrade(Instrument.BTC_USD, trade);
  }

  @Test
  void failedPersistenceLeavesProjectionUntouchedAndCanRetry() {
    var trade = trade();
    when(repository.saveTrade(trade))
        .thenThrow(new IllegalStateException("database unavailable"))
        .thenReturn(true);
    assertThatThrownBy(() -> service.handleTradeExecuted(trade))
        .isInstanceOf(IllegalStateException.class);
    assertThat(service.getRecentTrades(Instrument.BTC_USD, 10)).isEmpty();
    assertThat(service.getLastTradedPrice(Instrument.BTC_USD)).isZero();
    service.handleTradeExecuted(trade);
    assertThat(service.getRecentTrades(Instrument.BTC_USD, 10)).hasSize(1);
  }

  @Test
  void onlyEngineSnapshotsChangePublicDepth() {
    UUID id = UUID.randomUUID();
    service.registerOrder(id, UUID.randomUUID(), Instrument.BTC_USD, OrderSide.BUY, 100L, 10L);
    assertThat(service.getL2Snapshot(Instrument.BTC_USD, 10).bids()).isEmpty();
    service.handleOrderBookSnapshot(
        OrderBookSnapshotEvent.of(
            Instrument.BTC_USD,
            5,
            List.of(new OrderBookSnapshotEvent.SnapshotLevel(100L, 10L, 1)),
            List.of()));
    service.handleOrderPartiallyFilled(id, 5L, 5L);
    service.handleOrderRejected(id, "late rejection");
    assertThat(service.getL2Snapshot(Instrument.BTC_USD, 10).bids().getFirst().volume())
        .isEqualTo(10L);
    service.handleOrderBookSnapshot(
        OrderBookSnapshotEvent.of(Instrument.BTC_USD, 6, List.of(), List.of()));
    assertThat(service.getL2Snapshot(Instrument.BTC_USD, 10).bids()).isEmpty();
  }
}
