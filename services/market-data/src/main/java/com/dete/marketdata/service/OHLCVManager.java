package com.dete.marketdata.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.Interval;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aggregates trade executions into OHLCV candles across all supported intervals: 1m, 5m, 15m, 1h,
 * 1d.
 */
@Component
public class OHLCVManager {

  private final int maxHistory;
  private final Map<Instrument, Map<Interval, SeriesState>> seriesMap =
      new EnumMap<>(Instrument.class);

  public OHLCVManager(@Value("${marketdata.candles.max-history:1000}") int maxHistory) {
    this.maxHistory = maxHistory;
    for (Instrument instrument : Instrument.values()) {
      Map<Interval, SeriesState> intervalMap = new EnumMap<>(Interval.class);
      for (Interval interval : Interval.values()) {
        intervalMap.put(interval, new SeriesState(maxHistory));
      }
      seriesMap.put(instrument, intervalMap);
    }
  }

  /**
   * Processes a trade execution across all 5 intervals. Returns a map of interval to updated Candle
   * for WebSocket streaming.
   */
  public synchronized Map<Interval, Candle> onTrade(
      Instrument instrument, long price, long quantity, Instant timestamp) {
    Map<Interval, SeriesState> intervalMap = seriesMap.get(instrument);
    if (intervalMap == null) {
      return Collections.emptyMap();
    }

    Map<Interval, Candle> updatedCandles = new EnumMap<>(Interval.class);
    long tradeEpochMs = timestamp.toEpochMilli();

    for (Interval interval : Interval.values()) {
      SeriesState state = intervalMap.get(interval);
      if (state != null) {
        Candle updated = state.addTrade(price, quantity, tradeEpochMs, interval.getDurationMs());
        updatedCandles.put(interval, updated);
      }
    }

    return updatedCandles;
  }

  /**
   * Retrieves OHLCV candle history for an instrument and interval. Includes finalized historical
   * candles plus the current in-progress candle.
   */
  public synchronized List<Candle> getCandles(Instrument instrument, Interval interval, int limit) {
    Map<Interval, SeriesState> intervalMap = seriesMap.get(instrument);
    if (intervalMap == null) return Collections.emptyList();

    SeriesState state = intervalMap.get(interval);
    if (state == null) return Collections.emptyList();

    return state.getCandles(limit);
  }

  public synchronized void clear() {
    for (Map<Interval, SeriesState> intervalMap : seriesMap.values()) {
      for (SeriesState state : intervalMap.values()) {
        state.clear();
      }
    }
  }

  static class SeriesState {
    private final int maxHistory;
    private final Deque<Candle> history;
    private Candle currentCandle;

    SeriesState(int maxHistory) {
      this.maxHistory = maxHistory;
      this.history = new ArrayDeque<>(maxHistory);
    }

    synchronized Candle addTrade(long price, long quantity, long tradeEpochMs, long durationMs) {
      long windowStart = (tradeEpochMs / durationMs) * durationMs;
      long windowEnd = windowStart + durationMs - 1;

      if (currentCandle == null) {
        currentCandle = Candle.ofFirstTrade(windowStart, windowEnd, price, quantity);
      } else if (currentCandle.openTime() == windowStart) {
        currentCandle = currentCandle.withTrade(price, quantity);
      } else if (windowStart > currentCandle.openTime()) {
        if (history.size() >= maxHistory) {
          history.removeFirst();
        }
        history.addLast(currentCandle);
        currentCandle = Candle.ofFirstTrade(windowStart, windowEnd, price, quantity);
      } else {
        // Trade timestamp older than current candle - update current if within or ignore
        currentCandle = currentCandle.withTrade(price, quantity);
      }

      return currentCandle;
    }

    synchronized List<Candle> getCandles(int limit) {
      int effectiveLimit = limit <= 0 ? 100 : limit;
      List<Candle> all = new ArrayList<>(history.size() + 1);
      all.addAll(history);
      if (currentCandle != null) {
        all.add(currentCandle);
      }

      if (all.size() <= effectiveLimit) {
        return all;
      }
      return new ArrayList<>(all.subList(all.size() - effectiveLimit, all.size()));
    }

    synchronized void clear() {
      history.clear();
      currentCandle = null;
    }
  }
}
