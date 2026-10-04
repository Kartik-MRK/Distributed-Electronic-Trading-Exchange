package com.dete.matching.controller;

import com.dete.common.domain.enums.Instrument;
import com.dete.matching.engine.OrderBook;
import com.dete.matching.engine.model.L2Depth;
import com.dete.matching.replay.KafkaReplayService;
import com.dete.matching.service.MatchingEngineService;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Controller exposing Level 2 order book depth and operational metrics for the matching engine. */
@RestController
@RequestMapping("/matching")
public class MatchingEngineController {

  private final MatchingEngineService matchingEngineService;
  private final KafkaReplayService replayService;

  public MatchingEngineController(
      MatchingEngineService matchingEngineService, KafkaReplayService replayService) {
    this.matchingEngineService = matchingEngineService;
    this.replayService = replayService;
  }

  @GetMapping("/orderbook/{symbol}")
  public CompletableFuture<ResponseEntity<L2Depth>> getOrderBook(
      @PathVariable String symbol, @RequestParam(defaultValue = "10") int depth) {
    try {
      Instrument instrument = Instrument.valueOf(symbol.toUpperCase());
      return matchingEngineService.getL2Depth(instrument, depth).thenApply(ResponseEntity::ok);
    } catch (IllegalArgumentException e) {
      return CompletableFuture.completedFuture(ResponseEntity.badRequest().build());
    }
  }

  @GetMapping("/orderbook/{symbol}/stats")
  public ResponseEntity<Map<String, Object>> getStats(@PathVariable String symbol) {
    try {
      Instrument instrument = Instrument.valueOf(symbol.toUpperCase());
      OrderBook book = matchingEngineService.getOrderBook(instrument);
      if (book == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(
          Map.of(
              "instrument", instrument.name(),
              "sequenceNumber", book.currentSequenceNumber(),
              "orderCount", book.orderCount(),
              "bestBidPrice", book.bestBidPrice(),
              "bestAskPrice", book.bestAskPrice(),
              "totalBidVolume", book.totalBidVolume(),
              "totalAskVolume", book.totalAskVolume()));
    } catch (IllegalArgumentException e) {
      return ResponseEntity.badRequest().build();
    }
  }

  @PostMapping("/replay")
  public ResponseEntity<Map<String, Object>> triggerReplay() {
    int replayed = replayService.replayFromBeginning();
    return ResponseEntity.ok(Map.of("status", "COMPLETED", "commandsReplayed", replayed));
  }
}
