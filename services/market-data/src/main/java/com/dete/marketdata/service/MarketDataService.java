package com.dete.marketdata.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.events.marketdata.OrderBookSnapshotEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.Interval;
import com.dete.marketdata.model.OrderBookSnapshotResponse;
import com.dete.marketdata.model.TradeTape;
import com.dete.marketdata.repository.MarketDataTradeRepository;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Core Market Data Service managing read-side CQRS in-memory state: - OrderBookView per instrument
 * - TradeTape (ring buffer of last 500 trades) per instrument - Multi-interval OHLCV candles (1m,
 * 5m, 15m, 1h, 1d) - Last traded price - Historical trade persistence for Phase 12 Replay Viewer -
 * Real-time WebSocket streaming
 */
@Service
public class MarketDataService {

  private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

  private final Map<Instrument, com.dete.marketdata.model.OrderBookView> orderBooks =
      new EnumMap<>(Instrument.class);
  private final Map<Instrument, TradeTape> tradeTapes = new EnumMap<>(Instrument.class);
  private final Map<Instrument, AtomicLong> lastTradedPrices = new EnumMap<>(Instrument.class);
  private final Map<Instrument, java.time.Instant> lastTradeTimes = new EnumMap<>(Instrument.class);
  private final int tapeCapacity;

  private final Map<UUID, UUID> orderToAccountMap = new ConcurrentHashMap<>();
  private final Map<UUID, Instrument> orderToInstrumentMap = new ConcurrentHashMap<>();

  private final OHLCVManager ohlcvManager;
  private final MarketDataTradeRepository tradeRepository;
  private final MarketDataWebSocketBroadcaster broadcaster;

  public MarketDataService(
      @Value("${marketdata.tape.capacity:500}") int tapeCapacity,
      OHLCVManager ohlcvManager,
      MarketDataTradeRepository tradeRepository,
      MarketDataWebSocketBroadcaster broadcaster) {
    this.ohlcvManager = ohlcvManager;
    this.tradeRepository = tradeRepository;
    this.broadcaster = broadcaster;
    this.tapeCapacity = tapeCapacity;

    for (Instrument instrument : Instrument.values()) {
      orderBooks.put(instrument, new com.dete.marketdata.model.OrderBookView(instrument));
      tradeTapes.put(instrument, new TradeTape(tapeCapacity));
      lastTradedPrices.put(instrument, new AtomicLong(0L));
    }
    log.info("Initialized MarketDataService for {} instruments", Instrument.values().length);
  }

  /** Rebuild the bounded live projection from durable trades after a process restart. */
  @jakarta.annotation.PostConstruct
  public synchronized void restoreRecentTrades() {
    for (Instrument instrument : Instrument.values()) {
      var records =
          new java.util.ArrayList<>(tradeRepository.findByInstrument(instrument, tapeCapacity));
      records.sort(
          java.util.Comparator.comparing(
                  com.dete.marketdata.model.MarketDataTradeRecord::executedAt)
              .thenComparingLong(com.dete.marketdata.model.MarketDataTradeRecord::sequenceNumber));
      for (var record : records) {
        var trade =
            new TradeExecutedEvent(
                record.tradeId(),
                record.tradeId(),
                instrument,
                record.buyOrderId(),
                record.sellOrderId(),
                record.buyAccountId(),
                record.sellAccountId(),
                record.price(),
                record.quantity(),
                record.sequenceNumber(),
                record.executedAt(),
                1);
        tradeTapes.get(instrument).addTrade(trade);
        ohlcvManager.onTrade(instrument, trade.price(), trade.quantity(), trade.timestamp());
        lastTradedPrices.get(instrument).set(trade.price());
        lastTradeTimes.put(instrument, trade.timestamp());
      }
    }
  }

  public com.dete.marketdata.model.OrderBookView getOrderBook(Instrument instrument) {
    return orderBooks.get(instrument);
  }

  public OrderBookSnapshotResponse getL2Snapshot(Instrument instrument, int depth) {
    com.dete.marketdata.model.OrderBookView view = orderBooks.get(instrument);
    if (view == null) return null;
    return view.getL2Snapshot(depth);
  }

