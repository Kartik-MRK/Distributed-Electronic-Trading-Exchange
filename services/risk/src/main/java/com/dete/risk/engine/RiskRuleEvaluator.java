package com.dete.risk.engine;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.common.domain.types.FixedPoint;
import com.dete.risk.config.RiskProperties;
import com.dete.risk.model.AccountRiskState;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Core risk rule evaluator implementing all 6 pre-trade risk controls defined in DETE Phase 5. */
@Component
public class RiskRuleEvaluator {

  private static final Logger log = LoggerFactory.getLogger(RiskRuleEvaluator.class);

  private final RiskProperties properties;

  // Real-time last traded price per instrument (scaled fixed-point)
  private final Map<Instrument, Long> lastTradePrices = new ConcurrentHashMap<>();

  // Account risk tracking state
  private final Map<UUID, AccountRiskState> accountStates = new ConcurrentHashMap<>();

  // orderId -> accountId index for terminal event cleanup
  private final Map<UUID, UUID> orderToAccount = new ConcurrentHashMap<>();

  public RiskRuleEvaluator(RiskProperties properties) {
    this.properties = properties;
    // Initialize default reference prices (can be overridden by trade.executions)
    lastTradePrices.put(Instrument.BTC_USD, 60_000L * FixedPoint.SCALE);
    lastTradePrices.put(Instrument.ETH_USD, 3_000L * FixedPoint.SCALE);
    lastTradePrices.put(Instrument.SOL_USD, 150L * FixedPoint.SCALE);
  }

