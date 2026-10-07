package com.dete.matching.concurrency;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.model.MatchResult;
import com.dete.matching.service.MatchingEngineService;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class CancelVsFillConcurrencyTest {

  private MatchingEngineService matchingEngineService;
  private ExecutorService testExecutor;

  @BeforeEach
  void setUp() {
    matchingEngineService = new MatchingEngineService(null);
    testExecutor = Executors.newFixedThreadPool(4);
  }

  @AfterEach
  void tearDown() {
    testExecutor.shutdown();
  }

  @Test
  @DisplayName(
      "Concurrent Cancel vs Fill: When cancel and crossing fill race, exactly one wins and volume is strictly conserved")
  void concurrentCancelVsFillExactlyOneWins() throws Exception {
    int iterations = 100;
    AtomicInteger cancelWonCount = new AtomicInteger(0);
    AtomicInteger fillWonCount = new AtomicInteger(0);

    for (int i = 0; i < iterations; i++) {
      UUID makerAccountId = UUID.randomUUID();
      UUID takerAccountId = UUID.randomUUID();
      UUID makerOrderId = UUID.randomUUID();
      long price = 50_000_00000000L;
      long qty = 1_00000000L;

      // 1. Submit resting maker SELL order
      OrderPlacedEvent makerOrder =
          new OrderPlacedEvent(
              UUID.randomUUID(),
              makerOrderId,
              makerAccountId,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              price,
              qty,
              UUID.randomUUID(),
              Instant.now(),
              1);
      matchingEngineService.processOrder(makerOrder).get(2, TimeUnit.SECONDS);

      // Verify maker order is resting in book
      assertThat(matchingEngineService.getOrderBook(Instrument.BTC_USD).getOrder(makerOrderId))
          .isNotNull();

      // 2. Prepare racing threads: Thread A cancels, Thread B matches with crossing BUY
      CountDownLatch startGate = new CountDownLatch(1);

      OrderCancelCommand cancelCommand =
          new OrderCancelCommand(
              UUID.randomUUID(),
              makerOrderId,
              makerAccountId,
              Instrument.BTC_USD,
              Instant.now(),
              1);

      OrderPlacedEvent takerOrder =
          new OrderPlacedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              takerAccountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              price,
              qty,
              UUID.randomUUID(),
              Instant.now(),
              1);

      CompletableFuture<MatchResult> cancelFuture =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  startGate.await();
                  return matchingEngineService.cancelOrder(cancelCommand).get();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              testExecutor);

      CompletableFuture<MatchResult> fillFuture =
          CompletableFuture.supplyAsync(
              () -> {
                try {
                  startGate.await();
                  return matchingEngineService.processOrder(takerOrder).get();
                } catch (Exception e) {
                  throw new RuntimeException(e);
                }
              },
              testExecutor);

      // Trigger race condition
      startGate.countDown();

      MatchResult cancelResult = cancelFuture.get(5, TimeUnit.SECONDS);
      MatchResult fillResult = fillFuture.get(5, TimeUnit.SECONDS);

      boolean cancelWon = cancelResult.cancelledEvent() != null;
      boolean fillWon =
          fillResult.trades().stream().anyMatch(t -> t.sellOrderId().equals(makerOrderId));

      // Core invariant: Exactly one of {cancel, fill} wins! Never both, never neither!
      assertThat(cancelWon ^ fillWon)
          .as(
              "Race condition invariant violated at iteration %d: cancelWon=%b, fillWon=%b",
              i, cancelWon, fillWon)
          .isTrue();

      if (cancelWon) {
        cancelWonCount.incrementAndGet();
      } else {
        fillWonCount.incrementAndGet();
      }

      // Cleanup remaining resting taker if cancel won
      if (cancelWon) {
        matchingEngineService
            .cancelOrder(
                new OrderCancelCommand(
                    UUID.randomUUID(),
                    takerOrder.orderId(),
                    takerAccountId,
                    Instrument.BTC_USD,
                    Instant.now(),
                    1))
            .get(2, TimeUnit.SECONDS);
      }
    }

    assertThat(cancelWonCount.get() + fillWonCount.get()).isEqualTo(iterations);
  }
}
