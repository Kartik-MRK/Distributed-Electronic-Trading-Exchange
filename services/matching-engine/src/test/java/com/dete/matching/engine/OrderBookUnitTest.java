package com.dete.matching.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.model.BookOrder;
import com.dete.matching.engine.model.L2Depth;
import com.dete.matching.engine.model.MatchResult;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OrderBookUnitTest {

  private OrderBook orderBook;
  private UUID accountA;
  private UUID accountB;
  private UUID accountC;

  @BeforeEach
  void setUp() {
    orderBook = new OrderBook(Instrument.BTC_USD);
    accountA = UUID.randomUUID();
    accountB = UUID.randomUUID();
    accountC = UUID.randomUUID();
  }

  private OrderPlacedEvent createOrder(
      UUID accountId, OrderSide side, OrderType type, long price, long quantity) {
    return new OrderPlacedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        accountId,
        Instrument.BTC_USD,
        side,
        type,
        price,
        quantity,
        UUID.randomUUID(),
        Instant.now(),
        1);
  }

  @Test
  @DisplayName("Limit order rests in book when no crossing orders exist")
  void testLimitOrderRests() {
    OrderPlacedEvent bid =
        createOrder(
            accountA,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE);

    MatchResult result = orderBook.processOrder(bid);

    assertThat(result.isRejected()).isFalse();
    assertThat(result.acceptedEvent()).isNotNull();
    assertThat(result.trades()).isEmpty();
    assertThat(orderBook.orderCount()).isEqualTo(1);
    assertThat(orderBook.bestBidPrice()).isEqualTo(50_000 * FixedPoint.SCALE);
    assertThat(orderBook.bestAskPrice()).isEqualTo(0);
    assertThat(orderBook.totalBidVolume()).isEqualTo(1 * FixedPoint.SCALE);
  }

  @Test
  @DisplayName("Crossing limit orders execute at maker's resting price (price improvement)")
  void testLimitOrderMatchPriceImprovement() {
    // Resting ask at 50,000
    OrderPlacedEvent ask =
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            2 * FixedPoint.SCALE);
    orderBook.processOrder(ask);

    // Incoming bid willing to pay up to 51,000
    OrderPlacedEvent bid =
        createOrder(
            accountB,
            OrderSide.BUY,
            OrderType.LIMIT,
            51_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE);
    MatchResult result = orderBook.processOrder(bid);

    assertThat(result.trades()).hasSize(1);
    var trade = result.trades().get(0);
    assertThat(trade.price()).isEqualTo(50_000 * FixedPoint.SCALE); // Maker's price!
    assertThat(trade.quantity()).isEqualTo(1 * FixedPoint.SCALE);
    assertThat(trade.buyAccountId()).isEqualTo(accountB);
    assertThat(trade.sellAccountId()).isEqualTo(accountA);

    assertThat(result.filledEvents()).hasSize(1);
    assertThat(result.filledEvents().get(0).orderId()).isEqualTo(bid.orderId());

    assertThat(result.partiallyFilledEvents()).hasSize(1);
    assertThat(result.partiallyFilledEvents().get(0).orderId()).isEqualTo(ask.orderId());
    assertThat(result.partiallyFilledEvents().get(0).remainingQuantity())
        .isEqualTo(1 * FixedPoint.SCALE);

    // Remaining 1 BTC ask still resting at 50,000
    assertThat(orderBook.orderCount()).isEqualTo(1);
    assertThat(orderBook.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE);
  }

  @Test
  @DisplayName("Price-time priority (FIFO): same-price orders fill strictly in arrival sequence")
  void testPriceTimePriorityFifo() {
    long price = 50_000 * FixedPoint.SCALE;
    long qty = 1 * FixedPoint.SCALE;

    OrderPlacedEvent ask1 = createOrder(accountA, OrderSide.SELL, OrderType.LIMIT, price, qty);
    OrderPlacedEvent ask2 = createOrder(accountB, OrderSide.SELL, OrderType.LIMIT, price, qty);

    orderBook.processOrder(ask1);
    orderBook.processOrder(ask2);

    assertThat(orderBook.totalAskVolume()).isEqualTo(2 * FixedPoint.SCALE);

    // Incoming bid for 1 BTC should match ask1 (first arrived), leaving ask2 untouched
    OrderPlacedEvent bid = createOrder(accountC, OrderSide.BUY, OrderType.LIMIT, price, qty);
    MatchResult result = orderBook.processOrder(bid);

    assertThat(result.trades()).hasSize(1);
    assertThat(result.trades().get(0).sellOrderId()).isEqualTo(ask1.orderId());
    assertThat(orderBook.getOrder(ask1.orderId())).isNull(); // ask1 fully filled and removed
    assertThat(orderBook.getOrder(ask2.orderId())).isNotNull(); // ask2 still resting
    assertThat(orderBook.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE);
  }

  @Test
  @DisplayName("Market order aggressively matches against book and rejects when empty")
  void testMarketOrderExecution() {
    // Empty book rejects market order
    OrderPlacedEvent marketBuyEmpty =
        createOrder(accountA, OrderSide.BUY, OrderType.MARKET, 0, 1 * FixedPoint.SCALE);
    MatchResult emptyResult = orderBook.processOrder(marketBuyEmpty);
    assertThat(emptyResult.isRejected()).isTrue();
    assertThat(emptyResult.rejectedEvent().reason()).contains("No liquidity");

    // Add asks
    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));
    orderBook.processOrder(
        createOrder(
            accountB,
            OrderSide.SELL,
            OrderType.LIMIT,
            51_000 * FixedPoint.SCALE,
            2 * FixedPoint.SCALE));

    // Market buy for 2 BTC sweeps 1 BTC @ 50,000 and 1 BTC @ 51,000
    OrderPlacedEvent marketBuy =
        createOrder(accountC, OrderSide.BUY, OrderType.MARKET, 0, 2 * FixedPoint.SCALE);
    MatchResult result = orderBook.processOrder(marketBuy);

    assertThat(result.trades()).hasSize(2);
    assertThat(result.trades().get(0).price()).isEqualTo(50_000 * FixedPoint.SCALE);
    assertThat(result.trades().get(0).quantity()).isEqualTo(1 * FixedPoint.SCALE);
    assertThat(result.trades().get(1).price()).isEqualTo(51_000 * FixedPoint.SCALE);
    assertThat(result.trades().get(1).quantity()).isEqualTo(1 * FixedPoint.SCALE);

    // 1 BTC remains resting at 51,000
    assertThat(orderBook.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE);
    assertThat(orderBook.bestAskPrice()).isEqualTo(51_000 * FixedPoint.SCALE);
  }

  @Test
  @DisplayName("Market order remainder is cancelled immediately if book liquidity is exhausted")
  void testMarketOrderRemainderCancelled() {
    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));

    // Market buy for 3 BTC when only 1 BTC available
    OrderPlacedEvent marketBuy =
        createOrder(accountB, OrderSide.BUY, OrderType.MARKET, 0, 3 * FixedPoint.SCALE);
    MatchResult result = orderBook.processOrder(marketBuy);

    assertThat(result.trades()).hasSize(1);
    assertThat(result.cancelledEvent()).isNotNull();
    assertThat(result.cancelledEvent().remainingQuantity()).isEqualTo(2 * FixedPoint.SCALE);
    assertThat(orderBook.orderCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("IOC (Immediate-Or-Cancel) fills crossing liquidity and cancels unfilled remainder")
  void testIocOrder() {
    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));

    // IOC Buy for 3 BTC @ 50,000
    OrderPlacedEvent iocBuy =
        createOrder(
            accountB,
            OrderSide.BUY,
            OrderType.IOC,
            50_000 * FixedPoint.SCALE,
            3 * FixedPoint.SCALE);
    MatchResult result = orderBook.processOrder(iocBuy);

    assertThat(result.trades()).hasSize(1);
    assertThat(result.cancelledEvent()).isNotNull();
    assertThat(result.cancelledEvent().remainingQuantity()).isEqualTo(2 * FixedPoint.SCALE);
    assertThat(orderBook.orderCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("FOK (Fill-Or-Kill) fills completely if sufficient depth, else rejects with 0 fills")
  void testFokOrder() {
    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));

    // FOK for 2 BTC when only 1 BTC available -> REJECT with 0 fills
    OrderPlacedEvent fokFail =
        createOrder(
            accountB,
            OrderSide.BUY,
            OrderType.FOK,
            50_000 * FixedPoint.SCALE,
            2 * FixedPoint.SCALE);
    MatchResult failResult = orderBook.processOrder(fokFail);

    assertThat(failResult.isRejected()).isTrue();
    assertThat(failResult.trades()).isEmpty();
    assertThat(orderBook.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE); // Resting ask untouched

    // Add another 1 BTC ask at 50,000 -> Now 2 BTC available
    orderBook.processOrder(
        createOrder(
            accountC,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));

    OrderPlacedEvent fokSuccess =
        createOrder(
            accountB,
            OrderSide.BUY,
            OrderType.FOK,
            50_000 * FixedPoint.SCALE,
            2 * FixedPoint.SCALE);
    MatchResult successResult = orderBook.processOrder(fokSuccess);

    assertThat(successResult.isRejected()).isFalse();
    assertThat(successResult.trades()).hasSize(2);
    assertThat(orderBook.orderCount()).isEqualTo(0);
  }

  @Test
  @DisplayName("O(1) cancellation removes order and cleans up empty price level")
  void testCancelOrderO1() {
    OrderPlacedEvent bid =
        createOrder(
            accountA,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE);
    orderBook.processOrder(bid);

    assertThat(orderBook.orderCount()).isEqualTo(1);
    assertThat(orderBook.bestBidPrice()).isEqualTo(50_000 * FixedPoint.SCALE);

    MatchResult cancelResult = orderBook.cancelOrder(bid.orderId(), accountA);
    assertThat(cancelResult.cancelledEvent()).isNotNull();
    assertThat(cancelResult.cancelledEvent().orderId()).isEqualTo(bid.orderId());
    assertThat(cancelResult.cancelledEvent().remainingQuantity()).isEqualTo(1 * FixedPoint.SCALE);

    assertThat(orderBook.orderCount()).isEqualTo(0);
    assertThat(orderBook.bestBidPrice()).isEqualTo(0);
    assertThat(orderBook.getOrder(bid.orderId())).isNull();

    // Second cancel should be rejected (not found)
    MatchResult secondCancel = orderBook.cancelOrder(bid.orderId(), accountA);
    assertThat(secondCancel.isRejected()).isTrue();
  }

  @Test
  @DisplayName("Self-Trade Prevention (STP) skips resting orders from the same account")
  void testSelfTradePrevention() {
    // Account A places resting ask at 50,000
    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));
    // Account B places resting ask at 50,000
    orderBook.processOrder(
        createOrder(
            accountB,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));

    // Account A sends bid at 50,000 for 1 BTC. STP must skip Account A's ask and match Account B's
    // ask!
    OrderPlacedEvent bidA =
        createOrder(
            accountA,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE);
    MatchResult result = orderBook.processOrder(bidA);

    assertThat(result.trades()).hasSize(1);
    var trade = result.trades().get(0);
    assertThat(trade.buyAccountId()).isEqualTo(accountA);
    assertThat(trade.sellAccountId()).isEqualTo(accountB); // Matched B, skipped A!

    // Account A's resting ask is still in the book
    assertThat(orderBook.totalAskVolume()).isEqualTo(1 * FixedPoint.SCALE);
    BookOrder restingA = orderBook.getOrder(result.trades().get(0).sellOrderId());
    assertThat(restingA).isNull(); // B's ask was filled
  }

  @Test
  @DisplayName(
      "Modify order executes as cancel-and-reinsert at back of queue with fresh sequence number")
  void testModifyOrder() {
    long price = 50_000 * FixedPoint.SCALE;
    OrderPlacedEvent ask1 =
        createOrder(accountA, OrderSide.SELL, OrderType.LIMIT, price, 1 * FixedPoint.SCALE);
    OrderPlacedEvent ask2 =
        createOrder(accountB, OrderSide.SELL, OrderType.LIMIT, price, 1 * FixedPoint.SCALE);

    orderBook.processOrder(ask1);
    orderBook.processOrder(ask2);

    long origSeq = orderBook.getOrder(ask1.orderId()).sequenceNumber();

    // Modify ask1 to 2 BTC at same price
    OrderModifyCommand modify =
        OrderModifyCommand.of(
            ask1.orderId(), accountA, Instrument.BTC_USD, price, 2 * FixedPoint.SCALE);
    MatchResult modifyResult = orderBook.modifyOrder(modify);

    assertThat(modifyResult.cancelledEvent()).isNotNull();
    assertThat(orderBook.getOrder(ask1.orderId())).isNotNull();
    assertThat(orderBook.getOrder(ask1.orderId()).remainingQuantity())
        .isEqualTo(2 * FixedPoint.SCALE);
    assertThat(orderBook.getOrder(ask1.orderId()).sequenceNumber()).isGreaterThan(origSeq);

    // Because ask1 was reinserted, ask2 now has time priority!
    OrderPlacedEvent incomingBid =
        createOrder(accountC, OrderSide.BUY, OrderType.LIMIT, price, 1 * FixedPoint.SCALE);
    MatchResult matchResult = orderBook.processOrder(incomingBid);

    assertThat(matchResult.trades()).hasSize(1);
    assertThat(matchResult.trades().get(0).sellOrderId())
        .isEqualTo(ask2.orderId()); // ask2 filled first!
  }

  @Test
  @DisplayName("L2 Depth correctly aggregates price levels and volumes")
  void testL2Depth() {
    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.BUY,
            OrderType.LIMIT,
            49_900 * FixedPoint.SCALE,
            1 * FixedPoint.SCALE));
    orderBook.processOrder(
        createOrder(
            accountB,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            2 * FixedPoint.SCALE));
    orderBook.processOrder(
        createOrder(
            accountC,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000 * FixedPoint.SCALE,
            3 * FixedPoint.SCALE));

    orderBook.processOrder(
        createOrder(
            accountA,
            OrderSide.SELL,
            OrderType.LIMIT,
            50_100 * FixedPoint.SCALE,
            5 * FixedPoint.SCALE));

    L2Depth depth = orderBook.getL2Depth(5);
    assertThat(depth.bids()).hasSize(2);
    assertThat(depth.bids().get(0).price()).isEqualTo(50_000 * FixedPoint.SCALE);
    assertThat(depth.bids().get(0).volume()).isEqualTo(5 * FixedPoint.SCALE); // 2 + 3
    assertThat(depth.bids().get(0).orderCount()).isEqualTo(2);

    assertThat(depth.bids().get(1).price()).isEqualTo(49_900 * FixedPoint.SCALE);
    assertThat(depth.bids().get(1).volume()).isEqualTo(1 * FixedPoint.SCALE);

    assertThat(depth.asks()).hasSize(1);
    assertThat(depth.asks().get(0).price()).isEqualTo(50_100 * FixedPoint.SCALE);
    assertThat(depth.asks().get(0).volume()).isEqualTo(5 * FixedPoint.SCALE);
  }
}
