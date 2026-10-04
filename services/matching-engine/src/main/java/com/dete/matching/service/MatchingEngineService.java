package com.dete.matching.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.OrderBook;
import com.dete.matching.engine.model.L2Depth;
import com.dete.matching.engine.model.MatchResult;
import jakarta.annotation.PreDestroy;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Core Matching Engine Service implementing the Single-Writer Architecture. Each Instrument is
 * dedicated to a single-threaded executor and an in-memory OrderBook. This guarantees zero-lock,
 * deterministic, FIFO execution per instrument.
 */
@Service
public class MatchingEngineService {

  private static final Logger log = LoggerFactory.getLogger(MatchingEngineService.class);

  private final Map<Instrument, OrderBook> orderBooks = new EnumMap<>(Instrument.class);
  private final Map<Instrument, ExecutorService> dispatchers = new EnumMap<>(Instrument.class);

  public MatchingEngineService() {
    for (Instrument instrument : Instrument.values()) {
      orderBooks.put(instrument, new OrderBook(instrument));
      dispatchers.put(
          instrument,
          Executors.newSingleThreadExecutor(
              new MatchingThreadFactory("me-" + instrument.symbol().toLowerCase())));
    }
    log.info("Initialized MatchingEngineService with {} instrument order books", orderBooks.size());
  }

  public OrderBook getOrderBook(Instrument instrument) {
    return orderBooks.get(instrument);
  }

  /** Dispatches an incoming OrderPlacedEvent to the instrument's single-writer thread. */
  public CompletableFuture<MatchResult> processOrder(OrderPlacedEvent event) {
    Instrument instrument = event.instrument();
    OrderBook book = orderBooks.get(instrument);
    ExecutorService dispatcher = dispatchers.get(instrument);

    return CompletableFuture.supplyAsync(() -> book.processOrder(event), dispatcher);
  }

  /** Dispatches an order cancellation command to the instrument's single-writer thread. */
  public CompletableFuture<MatchResult> cancelOrder(OrderCancelCommand command) {
    Instrument instrument = command.instrument();
    OrderBook book = orderBooks.get(instrument);
    ExecutorService dispatcher = dispatchers.get(instrument);

    return CompletableFuture.supplyAsync(
        () -> book.cancelOrder(command.orderId(), command.accountId()), dispatcher);
  }

  /** Dispatches an order modify command to the instrument's single-writer thread. */
  public CompletableFuture<MatchResult> modifyOrder(OrderModifyCommand command) {
    Instrument instrument = command.instrument();
    OrderBook book = orderBooks.get(instrument);
    ExecutorService dispatcher = dispatchers.get(instrument);

    return CompletableFuture.supplyAsync(() -> book.modifyOrder(command), dispatcher);
  }

  /** Fetches Level 2 order book depth for an instrument. */
  public CompletableFuture<L2Depth> getL2Depth(Instrument instrument, int depth) {
    OrderBook book = orderBooks.get(instrument);
    ExecutorService dispatcher = dispatchers.get(instrument);

    return CompletableFuture.supplyAsync(() -> book.getL2Depth(depth), dispatcher);
  }

  /** Resets all order books (used for testing or pre-replay). */
  public void resetAll() {
    for (Map.Entry<Instrument, OrderBook> entry : orderBooks.entrySet()) {
      ExecutorService dispatcher = dispatchers.get(entry.getKey());
      try {
        dispatcher.submit(() -> entry.getValue().clear()).get(2, TimeUnit.SECONDS);
      } catch (Exception e) {
        log.error("Error resetting order book for instrument {}", entry.getKey(), e);
      }
    }
  }

  @PreDestroy
  public void shutdown() {
    log.info("Shutting down MatchingEngineService dispatchers...");
    for (ExecutorService dispatcher : dispatchers.values()) {
      dispatcher.shutdown();
      try {
        if (!dispatcher.awaitTermination(3, TimeUnit.SECONDS)) {
          dispatcher.shutdownNow();
        }
      } catch (InterruptedException e) {
        dispatcher.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private static class MatchingThreadFactory implements ThreadFactory {
    private final String prefix;
    private final AtomicInteger counter = new AtomicInteger(1);

    public MatchingThreadFactory(String prefix) {
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, prefix + "-" + counter.getAndIncrement());
      t.setDaemon(true);
      return t;
    }
  }
}
