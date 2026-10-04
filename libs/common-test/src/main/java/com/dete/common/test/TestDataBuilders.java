package com.dete.common.test;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.AccountId;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.domain.types.InstrumentId;
import com.dete.common.domain.types.Money;
import com.dete.common.domain.types.OrderId;
import com.dete.common.domain.types.Price;
import com.dete.common.domain.types.Quantity;
import com.dete.common.domain.types.TradeId;
import com.dete.common.events.account.BalanceReleasedEvent;
import com.dete.common.events.account.BalanceReservedEvent;
import com.dete.common.events.account.BalanceSettledEvent;
import com.dete.common.events.audit.AuditEvent;
import com.dete.common.events.order.OrderAcceptedEvent;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.events.order.OrderRejectedEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import java.time.Instant;
import java.util.UUID;

/** Test data builders for creating mock domain objects and events across test suites. */
public final class TestDataBuilders {

  private TestDataBuilders() {}

  // ---------------------------------------------------------------------------
  // Domain Types
  // ---------------------------------------------------------------------------

  public static AccountId anAccountId() {
    return AccountId.generate();
  }

  public static OrderId anOrderId() {
    return OrderId.generate();
  }

  public static TradeId aTradeId() {
    return TradeId.generate();
  }

  public static InstrumentId anInstrumentId() {
    return InstrumentId.of("BTC_USD");
  }

  public static Price aPrice(double val) {
    return Price.ofDouble(val);
  }

  public static Quantity aQuantity(double val) {
    return Quantity.ofDouble(val);
  }

  public static Money aMoney(double val) {
    return Money.ofDouble(val);
  }

  // ---------------------------------------------------------------------------
  // Order Events
  // ---------------------------------------------------------------------------

  public static OrderPlacedEvent anOrderPlacedEvent() {
    return anOrderPlacedEvent(
        anOrderId(), anAccountId(), Instrument.BTC_USD, OrderSide.BUY, 65_000.0, 1.0);
  }

  public static OrderPlacedEvent anOrderPlacedEvent(
      OrderId orderId,
      AccountId accountId,
      Instrument instrument,
      OrderSide side,
      double price,
      double quantity) {
    return new OrderPlacedEvent(
        UUID.randomUUID(),
        orderId.value(),
        accountId.value(),
        instrument,
        side,
        OrderType.LIMIT,
        FixedPoint.ofDouble(price).scaledValue(),
        FixedPoint.ofDouble(quantity).scaledValue(),
        UUID.randomUUID(),
        Instant.now(),
        1);
  }

  public static OrderAcceptedEvent anOrderAcceptedEvent(OrderId orderId, long sequenceNumber) {
    return OrderAcceptedEvent.of(orderId.value(), sequenceNumber);
  }

  public static OrderRejectedEvent anOrderRejectedEvent(OrderId orderId, String reason) {
    return OrderRejectedEvent.of(orderId.value(), reason);
  }

  public static OrderCancelledEvent anOrderCancelledEvent(
      OrderId orderId, double remainingQuantity) {
    return OrderCancelledEvent.of(
        orderId.value(), FixedPoint.ofDouble(remainingQuantity).scaledValue());
  }

  public static OrderPartiallyFilledEvent anOrderPartiallyFilledEvent(
      OrderId orderId, double fillQty, double fillPrice, double remainingQty, TradeId tradeId) {
    return new OrderPartiallyFilledEvent(
        UUID.randomUUID(),
        orderId.value(),
        tradeId.value(),
        FixedPoint.ofDouble(fillQty).scaledValue(),
        FixedPoint.ofDouble(fillPrice).scaledValue(),
        FixedPoint.ofDouble(remainingQty).scaledValue(),
        Instant.now(),
        1);
  }

  public static OrderFilledEvent anOrderFilledEvent(
      OrderId orderId, double fillQty, double fillPrice, TradeId tradeId) {
    return new OrderFilledEvent(
        UUID.randomUUID(),
        orderId.value(),
        tradeId.value(),
        FixedPoint.ofDouble(fillQty).scaledValue(),
        FixedPoint.ofDouble(fillPrice).scaledValue(),
        Instant.now(),
        1);
  }

  // ---------------------------------------------------------------------------
  // Trade Events
  // ---------------------------------------------------------------------------

  public static TradeExecutedEvent aTradeExecutedEvent() {
    OrderId buyOrder = anOrderId();
    OrderId sellOrder = anOrderId();
    AccountId buyer = anAccountId();
    AccountId seller = anAccountId();
    return new TradeExecutedEvent(
        UUID.randomUUID(),
        UUID.randomUUID(),
        Instrument.BTC_USD,
        buyOrder.value(),
        sellOrder.value(),
        buyer.value(),
        seller.value(),
        FixedPoint.ofDouble(65_000.0).scaledValue(),
        FixedPoint.ofDouble(0.5).scaledValue(),
        1L,
        Instant.now(),
        1);
  }

  // ---------------------------------------------------------------------------
  // Account Events
  // ---------------------------------------------------------------------------

  public static BalanceReservedEvent aBalanceReservedEvent(AccountId accountId, OrderId orderId) {
    return new BalanceReservedEvent(
        UUID.randomUUID(),
        accountId.value(),
        orderId.value(),
        Instrument.BTC_USD,
        OrderSide.BUY,
        FixedPoint.ofDouble(32_500.0).scaledValue(),
        Instant.now(),
        1);
  }

  public static BalanceReleasedEvent aBalanceReleasedEvent(AccountId accountId, OrderId orderId) {
    return new BalanceReleasedEvent(
        UUID.randomUUID(),
        accountId.value(),
        orderId.value(),
        FixedPoint.ofDouble(32_500.0).scaledValue(),
        Instant.now(),
        1);
  }

  public static BalanceSettledEvent aBalanceSettledEvent(
      TradeId tradeId, AccountId buyer, AccountId seller) {
    return new BalanceSettledEvent(
        UUID.randomUUID(),
        tradeId.value(),
        buyer.value(),
        seller.value(),
        Instrument.BTC_USD,
        FixedPoint.ofDouble(65_000.0).scaledValue(),
        FixedPoint.ofDouble(0.5).scaledValue(),
        Instant.now(),
        1);
  }

  // ---------------------------------------------------------------------------
  // Audit Event
  // ---------------------------------------------------------------------------

  public static AuditEvent anAuditEvent(String eventType, String subjectType, String payload) {
    return new AuditEvent(
        UUID.randomUUID(),
        eventType,
        subjectType,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "BTC_USD",
        payload,
        UUID.randomUUID().toString(),
        Instant.now(),
        1);
  }
}
