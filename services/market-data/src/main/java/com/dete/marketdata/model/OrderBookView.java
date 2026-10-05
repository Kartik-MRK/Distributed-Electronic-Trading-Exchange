package com.dete.marketdata.model;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.events.marketdata.OrderBookSnapshotEvent.SnapshotLevel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Thread-safe materialized view of the L2 Order Book for a single instrument. Maintains sorted bids
 * (descending) and asks (ascending), with fast order lookups for lifecycle updates.
 */
public class OrderBookView {

  private final Instrument instrument;
  private long sequenceNumber;
  private Instant lastUpdated;

  private final NavigableMap<Long, LevelData> bids = new TreeMap<>(Collections.reverseOrder());
  private final NavigableMap<Long, LevelData> asks = new TreeMap<>(Comparator.naturalOrder());
  private final Map<UUID, OrderEntry> orderIndex = new HashMap<>();

  public OrderBookView(Instrument instrument) {
    this.instrument = instrument;
    this.sequenceNumber = 0L;
    this.lastUpdated = Instant.now();
  }

  public synchronized Instrument getInstrument() {
    return instrument;
  }

  public synchronized long getSequenceNumber() {
    return sequenceNumber;
  }

  public synchronized Instant getLastUpdated() {
    return lastUpdated;
  }

  public synchronized void addOrder(
      UUID orderId, UUID accountId, OrderSide side, long price, long quantity) {
    if (orderId == null || price <= 0 || quantity <= 0) return;

    OrderEntry existing = orderIndex.get(orderId);
    if (existing != null) {
      return; // Already in book
    }

    OrderEntry entry = new OrderEntry(orderId, accountId, side, price, quantity);
    orderIndex.put(orderId, entry);

    NavigableMap<Long, LevelData> targetMap = (side == OrderSide.BUY) ? bids : asks;
    LevelData current = targetMap.get(price);
    if (current == null) {
      targetMap.put(price, new LevelData(price, quantity, 1));
    } else {
      targetMap.put(price, new LevelData(price, current.volume + quantity, current.orderCount + 1));
    }

    this.sequenceNumber++;
    this.lastUpdated = Instant.now();
  }

  public synchronized void applyFill(UUID orderId, long fillQty, long remainingQty) {
    OrderEntry entry = orderIndex.get(orderId);
    if (entry == null) return;

    NavigableMap<Long, LevelData> targetMap = (entry.side == OrderSide.BUY) ? bids : asks;
    LevelData current = targetMap.get(entry.price);

    if (current != null) {
      long newVolume = Math.max(0L, current.volume - fillQty);
      if (remainingQty <= 0) {
        orderIndex.remove(orderId);
        int newCount = Math.max(0, current.orderCount - 1);
        if (newVolume <= 0 || newCount <= 0) {
          targetMap.remove(entry.price);
        } else {
          targetMap.put(entry.price, new LevelData(entry.price, newVolume, newCount));
        }
      } else {
        entry.remainingQuantity = remainingQty;
        targetMap.put(entry.price, new LevelData(entry.price, newVolume, current.orderCount));
      }
    } else if (remainingQty <= 0) {
      orderIndex.remove(orderId);
    }

    this.sequenceNumber++;
    this.lastUpdated = Instant.now();
  }

  public synchronized void cancelOrder(UUID orderId, long remainingQty) {
    OrderEntry entry = orderIndex.remove(orderId);
    if (entry == null) return;

    NavigableMap<Long, LevelData> targetMap = (entry.side == OrderSide.BUY) ? bids : asks;
    LevelData current = targetMap.get(entry.price);

    if (current != null) {
      long qtyToDeduct = (remainingQty > 0) ? remainingQty : entry.remainingQuantity;
      long newVolume = Math.max(0L, current.volume - qtyToDeduct);
      int newCount = Math.max(0, current.orderCount - 1);
      if (newVolume <= 0 || newCount <= 0) {
        targetMap.remove(entry.price);
      } else {
        targetMap.put(entry.price, new LevelData(entry.price, newVolume, newCount));
      }
    }

    this.sequenceNumber++;
    this.lastUpdated = Instant.now();
  }

  public synchronized void resync(
      long newSequenceNumber, List<SnapshotLevel> newBids, List<SnapshotLevel> newAsks) {
    if (newSequenceNumber < this.sequenceNumber && this.sequenceNumber > 0) {
      return; // Skip stale snapshot
    }

    bids.clear();
    asks.clear();
    orderIndex.clear();

    if (newBids != null) {
      for (SnapshotLevel level : newBids) {
        if (level.volume() > 0 && level.orderCount() > 0) {
          bids.put(level.price(), new LevelData(level.price(), level.volume(), level.orderCount()));
        }
      }
    }

    if (newAsks != null) {
      for (SnapshotLevel level : newAsks) {
        if (level.volume() > 0 && level.orderCount() > 0) {
          asks.put(level.price(), new LevelData(level.price(), level.volume(), level.orderCount()));
        }
      }
    }

    this.sequenceNumber = newSequenceNumber;
    this.lastUpdated = Instant.now();
  }

  public synchronized OrderBookSnapshotResponse getL2Snapshot(int depth) {
    int requestedDepth = depth <= 0 ? 20 : depth;

    List<PriceLevelView> bidList = new ArrayList<>(Math.min(requestedDepth, bids.size()));
    int bidCount = 0;
    for (Map.Entry<Long, LevelData> entry : bids.entrySet()) {
      if (bidCount++ >= requestedDepth) break;
      bidList.add(
          new PriceLevelView(entry.getKey(), entry.getValue().volume, entry.getValue().orderCount));
    }

    List<PriceLevelView> askList = new ArrayList<>(Math.min(requestedDepth, asks.size()));
    int askCount = 0;
    for (Map.Entry<Long, LevelData> entry : asks.entrySet()) {
      if (askCount++ >= requestedDepth) break;
      askList.add(
          new PriceLevelView(entry.getKey(), entry.getValue().volume, entry.getValue().orderCount));
    }

    return new OrderBookSnapshotResponse(instrument, sequenceNumber, lastUpdated, bidList, askList);
  }

  public synchronized UUID getAccountIdForOrder(UUID orderId) {
    OrderEntry entry = orderIndex.get(orderId);
    return entry != null ? entry.accountId : null;
  }

  public synchronized void clear() {
    bids.clear();
    asks.clear();
    orderIndex.clear();
    sequenceNumber = 0L;
    lastUpdated = Instant.now();
  }

  private static class LevelData {
    final long price;
    final long volume;
    final int orderCount;

    LevelData(long price, long volume, int orderCount) {
      this.price = price;
      this.volume = volume;
      this.orderCount = orderCount;
    }
  }

  private static class OrderEntry {
    final UUID orderId;
    final UUID accountId;
    final OrderSide side;
    final long price;
    long remainingQuantity;

    OrderEntry(UUID orderId, UUID accountId, OrderSide side, long price, long remainingQuantity) {
      this.orderId = orderId;
      this.accountId = accountId;
      this.side = side;
      this.price = price;
      this.remainingQuantity = remainingQuantity;
    }
  }
}
