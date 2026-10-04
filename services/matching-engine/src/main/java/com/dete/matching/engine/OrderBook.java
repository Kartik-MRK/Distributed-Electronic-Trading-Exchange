package com.dete.matching.engine;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.events.order.OrderAcceptedEvent;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.events.order.OrderRejectedEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.matching.engine.model.BookOrder;
import com.dete.matching.engine.model.L2Depth;
import com.dete.matching.engine.model.MatchResult;
import com.dete.matching.engine.model.PriceLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

/**
 * High-performance, deterministic, in-memory limit order book for a single financial instrument.
 * Owned by a single thread in the single-writer architecture, eliminating locking overhead.
 */
public final class OrderBook {

  private final Instrument instrument;
  private final NavigableMap<Long, PriceLevel> bids;
  private final NavigableMap<Long, PriceLevel> asks;
  private final Map<UUID, BookOrder> orderIndex;

  private long sequenceNumber;

  public OrderBook(Instrument instrument) {
    this.instrument = Objects.requireNonNull(instrument, "instrument cannot be null");
    this.bids = new TreeMap<>(Comparator.reverseOrder()); // Descending: highest bid first
    this.asks = new TreeMap<>(Comparator.naturalOrder()); // Ascending: lowest ask first
    this.orderIndex = new HashMap<>();
    this.sequenceNumber = 0;
  }

  public Instrument instrument() {
    return instrument;
  }

  public long currentSequenceNumber() {
    return sequenceNumber;
  }

  public void setSequenceNumber(long sequenceNumber) {
    this.sequenceNumber = sequenceNumber;
  }

  public int orderCount() {
    return orderIndex.size();
  }

  public BookOrder getOrder(UUID orderId) {
    return orderIndex.get(orderId);
  }

  public long bestBidPrice() {
    return bids.isEmpty() ? 0 : bids.firstKey();
  }

  public long bestAskPrice() {
    return asks.isEmpty() ? 0 : asks.firstKey();
  }

  public long totalBidVolume() {
    long vol = 0;
    for (PriceLevel level : bids.values()) {
      vol += level.totalVolume();
    }
    return vol;
  }

  public long totalAskVolume() {
    long vol = 0;
    for (PriceLevel level : asks.values()) {
      vol += level.totalVolume();
    }
    return vol;
  }

  public void clear() {
    bids.clear();
    asks.clear();
    orderIndex.clear();
    sequenceNumber = 0;
  }