  public List<TradeExecutedEvent> getRecentTrades(Instrument instrument, int limit) {
    TradeTape tape = tradeTapes.get(instrument);
    if (tape == null) return Collections.emptyList();
    return tape.getRecentTrades(limit);
  }

  public List<com.dete.marketdata.model.MarketDataTradeRecord> getReplayTrades(
      Instrument instrument, java.time.Instant from, java.time.Instant to, int limit) {
    return tradeRepository.findByInstrumentAndWindow(instrument, from, to, limit);
  }

  public List<Candle> getCandles(Instrument instrument, Interval interval, int limit) {
    return ohlcvManager.getCandles(instrument, interval, limit);
  }

  public long getLastTradedPrice(Instrument instrument) {
    AtomicLong ltp = lastTradedPrices.get(instrument);
    return ltp != null ? ltp.get() : 0L;
  }

  public void registerOrder(
      UUID orderId,
      UUID accountId,
      Instrument instrument,
      OrderSide side,
      long price,
      long quantity) {
    if (orderId == null) return;
    if (accountId != null) {
      orderToAccountMap.put(orderId, accountId);
    }
    if (instrument != null) {
      orderToInstrumentMap.put(orderId, instrument);
      // Commands are intent, not confirmed resting liquidity. Only engine snapshots update depth.
    }

    if (accountId != null) {
      Map<String, Object> update =
          Map.of(
              "orderId",
              orderId.toString(),
              "accountId",
              accountId.toString(),
              "instrument",
              instrument != null ? instrument.name() : "",
              "status",
              "PLACED",
              "price",
              price,
              "quantity",
              quantity);
      broadcaster.broadcastUserOrderUpdate(accountId, update);
    }
  }

  public void handleOrderAccepted(UUID orderId, long sequenceNumber) {
    UUID accountId = orderToAccountMap.get(orderId);
    if (accountId != null) {
      Map<String, Object> update =
          Map.of(
              "orderId",
              orderId.toString(),
              "accountId",
              accountId.toString(),
              "sequenceNumber",
              sequenceNumber,
              "status",
              "ACCEPTED");
      broadcaster.broadcastUserOrderUpdate(accountId, update);
    }
  }

  public void handleOrderFilled(UUID orderId, long filledQty) {
    orderToInstrumentMap.remove(orderId);

    UUID accountId = orderToAccountMap.remove(orderId);
    if (accountId != null) {
      Map<String, Object> update =
          Map.of(
              "orderId",
              orderId.toString(),
              "accountId",
              accountId.toString(),
              "filledQuantity",
              filledQty,
              "status",
              "FILLED");
      broadcaster.broadcastUserOrderUpdate(accountId, update);
    }
  }

  public void handleOrderPartiallyFilled(UUID orderId, long filledQty, long remainingQty) {
    UUID accountId = orderToAccountMap.get(orderId);
    if (accountId != null) {
      Map<String, Object> update =
          Map.of(
              "orderId",
              orderId.toString(),
              "accountId",
              accountId.toString(),
              "filledQuantity",
              filledQty,
              "remainingQuantity",
              remainingQty,
              "status",
              "PARTIALLY_FILLED");
      broadcaster.broadcastUserOrderUpdate(accountId, update);
    }
  }

  public void handleOrderCancelled(UUID orderId, long remainingQty) {
    orderToInstrumentMap.remove(orderId);

    UUID accountId = orderToAccountMap.remove(orderId);
    if (accountId != null) {
      Map<String, Object> update =
          Map.of(
              "orderId", orderId.toString(),
              "accountId", accountId.toString(),
              "status", "CANCELLED");
      broadcaster.broadcastUserOrderUpdate(accountId, update);
    }
  }

  public void handleOrderRejected(UUID orderId, String reason) {
    UUID accountId = orderToAccountMap.remove(orderId);
    orderToInstrumentMap.remove(orderId);

    if (accountId != null) {
      Map<String, Object> update =
          Map.of(
              "orderId",
              orderId.toString(),
              "accountId",
              accountId.toString(),
              "status",
              "REJECTED",
              "reason",
              reason != null ? reason : "");
      broadcaster.broadcastUserOrderUpdate(accountId, update);
    }
  }