  public ValidateOrderResponse evaluate(ValidateOrderRequest request) {
    if (request == null) {
      return ValidateOrderResponse.reject("Order request cannot be null");
    }

    Instrument instrument = request.instrument();
    if (instrument == null) {
      return ValidateOrderResponse.reject("Instrument is required");
    }

    long qty = request.quantity();
    if (qty <= 0) {
      return ValidateOrderResponse.reject("Order quantity must be strictly positive");
    }

    AccountRiskState accountState =
        accountStates.computeIfAbsent(request.accountId(), AccountRiskState::new);

    // -------------------------------------------------------------
    // RULE 1: Max Order Size per Instrument
    // -------------------------------------------------------------
    long maxQty = properties.getMaxQty(instrument);
    if (qty > maxQty) {
      return ValidateOrderResponse.reject(
          String.format(
              "Rule 1 Violation: Order quantity %d exceeds maximum allowed limit %d for %s",
              qty, maxQty, instrument));
    }

    // -------------------------------------------------------------
    // RULE 2: Max Order Notional Limit
    // -------------------------------------------------------------
    Long price = request.price();
    long effectivePrice;
    if (price != null && price > 0) {
      effectivePrice = price;
    } else {
      effectivePrice = lastTradePrices.getOrDefault(instrument, 0L);
    }

    if (effectivePrice > 0) {
      long notional = FixedPoint.multiply(effectivePrice, qty);
      if (notional > properties.getMaxOrderNotional()) {
        return ValidateOrderResponse.reject(
            String.format(
                "Rule 2 Violation: Order notional %d exceeds maximum allowed limit %d USD",
                notional, properties.getMaxOrderNotional()));
      }
    }

    // -------------------------------------------------------------
    // RULE 3: Max Concurrent Open Orders per Account
    // -------------------------------------------------------------
    if (accountState.getOpenOrdersCount() >= properties.getMaxOpenOrdersPerAccount()) {
      return ValidateOrderResponse.reject(
          String.format(
              "Rule 3 Violation: Account %s has reached the limit of %d concurrent open orders",
              request.accountId(), properties.getMaxOpenOrdersPerAccount()));
    }

    // -------------------------------------------------------------
    // RULE 4: Price Deviation from Last Traded Price
    // -------------------------------------------------------------
    if (request.orderType() == OrderType.LIMIT
        || request.orderType() == OrderType.IOC
        || request.orderType() == OrderType.FOK) {
      if (price == null || price <= 0) {
        return ValidateOrderResponse.reject("Price must be strictly positive for limit orders");
      }

      long lastPrice = lastTradePrices.getOrDefault(instrument, 0L);
      if (lastPrice > 0) {
        double maxDev = properties.getMaxPriceDeviationPct();
        long maxAllowedPrice = Math.round(lastPrice * (1.0 + maxDev));
        long minAllowedPrice = Math.round(lastPrice * (1.0 - maxDev));

        if (request.side() == OrderSide.BUY && price > maxAllowedPrice) {
          return ValidateOrderResponse.reject(
              String.format(
                  "Rule 4 Violation: BUY price %d exceeds upper deviation limit %d (last price: %d, max dev: %.1f%%)",
                  price, maxAllowedPrice, lastPrice, maxDev * 100));
        }

        if (request.side() == OrderSide.SELL && price < minAllowedPrice) {
          return ValidateOrderResponse.reject(
              String.format(
                  "Rule 4 Violation: SELL price %d is below lower deviation limit %d (last price: %d, max dev: %.1f%%)",
                  price, minAllowedPrice, lastPrice, maxDev * 100));
        }
      }
    }

    // -------------------------------------------------------------
    // RULE 5: Market Order Guard
    // -------------------------------------------------------------
    if (request.orderType() == OrderType.MARKET) {
      long lastPrice = lastTradePrices.getOrDefault(instrument, 0L);
      if (lastPrice <= 0) {
        return ValidateOrderResponse.reject(
            String.format(
                "Rule 5 Violation: Market order rejected: no reference price available for %s",
                instrument));
      }

      long maxMarketQty = Math.round(maxQty * properties.getMarketOrderMaxQtyRatio());
      if (qty > maxMarketQty) {
        return ValidateOrderResponse.reject(
            String.format(
                "Rule 5 Violation: Market order quantity %d exceeds market order guard threshold %d for %s",
                qty, maxMarketQty, instrument));
      }
    }

    // -------------------------------------------------------------
    // RULE 6: Self-Trade Prevention Check
    // -------------------------------------------------------------
    if (accountState.hasCrossingRestingOrder(request.side(), request.price())) {
      return ValidateOrderResponse.reject(
          String.format(
              "Rule 6 Violation: Self-trade detected for account %s on %s with resting opposite order",
              request.accountId(), instrument));
    }

    // All 6 rules passed! Update tracking state for this account
    accountState.incrementOpenOrders();
    if (request.orderId() != null) {
      orderToAccount.put(request.orderId(), request.accountId());
      if (price != null && price > 0) {
        accountState.addRestingOrder(request.orderId(), request.side(), price);
      }
    }

    log.debug(
        "Order {} approved by pre-trade risk for account {} on {}",
        request.orderId(),
        request.accountId(),
        instrument);
    return ValidateOrderResponse.approve();
  }

  public void updateLastTradePrice(Instrument instrument, long price) {
    if (instrument != null) {
      if (price > 0) {
        lastTradePrices.put(instrument, price);
        log.debug("Updated last trade price for {}: {}", instrument, price);
      } else {
        lastTradePrices.remove(instrument);
        log.debug("Cleared last trade price for {}", instrument);
      }
    }
  }

  public long getLastTradePrice(Instrument instrument) {
    return lastTradePrices.getOrDefault(instrument, 0L);
  }

  public AccountRiskState getAccountState(UUID accountId) {
    return accountStates.computeIfAbsent(accountId, AccountRiskState::new);
  }

  public void onOrderClosed(UUID accountId, UUID orderId) {
    UUID resolvedAccountId = accountId;
    if (resolvedAccountId == null && orderId != null) {
      resolvedAccountId = orderToAccount.remove(orderId);
    } else if (orderId != null) {
      orderToAccount.remove(orderId);
    }

    if (resolvedAccountId != null) {
      AccountRiskState state = accountStates.get(resolvedAccountId);
      if (state != null) {
        state.decrementOpenOrders();
        if (orderId != null) {
          state.removeRestingOrder(orderId);
        }
      }
    }
  }
}
