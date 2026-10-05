package com.dete.risk.model;

import com.dete.common.domain.enums.OrderSide;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks real-time risk state for a single account: active open orders count and resting prices for
 * self-trade prevention.
 */
public class AccountRiskState {

  private final UUID accountId;
  private final AtomicInteger openOrdersCount = new AtomicInteger(0);

  // orderId -> RestingOrderInfo
  private final Map<UUID, RestingOrderInfo> restingOrders = new ConcurrentHashMap<>();

  public record RestingOrderInfo(OrderSide side, long price) {}

  public AccountRiskState(UUID accountId) {
    this.accountId = accountId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public int getOpenOrdersCount() {
    return openOrdersCount.get();
  }

  public int incrementOpenOrders() {
    return openOrdersCount.incrementAndGet();
  }

  public int decrementOpenOrders() {
    return openOrdersCount.updateAndGet(current -> Math.max(0, current - 1));
  }

  public void addRestingOrder(UUID orderId, OrderSide side, long price) {
    if (orderId != null && price > 0) {
      restingOrders.put(orderId, new RestingOrderInfo(side, price));
    }
  }

  public void removeRestingOrder(UUID orderId) {
    if (orderId != null) {
      restingOrders.remove(orderId);
    }
  }

  /**
   * Check whether this account already has a resting order on the opposite side that would
   * immediately match/cross with an incoming order (Rule 6: Self-Trade Prevention).
   */
  public boolean hasCrossingRestingOrder(OrderSide incomingSide, Long incomingPrice) {
    if (restingOrders.isEmpty()) {
      return false;
    }

    for (RestingOrderInfo resting : restingOrders.values()) {
      if (incomingSide == OrderSide.BUY && resting.side() == OrderSide.SELL) {
        // Buyer is willing to pay incomingPrice. If resting ask <= incomingPrice (or market order),
        // crosses!
        if (incomingPrice == null || incomingPrice <= 0 || resting.price() <= incomingPrice) {
          return true;
        }
      } else if (incomingSide == OrderSide.SELL && resting.side() == OrderSide.BUY) {
        // Seller is willing to accept incomingPrice. If resting bid >= incomingPrice (or market
        // order), crosses!
        if (incomingPrice == null || incomingPrice <= 0 || resting.price() >= incomingPrice) {
          return true;
        }
      }
    }
    return false;
  }

  public void clear() {
    openOrdersCount.set(0);
    restingOrders.clear();
  }
}
