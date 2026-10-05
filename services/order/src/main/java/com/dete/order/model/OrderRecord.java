package com.dete.order.model;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import java.time.Instant;
import java.util.UUID;

/** Representation of an order stored in the order_svc.orders table. */
public record OrderRecord(
    UUID orderId,
    UUID accountId,
    Instrument instrument,
    OrderSide side,
    OrderType orderType,
    Long price,
    long originalQty,
    long remainingQty,
    long filledQty,
    OrderStatus status,
    UUID idempotencyKey,
    String rejectReason,
    Instant createdAt,
    Instant updatedAt) {

  public static OrderRecord createNew(
      UUID orderId,
      UUID accountId,
      Instrument instrument,
      OrderSide side,
      OrderType orderType,
      Long price,
      long quantity,
      UUID idempotencyKey) {
    Instant now = Instant.now();
    return new OrderRecord(
        orderId,
        accountId,
        instrument,
        side,
        orderType,
        price,
        quantity,
        quantity,
        0L,
        OrderStatus.SUBMITTED,
        idempotencyKey,
        null,
        now,
        now);
  }

  public boolean isCancellable() {
    return status == OrderStatus.SUBMITTED
        || status == OrderStatus.ACCEPTED
        || status == OrderStatus.PARTIALLY_FILLED;
  }
}
