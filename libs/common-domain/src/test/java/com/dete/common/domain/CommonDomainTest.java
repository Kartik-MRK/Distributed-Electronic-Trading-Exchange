package com.dete.common.domain;

import static org.junit.jupiter.api.Assertions.*;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.AccountId;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.domain.types.InstrumentId;
import com.dete.common.domain.types.Money;
import com.dete.common.domain.types.OrderId;
import com.dete.common.domain.types.Price;
import com.dete.common.domain.types.Quantity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for common-domain types and enums. These are the foundation of the entire system —
 * correctness here is critical.
 */
class CommonDomainTest {

  // ------------------------------------------------------------------
  // FixedPoint
  // ------------------------------------------------------------------

  @Test
  void fixedPoint_ofWhole_oneUsd() {
    FixedPoint one = FixedPoint.ofWhole(1);
    assertEquals(100_000_000L, one.scaledValue());
    assertEquals("1", one.toString());
  }

  @Test
  void fixedPoint_ofDouble_halfBtc() {
    FixedPoint half = FixedPoint.ofDouble(0.5);
    assertEquals(50_000_000L, half.scaledValue());
  }

  @Test
  void fixedPoint_add() {
    FixedPoint a = FixedPoint.ofWhole(1000);
    FixedPoint b = FixedPoint.ofWhole(500);
    assertEquals(FixedPoint.ofWhole(1500), a.add(b));
  }

  @Test
  void fixedPoint_subtract() {
    FixedPoint a = FixedPoint.ofWhole(1000);
    FixedPoint b = FixedPoint.ofWhole(300);
    assertEquals(FixedPoint.ofWhole(700), a.subtract(b));
  }

  @Test
  void fixedPoint_multiply_priceByQuantity() {
    // 65000 USD * 0.1 BTC = 6500 USD notional
    FixedPoint price = FixedPoint.ofWhole(65_000);
    FixedPoint quantity = FixedPoint.ofDouble(0.1);
    FixedPoint notional = price.multiply(quantity);
    assertEquals(FixedPoint.ofWhole(6_500), notional);
  }

  @Test
  void fixedPoint_zero_isZero() {
    assertTrue(FixedPoint.ZERO.isZero());
    assertFalse(FixedPoint.ONE.isZero());
  }

  @Test
  void fixedPoint_compareTo() {
    FixedPoint low = FixedPoint.ofWhole(100);
    FixedPoint high = FixedPoint.ofWhole(200);
    assertTrue(low.isLessThan(high));
    assertTrue(high.isGreaterThan(low));
  }

  // ------------------------------------------------------------------
  // Typed IDs
  // ------------------------------------------------------------------

  @Test
  void orderId_generate_uniqueEachTime() {
    OrderId a = OrderId.generate();
    OrderId b = OrderId.generate();
    assertNotEquals(a, b);
  }

  @Test
  void orderId_ofString_roundTrips() {
    UUID uuid = UUID.randomUUID();
    OrderId id = OrderId.of(uuid.toString());
    assertEquals(uuid, id.value());
  }

  @Test
  void accountId_nullThrows() {
    assertThrows(IllegalArgumentException.class, () -> AccountId.of((UUID) null));
  }

  // ------------------------------------------------------------------
  // Enums
  // ------------------------------------------------------------------

  @Test
  void instrument_fromSymbol_btcUsd() {
    assertEquals(Instrument.BTC_USD, Instrument.fromSymbol("BTC-USD"));
  }

  @Test
  void instrument_kafkaPartition_unique() {
    // Ensure no two instruments share a partition (they must be unique for single-writer model)
    long distinctPartitions =
        java.util.Arrays.stream(Instrument.values())
            .mapToInt(Instrument::kafkaPartition)
            .distinct()
            .count();
    assertEquals(Instrument.values().length, distinctPartitions);
  }

  @Test
  void orderSide_opposite() {
    assertEquals(OrderSide.SELL, OrderSide.BUY.opposite());
    assertEquals(OrderSide.BUY, OrderSide.SELL.opposite());
  }

  @Test
  void orderType_restingAllowed() {
    assertTrue(OrderType.LIMIT.isRestingAllowed());
    assertTrue(OrderType.GTC.isRestingAllowed());
    assertFalse(OrderType.MARKET.isRestingAllowed());
    assertFalse(OrderType.IOC.isRestingAllowed());
    assertFalse(OrderType.FOK.isRestingAllowed());
  }

  @Test
  void orderStatus_terminalStates() {
    assertTrue(OrderStatus.FILLED.isTerminal());
    assertTrue(OrderStatus.CANCELLED.isTerminal());
    assertTrue(OrderStatus.REJECTED.isTerminal());
    assertFalse(OrderStatus.ACCEPTED.isTerminal());
    assertFalse(OrderStatus.PARTIALLY_FILLED.isTerminal());
  }

  @Test
  void orderStatus_validTransitions() {
    assertTrue(OrderStatus.SUBMITTED.canTransitionTo(OrderStatus.ACCEPTED));
    assertTrue(OrderStatus.ACCEPTED.canTransitionTo(OrderStatus.PARTIALLY_FILLED));
    assertTrue(OrderStatus.PARTIALLY_FILLED.canTransitionTo(OrderStatus.FILLED));
    // Terminal states cannot transition
    assertFalse(OrderStatus.FILLED.canTransitionTo(OrderStatus.CANCELLED));
    // Invalid backwards transition
    assertFalse(OrderStatus.PARTIALLY_FILLED.canTransitionTo(OrderStatus.SUBMITTED));
  }

  // ------------------------------------------------------------------
  // Financial & Typed Value Objects
  // ------------------------------------------------------------------

  @Test
  void instrumentId_normalizationAndValidation() {
    InstrumentId id = InstrumentId.of("btc_usd ");
    assertEquals("BTC_USD", id.value());
    assertEquals("BTC_USD", id.toString());
    assertThrows(IllegalArgumentException.class, () -> InstrumentId.of("   "));
  }

  @Test
  void priceAndQuantity_multiplicationYieldsMoney() {
    Price price = Price.ofWhole(50_000);
    Quantity qty = Quantity.ofDouble(0.2);
    Money notional = price.multiply(qty);
    assertEquals(Money.ofWhole(10_000), notional);
    assertEquals(10_000_00000000L, notional.scaledValue());
  }

  @Test
  void quantity_negativeThrows() {
    assertThrows(IllegalArgumentException.class, () -> Quantity.ofDouble(-1.0));
  }

  @Test
  void quantity_subtract() {
    Quantity a = Quantity.ofDouble(1.5);
    Quantity b = Quantity.ofDouble(0.5);
    assertEquals(Quantity.ofWhole(1), a.subtract(b));
    assertThrows(IllegalArgumentException.class, () -> b.subtract(a));
  }

  @Test
  void money_operations() {
    Money balance = Money.ofWhole(100);
    Money fee = Money.ofDouble(1.5);
    Money remaining = balance.subtract(fee);
    assertEquals(Money.ofDouble(98.5), remaining);
    assertTrue(remaining.isPositive());
    assertTrue(remaining.isGreaterThan(fee));
  }
}
