package com.dete.order.client;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.exception.InvalidOrderException;
import com.dete.order.exception.PreTradeRiskException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreTradeRiskValidatorTest {

  private DefaultPreTradeRiskValidator validator;
  private UUID accountId;

  @BeforeEach
  void setUp() {
    validator = new DefaultPreTradeRiskValidator();
    accountId = UUID.randomUUID();
  }

  @Test
  @DisplayName("Valid LIMIT BUY order passes pre-trade risk checks")
  void testValidLimitBuyOrder() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    assertThatCode(() -> validator.validateOrder(request, accountId)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Valid MARKET order passes pre-trade risk checks without price")
  void testValidMarketOrder() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.SOL_USD, OrderSide.SELL, OrderType.MARKET, null, 50L * FixedPoint.SCALE);

    assertThatCode(() -> validator.validateOrder(request, accountId)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Quantity <= 0 throws InvalidOrderException")
  void testQuantityZeroOrNegativeThrows() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD, OrderSide.BUY, OrderType.LIMIT, 60_000L * FixedPoint.SCALE, 0L);

    assertThatThrownBy(() -> validator.validateOrder(request, accountId))
        .isInstanceOf(InvalidOrderException.class)
        .hasMessageContaining("strictly positive");
  }

  @Test
  @DisplayName("Limit order with missing or non-positive price throws InvalidOrderException")
  void testLimitOrderMissingPriceThrows() {
    CreateOrderRequest req1 =
        new CreateOrderRequest(
            Instrument.ETH_USD, OrderSide.BUY, OrderType.LIMIT, null, 1L * FixedPoint.SCALE);

    assertThatThrownBy(() -> validator.validateOrder(req1, accountId))
        .isInstanceOf(InvalidOrderException.class);

    CreateOrderRequest req2 =
        new CreateOrderRequest(
            Instrument.ETH_USD, OrderSide.BUY, OrderType.LIMIT, -10L, 1L * FixedPoint.SCALE);

    assertThatThrownBy(() -> validator.validateOrder(req2, accountId))
        .isInstanceOf(InvalidOrderException.class);
  }

  @Test
  @DisplayName("Order quantity exceeding instrument limit throws PreTradeRiskException")
  void testQuantityExceedsLimitThrows() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            20_000L * FixedPoint.SCALE,
            101L * FixedPoint.SCALE); // Max is 100 BTC

    assertThatThrownBy(() -> validator.validateOrder(request, accountId))
        .isInstanceOf(PreTradeRiskException.class)
        .hasMessageContaining("exceeds maximum allowed quantity");
  }

  @Test
  @DisplayName("Order notional exceeding $5,000,000 throws PreTradeRiskException")
  void testNotionalExceedsLimitThrows() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            80_000L * FixedPoint.SCALE,
            80L * FixedPoint.SCALE); // Notional = $6,400,000 > $5,000,000

    assertThatThrownBy(() -> validator.validateOrder(request, accountId))
        .isInstanceOf(PreTradeRiskException.class)
        .hasMessageContaining("exceeds maximum allowed notional");
  }
}
