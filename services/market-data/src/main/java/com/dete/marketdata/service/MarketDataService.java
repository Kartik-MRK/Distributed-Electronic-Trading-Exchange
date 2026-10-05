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

    for (Instrument instrument : Instrument.values()) {
      orderBooks.put(instrument, new com.dete.marketdata.model.OrderBookView(instrument));
      tradeTapes.put(instrument, new TradeTape(tapeCapacity));
      lastTradedPrices.put(instrument, new AtomicLong(0L));
    }
    log.info("Initialized MarketDataService for {} instruments", Instrument.values().length);
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
      com.dete.marketdata.model.OrderBookView view = orderBooks.get(instrument);
      if (view != null && price > 0 && quantity > 0) {
        view.addOrder(orderId, accountId, side, price, quantity);
        broadcaster.broadcastOrderBook(instrument, view.getL2Snapshot(20));
      }
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
    Instrument instrument = orderToInstrumentMap.get(orderId);
    if (instrument != null) {
      com.dete.marketdata.model.OrderBookView view = orderBooks.get(instrument);
      if (view != null) {
        view.applyFill(orderId, filledQty, 0L);
        broadcaster.broadcastOrderBook(instrument, view.getL2Snapshot(20));
      }
    }

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
    Instrument instrument = orderToInstrumentMap.get(orderId);
    if (instrument != null) {
      com.dete.marketdata.model.OrderBookView view = orderBooks.get(instrument);
      if (view != null) {
        view.applyFill(orderId, filledQty, remainingQty);
        broadcaster.broadcastOrderBook(instrument, view.getL2Snapshot(20));
      }
    }

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
    Instrument instrument = orderToInstrumentMap.remove(orderId);
    if (instrument != null) {
      com.dete.marketdata.model.OrderBookView view = orderBooks.get(instrument);
      if (view != null) {
        view.cancelOrder(orderId, remainingQty);
        broadcaster.broadcastOrderBook(instrument, view.getL2Snapshot(20));
      }
    }

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

  public void handleTradeExecuted(TradeExecutedEvent trade) {
    if (trade == null) return;
    Instrument instrument = trade.instrument();

    // 1. Update Last Traded Price
    AtomicLong ltp = lastTradedPrices.get(instrument);
    if (ltp != null) {
      ltp.set(trade.price());
    }

    // 2. Append to in-memory Trade Tape
    TradeTape tape = tradeTapes.get(instrument);
    if (tape != null) {
      tape.addTrade(trade);
    }

    // 3. Update OHLCV Candles for all 5 intervals
    Map<Interval, Candle> updatedCandles =
        ohlcvManager.onTrade(instrument, trade.price(), trade.quantity(), trade.timestamp());

    // 4. Persist to historical database (read model for Phase 12 Replay)
    try {
      tradeRepository.saveTrade(trade);
    } catch (Exception e) {
      log.error("Failed to save historical trade record for {}", trade.tradeId(), e);
    }

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

  public void resetAll() {
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
    orderToAccountMap.clear();
    orderToInstrumentMap.clear();
  }
}