  public synchronized void handleTradeExecuted(TradeExecutedEvent trade) {
    if (trade == null) return;
    // Persist before touching projections. Database failures must reach Kafka retry handling;
    // a redelivered execution must not add volume or appear in the tape twice.
    if (!tradeRepository.saveTrade(trade)) return;
    Instrument instrument = trade.instrument();

    // 1. Update Last Traded Price
    AtomicLong ltp = lastTradedPrices.get(instrument);
    if (ltp != null
        && !trade
            .timestamp()
            .isBefore(lastTradeTimes.getOrDefault(instrument, java.time.Instant.MIN))) {
      ltp.set(trade.price());
      lastTradeTimes.put(instrument, trade.timestamp());
    }

    // 2. Append to in-memory Trade Tape
    TradeTape tape = tradeTapes.get(instrument);
    if (tape != null) {
      tape.addTrade(trade);
    }

    // 3. Update OHLCV Candles for all 5 intervals
    Map<Interval, Candle> updatedCandles =
        ohlcvManager.onTrade(instrument, trade.price(), trade.quantity(), trade.timestamp());

    // 5. Cache account bindings if present
    if (trade.buyOrderId() != null && trade.buyAccountId() != null) {
      orderToAccountMap.put(trade.buyOrderId(), trade.buyAccountId());
      orderToInstrumentMap.put(trade.buyOrderId(), instrument);
    }
    if (trade.sellOrderId() != null && trade.sellAccountId() != null) {
      orderToAccountMap.put(trade.sellOrderId(), trade.sellAccountId());
      orderToInstrumentMap.put(trade.sellOrderId(), instrument);
    }

    // 6. Broadcast over WebSocket
    broadcaster.broadcastTrade(instrument, trade);
    for (Map.Entry<Interval, Candle> entry : updatedCandles.entrySet()) {
      broadcaster.broadcastCandle(instrument, entry.getKey(), entry.getValue());
    }

    // 7. Broadcast private trade execution to buyer and seller
    if (trade.buyAccountId() != null) {
      broadcaster.broadcastUserOrderUpdate(
          trade.buyAccountId(),
          Map.of(
              "orderId", trade.buyOrderId().toString(),
              "tradeId", trade.tradeId().toString(),
              "price", trade.price(),
              "quantity", trade.quantity(),
              "side", "BUY",
              "status", "EXECUTED"));
    }
    if (trade.sellAccountId() != null) {
      broadcaster.broadcastUserOrderUpdate(
          trade.sellAccountId(),
          Map.of(
              "orderId", trade.sellOrderId().toString(),
              "tradeId", trade.tradeId().toString(),
              "price", trade.price(),
              "quantity", trade.quantity(),
              "side", "SELL",
              "status", "EXECUTED"));
    }
  }

  public void handleOrderBookSnapshot(OrderBookSnapshotEvent snapshot) {
    if (snapshot == null) return;
    Instrument instrument = snapshot.instrument();
    com.dete.marketdata.model.OrderBookView view = orderBooks.get(instrument);
    if (view != null) {
      view.resync(snapshot.sequenceNumber(), snapshot.bids(), snapshot.asks());
      broadcaster.broadcastOrderBook(instrument, view.getL2Snapshot(20));
      log.trace(
          "Resynchronized order book for {} at seq {}", instrument, snapshot.sequenceNumber());
    }
  }

  public synchronized void resetAll() {
    for (com.dete.marketdata.model.OrderBookView view : orderBooks.values()) {
      view.clear();
    }
    for (TradeTape tape : tradeTapes.values()) {
      tape.clear();
    }
    for (AtomicLong ltp : lastTradedPrices.values()) {
      ltp.set(0L);
    }
    ohlcvManager.clear();
    lastTradeTimes.clear();
    orderToAccountMap.clear();
    orderToInstrumentMap.clear();
  }
}
