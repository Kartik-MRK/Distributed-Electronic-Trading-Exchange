package com.dete.matching.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.matching.engine.OrderBook;
import com.dete.matching.engine.model.MatchResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/**
 * Property-based tests verifying core financial and structural invariants of the Matching Engine.
 */
class MatchingEnginePropertyTest {

  record GeneratedOrder(UUID accountId, OrderSide side, long price, long quantity) {}

  @Provide
  Arbitrary<List<GeneratedOrder>> distinctAccountOrders() {
    Arbitrary<UUID> accountArb = Arbitraries.create(UUID::randomUUID);
    Arbitrary<OrderSide> sideArb = Arbitraries.of(OrderSide.BUY, OrderSide.SELL);
    Arbitrary<Long> priceArb = Arbitraries.longs().between(100L, 100_000L);
    Arbitrary<Long> qtyArb = Arbitraries.longs().between(1L, 1000L);

    Arbitrary<GeneratedOrder> orderArb =
        Combinators.combine(accountArb, sideArb, priceArb, qtyArb).as(GeneratedOrder::new);

    return orderArb.list().ofMinSize(5).ofMaxSize(100);
  }

  @Provide
  Arbitrary<List<GeneratedOrder>> sharedAccountOrders() {
    Arbitrary<UUID> accountArb =
        Arbitraries.of(
            UUID.fromString("00000000-0000-0000-0000-000000000001"),
            UUID.fromString("00000000-0000-0000-0000-000000000002"),
            UUID.fromString("00000000-0000-0000-0000-000000000003"));

    Arbitrary<OrderSide> sideArb = Arbitraries.of(OrderSide.BUY, OrderSide.SELL);
    Arbitrary<Long> priceArb = Arbitraries.longs().between(100L, 100_000L);
    Arbitrary<Long> qtyArb = Arbitraries.longs().between(1L, 1000L);

    Arbitrary<GeneratedOrder> orderArb =
        Combinators.combine(accountArb, sideArb, priceArb, qtyArb).as(GeneratedOrder::new);

    return orderArb.list().ofMinSize(5).ofMaxSize(100);
  }

  @Property(tries = 100)
  void propertyUncrossedBookWithDistinctAccounts(
      @ForAll("distinctAccountOrders") List<GeneratedOrder> orders) {
    OrderBook book = new OrderBook(Instrument.BTC_USD);
    long totalSubmittedVolume = 0;
    long totalMatchedVolume = 0;

    for (GeneratedOrder o : orders) {
      totalSubmittedVolume += o.quantity();
      OrderPlacedEvent event =
          new OrderPlacedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              o.accountId(),
              Instrument.BTC_USD,
              o.side(),
              OrderType.LIMIT,
              o.price(),
              o.quantity(),
              UUID.randomUUID(),
              Instant.now(),
              1);

      MatchResult result = book.processOrder(event);

      for (TradeExecutedEvent trade : result.trades()) {
        assertThat(trade.price()).isPositive();
        assertThat(trade.quantity()).isPositive();
        totalMatchedVolume += trade.quantity() * 2;
      }
    }

    // Invariant 1: Total resting + matched <= submitted
    long restingBidVol = book.totalBidVolume();
    long restingAskVol = book.totalAskVolume();
    assertThat(restingBidVol + restingAskVol + (totalMatchedVolume / 2))
        .isLessThanOrEqualTo(totalSubmittedVolume);

    // Invariant 2: With distinct market accounts, book is never crossed (bestBid < bestAsk)
    long bestBid = book.bestBidPrice();
    long bestAsk = book.bestAskPrice();
    if (bestBid > 0 && bestAsk > 0) {
      assertThat(bestBid)
          .as("Book crossed: bestBid %d must be strictly less than bestAsk %d", bestBid, bestAsk)
          .isLessThan(bestAsk);
    }
  }

  @Property(tries = 100)
  void propertyVolumeConservationWithSharedAccounts(
      @ForAll("sharedAccountOrders") List<GeneratedOrder> orders) {
    OrderBook book = new OrderBook(Instrument.BTC_USD);
    long totalSubmittedVolume = 0;
    long totalMatchedVolume = 0;

    for (GeneratedOrder o : orders) {
      totalSubmittedVolume += o.quantity();
      OrderPlacedEvent event =
          new OrderPlacedEvent(
              UUID.randomUUID(),
              UUID.randomUUID(),
              o.accountId(),
              Instrument.BTC_USD,
              o.side(),
              OrderType.LIMIT,
              o.price(),
              o.quantity(),
              UUID.randomUUID(),
              Instant.now(),
              1);

      MatchResult result = book.processOrder(event);

      for (TradeExecutedEvent trade : result.trades()) {
        assertThat(trade.price()).isPositive();
        assertThat(trade.quantity()).isPositive();
        // Self-trade prevention invariant: buyer and seller accounts must NEVER be the same
        assertThat(trade.buyAccountId()).isNotEqualTo(trade.sellAccountId());
        totalMatchedVolume += trade.quantity() * 2;
      }
    }

    // Invariant: Non-negative and volume conservation holds even under STP
    long restingBidVol = book.totalBidVolume();
    long restingAskVol = book.totalAskVolume();
    assertThat(restingBidVol).isNotNegative();
    assertThat(restingAskVol).isNotNegative();
    assertThat(restingBidVol + restingAskVol + (totalMatchedVolume / 2))
        .isLessThanOrEqualTo(totalSubmittedVolume);
  }
}