  /** Main entry point for processing incoming order commands. */
  public MatchResult processOrder(OrderPlacedEvent event) {
    MatchResult result = new MatchResult();

    if (event.quantity() <= 0) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(event.orderId(), "Quantity must be strictly positive"));
      return result;
    }

    if (orderIndex.containsKey(event.orderId())) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(event.orderId(), "Duplicate orderId: " + event.orderId()));
      return result;
    }

    long seq = ++sequenceNumber;
    BookOrder order = BookOrder.fromEvent(event, seq);

    switch (order.orderType()) {
      case LIMIT -> matchLimit(order, result);
      case MARKET -> matchMarket(order, result);
      case IOC -> matchIoc(order, result);
      case FOK -> matchFok(order, result);
      default ->
          result.setRejectedEvent(
              OrderRejectedEvent.of(
                  order.orderId(), "Unsupported order type: " + order.orderType()));
    }

    return result;
  }

  /** Cancels a resting order in O(1) time. */
  public MatchResult cancelOrder(UUID orderId, UUID accountId) {
    MatchResult result = new MatchResult();
    BookOrder order = orderIndex.get(orderId);

    if (order == null) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(orderId, "Order not found or already filled/cancelled"));
      return result;
    }

    if (!order.accountId().equals(accountId)) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(orderId, "Account mismatch for order cancellation"));
      return result;
    }

    removeOrderFromBook(order);
    order.cancel();

    ++sequenceNumber;
    result.setCancelledEvent(OrderCancelledEvent.of(orderId, order.remainingQuantity()));
    return result;
  }

  /**
   * Modifies an existing order via cancel-and-reinsert with a fresh sequence number. Preserves
   * price-time priority invariant: modified order goes to the back of the queue.
   */
  public MatchResult modifyOrder(OrderModifyCommand command) {
    MatchResult result = new MatchResult();
    BookOrder existing = orderIndex.get(command.orderId());

    if (existing == null) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(
              command.orderId(), "Order not found for modify: " + command.orderId()));
      return result;
    }

    if (!existing.accountId().equals(command.accountId())) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(command.orderId(), "Account mismatch for order modify"));
      return result;
    }

    if (command.newQuantity() <= 0) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(command.orderId(), "New quantity must be strictly positive"));
      return result;
    }

    // Cancel existing order
    removeOrderFromBook(existing);
    existing.cancel();
    result.setCancelledEvent(
        OrderCancelledEvent.of(existing.orderId(), existing.remainingQuantity()));

    // Reinsert as a new order with fresh sequence number
    OrderPlacedEvent newEvent =
        new OrderPlacedEvent(
            UUID.randomUUID(),
            existing.orderId(),
            existing.accountId(),
            existing.instrument(),
            existing.side(),
            existing.orderType(),
            command.newPrice(),
            command.newQuantity(),
            UUID.randomUUID(),
            Instant.now(),
            1);

    long seq = ++sequenceNumber;
    BookOrder modifiedOrder = BookOrder.fromEvent(newEvent, seq);

    matchLimit(modifiedOrder, result);
    return result;
  }

  // =========================================================================
  // MATCHING ALGORITHMS
  // =========================================================================

  private void matchLimit(BookOrder incoming, MatchResult result) {
    if (incoming.price() <= 0) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(incoming.orderId(), "Limit order price must be strictly positive"));
      return;
    }

    result.setAcceptedEvent(OrderAcceptedEvent.of(incoming.orderId(), incoming.sequenceNumber()));

    executeMatching(incoming, result, false, incoming.price());

    if (!incoming.isFilled()) {
      restOrder(incoming);
    }
  }

  private void matchMarket(BookOrder incoming, MatchResult result) {
    NavigableMap<Long, PriceLevel> opposingBook = (incoming.side() == OrderSide.BUY) ? asks : bids;

    if (opposingBook.isEmpty()) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(incoming.orderId(), "No liquidity available for market order"));
      return;
    }

    result.setAcceptedEvent(OrderAcceptedEvent.of(incoming.orderId(), incoming.sequenceNumber()));

    executeMatching(incoming, result, true, 0);

    if (!incoming.isFilled()) {
      // Market remainder is immediately cancelled (never rests)
      result.setCancelledEvent(
          OrderCancelledEvent.of(incoming.orderId(), incoming.remainingQuantity()));
    }
  }

  private void matchIoc(BookOrder incoming, MatchResult result) {
    if (incoming.price() <= 0) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(incoming.orderId(), "IOC order price must be strictly positive"));
      return;
    }

    result.setAcceptedEvent(OrderAcceptedEvent.of(incoming.orderId(), incoming.sequenceNumber()));

    executeMatching(incoming, result, false, incoming.price());

    if (!incoming.isFilled()) {
      // IOC remainder is cancelled immediately
      result.setCancelledEvent(
          OrderCancelledEvent.of(incoming.orderId(), incoming.remainingQuantity()));
    }
  }

  private void matchFok(BookOrder incoming, MatchResult result) {
    if (incoming.price() <= 0) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(incoming.orderId(), "FOK order price must be strictly positive"));
      return;
    }

    long availableVolume =
        calculateAvailableVolume(incoming.side(), incoming.price(), incoming.accountId());
    if (availableVolume < incoming.originalQuantity()) {
      result.setRejectedEvent(
          OrderRejectedEvent.of(incoming.orderId(), "Insufficient liquidity to fill FOK order"));
      return;
    }

    result.setAcceptedEvent(OrderAcceptedEvent.of(incoming.orderId(), incoming.sequenceNumber()));

    executeMatching(incoming, result, false, incoming.price());
  }

  private void executeMatching(
      BookOrder incoming, MatchResult result, boolean isMarket, long limitPrice) {
    NavigableMap<Long, PriceLevel> opposingBook = (incoming.side() == OrderSide.BUY) ? asks : bids;

    Iterator<Map.Entry<Long, PriceLevel>> levelIterator = opposingBook.entrySet().iterator();

    while (levelIterator.hasNext() && !incoming.isFilled()) {
      Map.Entry<Long, PriceLevel> levelEntry = levelIterator.next();
      long levelPrice = levelEntry.getKey();
      PriceLevel level = levelEntry.getValue();

      // Check crossing price limit condition
      if (!isMarket) {
        if (incoming.side() == OrderSide.BUY && levelPrice > limitPrice) {
          break; // Lowest ask exceeds limit buy price
        }
        if (incoming.side() == OrderSide.SELL && levelPrice < limitPrice) {
          break; // Highest bid is below limit sell price
        }
      }

      // Match within the price level FIFO queue
      Iterator<BookOrder> orderIterator = level.orders().iterator();
      List<BookOrder> filledMakers = new ArrayList<>();

      while (orderIterator.hasNext() && !incoming.isFilled()) {
        BookOrder restingMaker = orderIterator.next();

        // Self-Trade Prevention: skip resting orders owned by the same account
        if (restingMaker.accountId().equals(incoming.accountId())) {
          continue;
        }

        long matchPrice = restingMaker.price(); // Execution at maker's resting price
        long fillQty = Math.min(incoming.remainingQuantity(), restingMaker.remainingQuantity());

        // Perform fill on both orders
        incoming.fill(fillQty);
        restingMaker.fill(fillQty);
        level.reduceVolume(fillQty);

        UUID tradeId = UUID.randomUUID();
        long tradeSeq = ++sequenceNumber;

        UUID buyOrderId =
            (incoming.side() == OrderSide.BUY) ? incoming.orderId() : restingMaker.orderId();
        UUID sellOrderId =
            (incoming.side() == OrderSide.SELL) ? incoming.orderId() : restingMaker.orderId();
        UUID buyAccountId =
            (incoming.side() == OrderSide.BUY) ? incoming.accountId() : restingMaker.accountId();
        UUID sellAccountId =
            (incoming.side() == OrderSide.SELL) ? incoming.accountId() : restingMaker.accountId();

        TradeExecutedEvent trade =
            new TradeExecutedEvent(
                UUID.randomUUID(),
                tradeId,
                instrument,
                buyOrderId,
                sellOrderId,
                buyAccountId,
                sellAccountId,
                matchPrice,
                fillQty,
                tradeSeq,
                Instant.now(),
                1);
        result.addTrade(trade);

        // Record fill events for resting maker
        if (restingMaker.isFilled()) {
          filledMakers.add(restingMaker);
          orderIterator.remove();
          orderIndex.remove(restingMaker.orderId());
          result.addFilledEvent(
              new OrderFilledEvent(
                  UUID.randomUUID(),
                  restingMaker.orderId(),
                  tradeId,
                  fillQty,
                  matchPrice,
                  Instant.now(),
                  1));
        } else {
          result.addPartiallyFilledEvent(
              new OrderPartiallyFilledEvent(
                  UUID.randomUUID(),
                  restingMaker.orderId(),
                  tradeId,
                  fillQty,
                  matchPrice,
                  restingMaker.remainingQuantity(),
                  Instant.now(),
                  1));
        }

        // Record fill events for incoming taker
        if (incoming.isFilled()) {
          result.addFilledEvent(
              new OrderFilledEvent(
                  UUID.randomUUID(),
                  incoming.orderId(),
                  tradeId,
                  fillQty,
                  matchPrice,
                  Instant.now(),
                  1));
        } else {
          result.addPartiallyFilledEvent(
              new OrderPartiallyFilledEvent(
                  UUID.randomUUID(),
                  incoming.orderId(),
                  tradeId,
                  fillQty,
                  matchPrice,
                  incoming.remainingQuantity(),
                  Instant.now(),
                  1));
        }
      }

      // If price level became empty, remove it from the book
      if (level.isEmpty()) {
        levelIterator.remove();
      }
    }
  }

  private void restOrder(BookOrder order) {
    NavigableMap<Long, PriceLevel> targetBook = (order.side() == OrderSide.BUY) ? bids : asks;
    PriceLevel level = targetBook.computeIfAbsent(order.price(), PriceLevel::new);
    level.addOrder(order);
    orderIndex.put(order.orderId(), order);
  }

  private void removeOrderFromBook(BookOrder order) {
    NavigableMap<Long, PriceLevel> targetBook = (order.side() == OrderSide.BUY) ? bids : asks;
    PriceLevel level = targetBook.get(order.price());
    if (level != null) {
      level.removeOrder(order);
      if (level.isEmpty()) {
        targetBook.remove(order.price());
      }
    }
    orderIndex.remove(order.orderId());
  }

  private long calculateAvailableVolume(OrderSide side, long limitPrice, UUID accountId) {
    NavigableMap<Long, PriceLevel> opposingBook = (side == OrderSide.BUY) ? asks : bids;
    long volume = 0;

    for (Map.Entry<Long, PriceLevel> entry : opposingBook.entrySet()) {
      long levelPrice = entry.getKey();
      if (side == OrderSide.BUY && levelPrice > limitPrice) {
        break;
      }
      if (side == OrderSide.SELL && levelPrice < limitPrice) {
        break;
      }

      for (BookOrder order : entry.getValue().orders()) {
        if (!order.accountId().equals(accountId)) {
          volume += order.remainingQuantity();
        }
      }
    }
    return volume;
  }

  /** Generates a Level 2 market data depth snapshot. */
  public L2Depth getL2Depth(int depth) {
    List<L2Depth.L2Level> bidLevels = new ArrayList<>(depth);
    int count = 0;
    for (Map.Entry<Long, PriceLevel> entry : bids.entrySet()) {
      if (count++ >= depth) break;
      bidLevels.add(
          new L2Depth.L2Level(
              entry.getKey(), entry.getValue().totalVolume(), entry.getValue().orderCount()));
    }

    List<L2Depth.L2Level> askLevels = new ArrayList<>(depth);
    count = 0;
    for (Map.Entry<Long, PriceLevel> entry : asks.entrySet()) {
      if (count++ >= depth) break;
      askLevels.add(
          new L2Depth.L2Level(
              entry.getKey(), entry.getValue().totalVolume(), entry.getValue().orderCount()));
    }

    return new L2Depth(instrument, sequenceNumber, bidLevels, askLevels);
  }
}
