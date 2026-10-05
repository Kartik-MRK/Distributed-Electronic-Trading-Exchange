package com.dete.order.client;

import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.exception.InvalidOrderException;
import com.dete.order.exception.PreTradeRiskException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class DefaultPreTradeRiskValidator implements PreTradeRiskValidator {

  private static final Logger log = LoggerFactory.getLogger(DefaultPreTradeRiskValidator.class);

  // Default risk thresholds (in fixed-point units)
  private static final long MAX_BTC_QTY = 100L * FixedPoint.SCALE;
  private static final long MAX_ETH_QTY = 1_000L * FixedPoint.SCALE;
  private static final long MAX_SOL_QTY = 10_000L * FixedPoint.SCALE;
  private static final long MAX_ORDER_NOTIONAL = 5_000_000L * FixedPoint.SCALE; // $5,000,000 USD

  @Override
  @CircuitBreaker(name = "riskService")
  public void validateOrder(CreateOrderRequest request, UUID accountId) {
    if (request == null) {
      throw new InvalidOrderException("Order request cannot be null");
    }

    if (request.quantity() <= 0) {
      throw new InvalidOrderException("Order quantity must be strictly positive");
    }

    if (request.orderType() == OrderType.LIMIT
        || request.orderType() == OrderType.IOC
        || request.orderType() == OrderType.FOK) {
      if (request.price() == null || request.price() <= 0) {
        throw new InvalidOrderException("Price must be strictly positive for limit orders");
      }
    }

    // Size limit per instrument
    long maxQty =
        switch (request.instrument()) {
          case BTC_USD -> MAX_BTC_QTY;
          case ETH_USD -> MAX_ETH_QTY;
          case SOL_USD -> MAX_SOL_QTY;
        };

    if (request.quantity() > maxQty) {
      throw new PreTradeRiskException(
          String.format(
              "Order quantity %d exceeds maximum allowed quantity %d for %s",
              request.quantity(), maxQty, request.instrument()));
    }

    // Notional limit for priced orders
    if (request.price() != null && request.price() > 0) {
      long notional = FixedPoint.multiply(request.price(), request.quantity());
      if (notional > MAX_ORDER_NOTIONAL) {
        throw new PreTradeRiskException(
            String.format(
                "Order notional %d exceeds maximum allowed notional %d",
                notional, MAX_ORDER_NOTIONAL));
      }
    }

    log.debug("Pre-trade risk checks passed for account {} on {}", accountId, request.instrument());
  }
}
