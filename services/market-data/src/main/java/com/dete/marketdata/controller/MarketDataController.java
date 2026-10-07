package com.dete.marketdata.controller;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.model.Candle;
import com.dete.marketdata.model.Interval;
import com.dete.marketdata.model.OrderBookSnapshotResponse;
import com.dete.marketdata.service.MarketDataService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller serving read-side market data: - Order book snapshots - Recent trade tape -
 * Multi-interval OHLCV candles - Last traded prices - Supported instruments
 */
@RestController
@RequestMapping("/market-data")
public class MarketDataController {

  private final MarketDataService marketDataService;

  public MarketDataController(MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  @GetMapping(value = {"/{instrument}/orderbook", "/depth/{instrument}", "/{instrument}/depth"})
  public ResponseEntity<OrderBookSnapshotResponse> getOrderBook(
      @PathVariable String instrument, @RequestParam(defaultValue = "20") int depth) {
    Instrument inst = resolveInstrument(instrument);
    OrderBookSnapshotResponse snapshot = marketDataService.getL2Snapshot(inst, depth);
    return ResponseEntity.ok(snapshot);
  }

  @GetMapping("/{instrument}/trades")
  public ResponseEntity<List<TradeExecutedEvent>> getRecentTrades(
      @PathVariable String instrument, @RequestParam(defaultValue = "100") int limit) {
    Instrument inst = resolveInstrument(instrument);
    List<TradeExecutedEvent> trades = marketDataService.getRecentTrades(inst, limit);
    return ResponseEntity.ok(trades);
  }

  @GetMapping("/{instrument}/replay")
  public ResponseEntity<List<com.dete.marketdata.model.MarketDataTradeRecord>> getReplay(
      @PathVariable String instrument,
      @RequestParam(required = false) Long from,
      @RequestParam(required = false) Long to,
      @RequestParam(defaultValue = "500") int limit) {
    Instrument inst = resolveInstrument(instrument);
    java.time.Instant toInstant = to != null ? java.time.Instant.ofEpochMilli(to) : java.time.Instant.now();
    java.time.Instant fromInstant =
        from != null ? java.time.Instant.ofEpochMilli(from) : toInstant.minusSeconds(3600);
    List<com.dete.marketdata.model.MarketDataTradeRecord> trades =
        marketDataService.getReplayTrades(inst, fromInstant, toInstant, limit);
    return ResponseEntity.ok(trades);
  }

  @GetMapping("/{instrument}/candles")
  public ResponseEntity<List<Candle>> getCandles(
      @PathVariable String instrument,
      @RequestParam(defaultValue = "1m") String interval,
      @RequestParam(defaultValue = "100") int limit) {
    Instrument inst = resolveInstrument(instrument);
    Interval parsedInterval = Interval.fromCode(interval);
    List<Candle> candles = marketDataService.getCandles(inst, parsedInterval, limit);
    return ResponseEntity.ok(candles);
  }

  @GetMapping("/instruments")
  public ResponseEntity<List<Map<String, Object>>> getInstruments() {
    List<Map<String, Object>> result = new ArrayList<>();
    for (Instrument inst : Instrument.values()) {
      long ltp = marketDataService.getLastTradedPrice(inst);
      result.add(
          Map.of(
              "instrument", inst.name(),
              "symbol", inst.symbol(),
              "baseAsset", inst.baseAsset(),
              "quoteAsset", inst.quoteAsset(),
              "lastTradedPrice", ltp,
              "formattedPrice", FixedPoint.ofScaled(ltp).toString()));
    }
    return ResponseEntity.ok(result);
  }

  @GetMapping("/{instrument}/price")
  public ResponseEntity<Map<String, Object>> getLastTradedPrice(@PathVariable String instrument) {
    Instrument inst = resolveInstrument(instrument);
    long ltp = marketDataService.getLastTradedPrice(inst);
    return ResponseEntity.ok(
        Map.of(
            "instrument", inst.name(),
            "symbol", inst.symbol(),
            "lastTradedPrice", ltp,
            "formattedPrice", FixedPoint.ofScaled(ltp).toString()));
  }

  private Instrument resolveInstrument(String raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Instrument symbol is required");
    }
    String normalized = raw.trim().toUpperCase();
    for (Instrument i : Instrument.values()) {
      if (i.symbol().equalsIgnoreCase(normalized) || i.name().equalsIgnoreCase(normalized)) {
        return i;
      }
    }
    throw new IllegalArgumentException("Unknown instrument: " + raw);
  }
}
