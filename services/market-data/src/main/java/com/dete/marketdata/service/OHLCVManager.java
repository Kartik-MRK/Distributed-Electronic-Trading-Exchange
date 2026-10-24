package com.dete.marketdata.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.Interval;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Aggregates trade executions into OHLCV candles across all supported intervals: 1m, 5m, 15m, 1h,
 * 1d.
 */
@Component
public class OHLCVManager {

  private final Map<Instrument, Map<Interval, SeriesState>> seriesMap =
      new EnumMap<>(Instrument.class);

  public OHLCVManager(@Value("${marketdata.candles.max-history:1000}") int maxHistory) {
    if (maxHistory < 1) throw new IllegalArgumentException("Candle history must be positive");
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
        if (updated != null) updatedCandles.put(interval, updated);
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
    private final NavigableMap<Long, Window> windows = new TreeMap<>();

    SeriesState(int maxHistory) {
      this.maxHistory = maxHistory;
    }

    synchronized Candle addTrade(long price, long quantity, long tradeEpochMs, long durationMs) {
      long windowStart = (tradeEpochMs / durationMs) * durationMs;
      long windowEnd = windowStart + durationMs - 1;

      // Keep the current window plus maxHistory historical windows. Never put a late trade
      // into today's candle, and do not resurrect windows that have aged out of retention.
      if (windows.size() >= maxHistory + 1 && windowStart < windows.firstKey()) return null;
      Window window = windows.get(windowStart);
      if (window == null) {
        window =
            new Window(Candle.ofFirstTrade(windowStart, windowEnd, price, quantity), tradeEpochMs);
        windows.put(windowStart, window);
      } else {
        Candle old = window.candle;
        window.candle =
            new Candle(
                windowStart,
                windowEnd,
                tradeEpochMs < window.firstTrade ? price : old.open(),
                Math.max(old.high(), price),
                Math.min(old.low(), price),
                tradeEpochMs >= window.lastTrade ? price : old.close(),
                Math.addExact(old.volume(), quantity),
                Math.incrementExact(old.tradeCount()));
        window.firstTrade = Math.min(window.firstTrade, tradeEpochMs);
        window.lastTrade = Math.max(window.lastTrade, tradeEpochMs);
      }
      while (windows.size() > maxHistory + 1) windows.pollFirstEntry();
      return window.candle;
    }

    synchronized List<Candle> getCandles(int limit) {
      int effectiveLimit = limit <= 0 ? 100 : limit;
      List<Candle> all = windows.values().stream().map(w -> w.candle).toList();

      if (all.size() <= effectiveLimit) {
        return all;
      }
      return new ArrayList<>(all.subList(all.size() - effectiveLimit, all.size()));
    }

    synchronized void clear() {
      windows.clear();
    }

    private static class Window {
      Candle candle;
      long firstTrade;
      long lastTrade;

      Window(Candle candle, long timestamp) {
        this.candle = candle;
        this.firstTrade = timestamp;
        this.lastTrade = timestamp;
      }
    }
  }
}
