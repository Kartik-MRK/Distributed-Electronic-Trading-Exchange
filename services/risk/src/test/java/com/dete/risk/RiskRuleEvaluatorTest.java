package com.dete.risk;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.common.domain.types.FixedPoint;
import com.dete.risk.config.RiskProperties;
import com.dete.risk.engine.RiskRuleEvaluator;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class RiskRuleEvaluatorTest {

  private RiskProperties properties;
  private RiskRuleEvaluator evaluator;
  private UUID accountId;

  @BeforeEach
  void setUp() {
    properties = new RiskProperties();
    evaluator = new RiskRuleEvaluator(properties);
    accountId = UUID.randomUUID();
  }

  @Nested
  @DisplayName("Rule 1: Max Order Size Tests")
  class Rule1MaxOrderSizeTests {

    @Test
    @DisplayName("Should pass when order quantity is at or below maximum allowed quantity")
    void shouldPassWhenWithinMaxOrderSize() {
      ValidateOrderRequest btcRequest =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              40_000L * FixedPoint.SCALE,
              100L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(btcRequest);
      assertThat(response.approved()).isTrue();
      assertThat(response.rejectionReason()).isEmpty();
    }

    @Test
    @DisplayName("Should reject when order quantity exceeds maximum allowed quantity")
    void shouldRejectWhenExceedingMaxOrderSize() {
      ValidateOrderRequest btcRequest =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              101L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(btcRequest);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 1 Violation");
    }

    @Test
    @DisplayName("Should reject ETH order exceeding 1,000 ETH limit")
    void shouldRejectEthExceedingLimit() {
      ValidateOrderRequest ethRequest =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.ETH_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              3_000L * FixedPoint.SCALE,
              1_001L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(ethRequest);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 1 Violation");
    }
  }

  @Nested
  @DisplayName("Rule 2: Max Notional Tests")
  class Rule2MaxNotionalTests {

    @Test
    @DisplayName("Should pass when notional value <= $5,000,000 USD")
    void shouldPassWithinNotionalLimit() {
      // 50 BTC @ $60,000 = $3,000,000 USD
      ValidateOrderRequest request =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              50L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(request);
      assertThat(response.approved()).isTrue();
    }

    @Test
    @DisplayName("Should reject when notional value exceeds $5,000,000 USD")
    void shouldRejectWhenExceedingNotionalLimit() {
      // 90 BTC @ $60,000 = $5,400,000 USD (exceeds 5M)
      ValidateOrderRequest request =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              90L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(request);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 2 Violation");
    }
  }

  @Nested
  @DisplayName("Rule 3: Max Open Orders Tests")
  class Rule3MaxOpenOrdersTests {

    @Test
    @DisplayName("Should pass when open orders count is below threshold")
    void shouldPassWhenOpenOrdersBelowLimit() {
      for (int i = 0; i < 49; i++) {
        evaluator.getAccountState(accountId).incrementOpenOrders();
      }

      ValidateOrderRequest request =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(request);
      assertThat(response.approved()).isTrue();
    }

    @Test
    @DisplayName("Should reject when account has 50 or more open orders")
    void shouldRejectWhenExceedingMaxOpenOrders() {
      for (int i = 0; i < 50; i++) {
        evaluator.getAccountState(accountId).incrementOpenOrders();
      }

      ValidateOrderRequest request =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(request);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 3 Violation");

      // Decrementing allows new orders again
      evaluator.getAccountState(accountId).decrementOpenOrders();
      ValidateOrderResponse retryResponse = evaluator.evaluate(request);
      assertThat(retryResponse.approved()).isTrue();
    }
  }

  @Nested
  @DisplayName("Rule 4: Price Deviation Tests")
  class Rule4PriceDeviationTests {

    @BeforeEach
    void setupLastTradePrice() {
      // Last trade price for BTC is $60,000
      evaluator.updateLastTradePrice(Instrument.BTC_USD, 60_000L * FixedPoint.SCALE);
    }

    @Test
    @DisplayName("Should pass limit order within 10% price deviation band ($54,000 to $66,000)")
    void shouldPassWithinDeviationBand() {
      ValidateOrderRequest buyWithin =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              65_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);
      assertThat(evaluator.evaluate(buyWithin).approved()).isTrue();

      ValidateOrderRequest sellWithin =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              55_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);
      assertThat(evaluator.evaluate(sellWithin).approved()).isTrue();
    }

    @Test
    @DisplayName("Should reject BUY limit order priced > +10% above last trade price")
    void shouldRejectAggressiveBuyDeviation() {
      // 67,000 is > 66,000 (10% above 60,000)
      ValidateOrderRequest buyTooHigh =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              67_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(buyTooHigh);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 4 Violation");
    }

    @Test
    @DisplayName("Should reject SELL limit order priced < -10% below last trade price")
    void shouldRejectAggressiveSellDeviation() {
      // 53,000 is < 54,000 (10% below 60,000)
      ValidateOrderRequest sellTooLow =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              53_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(sellTooLow);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 4 Violation");
    }

    @Test
    @DisplayName("Should pass limit order when within reference range")
    void shouldPassWhenWithinRange() {
      ValidateOrderRequest solRequest =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.SOL_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              150L * FixedPoint.SCALE,
              10L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(solRequest);
      assertThat(response.approved()).isTrue();
    }
  }

  @Nested
  @DisplayName("Rule 5: Market Order Guard Tests")
  class Rule5MarketOrderGuardTests {

    @Test
    @DisplayName("Should reject market order if reference price is zero")
    void shouldRejectMarketOrderWithoutReferencePrice() {
      evaluator.updateLastTradePrice(Instrument.BTC_USD, 0L);

      ValidateOrderRequest marketOrder =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.MARKET,
              null,
              10L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(marketOrder);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 5 Violation");
    }

    @Test
    @DisplayName(
        "Should pass market order when quantity <= 50% max order size and last price exists")
    void shouldPassValidMarketOrder() {
      evaluator.updateLastTradePrice(Instrument.BTC_USD, 60_000L * FixedPoint.SCALE);

      // Max size for BTC is 100, 50% is 50. 40 BTC passes.
      ValidateOrderRequest marketOrder =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.MARKET,
              null,
              40L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(marketOrder);
      assertThat(response.approved()).isTrue();
    }

    @Test
    @DisplayName("Should reject market order exceeding 50% of instrument max order size")
    void shouldRejectMarketOrderExceedingHalfMaxSize() {
      evaluator.updateLastTradePrice(Instrument.BTC_USD, 60_000L * FixedPoint.SCALE);

      // Max size for BTC is 100, 50% is 50. 51 BTC rejected.
      ValidateOrderRequest marketOrder =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.MARKET,
              null,
              51L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(marketOrder);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 5 Violation");
    }

    @Test
    @DisplayName("Should reject market order exceeding max notional based on last trade price")
    void shouldRejectMarketOrderExceedingNotional() {
      evaluator.updateLastTradePrice(Instrument.BTC_USD, 150_000L * FixedPoint.SCALE);

      // 40 BTC @ 150,000 = $6,000,000 USD (exceeds 5M)
      ValidateOrderRequest marketOrder =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.MARKET,
              null,
              40L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(marketOrder);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 2 Violation");
    }
  }

  @Nested
  @DisplayName("Rule 6: Self-Trade Prevention Tests")
  class Rule6SelfTradePreventionTests {

    @Test
    @DisplayName(
        "Should reject BUY order when account has resting SELL order at equal or lower price")
    void shouldRejectCrossingRestingSell() {
      UUID order1 = UUID.randomUUID();
      // Resting SELL order at 60,000
      evaluator
          .getAccountState(accountId)
          .addRestingOrder(order1, OrderSide.SELL, 60_000L * FixedPoint.SCALE);

      // New BUY order at 60,000 (crosses resting sell)
      ValidateOrderRequest crossingBuy =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(crossingBuy);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 6 Violation");
    }

    @Test
    @DisplayName(
        "Should reject SELL order when account has resting BUY order at equal or higher price")
    void shouldRejectCrossingRestingBuy() {
      UUID order1 = UUID.randomUUID();
      // Resting BUY order at 60,000
      evaluator
          .getAccountState(accountId)
          .addRestingOrder(order1, OrderSide.BUY, 60_000L * FixedPoint.SCALE);

      // New SELL order at 59,000 (crosses resting buy)
      ValidateOrderRequest crossingSell =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              59_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(crossingSell);
      assertThat(response.approved()).isFalse();
      assertThat(response.rejectionReason()).contains("Rule 6 Violation");
    }

    @Test
    @DisplayName("Should allow orders on opposite side that do NOT cross resting price levels")
    void shouldAllowNonCrossingOppositeSideOrders() {
      UUID order1 = UUID.randomUUID();
      // Resting BUY order at 59,000
      evaluator
          .getAccountState(accountId)
          .addRestingOrder(order1, OrderSide.BUY, 59_000L * FixedPoint.SCALE);

      // New SELL order at 61,000 (does not cross)
      ValidateOrderRequest nonCrossingSell =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.SELL,
              OrderType.LIMIT,
              61_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(nonCrossingSell);
      assertThat(response.approved()).isTrue();
    }

    @Test
    @DisplayName("Should allow new order once resting order is closed")
    void shouldAllowOrderAfterRestingOrderRemoved() {
      UUID order1 = UUID.randomUUID();
      evaluator
          .getAccountState(accountId)
          .addRestingOrder(order1, OrderSide.SELL, 60_000L * FixedPoint.SCALE);

      // Close the resting order
      evaluator.onOrderClosed(accountId, order1);

      ValidateOrderRequest buyOrder =
          new ValidateOrderRequest(
              UUID.randomUUID(),
              accountId,
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              60_000L * FixedPoint.SCALE,
              1L * FixedPoint.SCALE);

      ValidateOrderResponse response = evaluator.evaluate(buyOrder);
      assertThat(response.approved()).isTrue();
    }
  }
}
