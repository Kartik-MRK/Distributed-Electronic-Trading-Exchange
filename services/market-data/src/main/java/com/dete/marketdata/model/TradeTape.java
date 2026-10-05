package com.dete.marketdata.model;

import com.dete.common.events.trade.TradeExecutedEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Thread-safe bounded ring buffer holding the most recent trades for an instrument. Maximum
 * capacity is configurable (default 500).
 */
public class TradeTape {

  private final int capacity;
  private final Deque<TradeExecutedEvent> buffer;

  public TradeTape(int capacity) {
    this.capacity = capacity;
    this.buffer = new ArrayDeque<>(capacity);
  }

  public synchronized void addTrade(TradeExecutedEvent trade) {
    if (trade == null) return;
    if (buffer.size() >= capacity) {
      buffer.removeLast();
    }
    buffer.addFirst(trade);
  }

  public synchronized List<TradeExecutedEvent> getRecentTrades(int limit) {
    int count = Math.min(limit, buffer.size());
    List<TradeExecutedEvent> result = new ArrayList<>(count);
    int i = 0;
    for (TradeExecutedEvent trade : buffer) {
      if (i++ >= count) break;
      result.add(trade);
    }
    return result;
  }

  public synchronized int size() {
    return buffer.size();
  }

  public synchronized void clear() {
    buffer.clear();
  }
}
