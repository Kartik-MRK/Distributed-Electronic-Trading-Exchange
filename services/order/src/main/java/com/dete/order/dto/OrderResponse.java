package com.dete.order.dto;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import com.dete.order.model.OrderRecord;
import java.time.Instant;
import java.util.UUID;

public record OrderResponse(
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

  public static OrderResponse fromRecord(OrderRecord record) {
    return new OrderResponse(
        record.orderId(),
        record.accountId(),
        record.instrument(),
        record.side(),
        record.orderType(),
        record.price(),
        record.originalQty(),
        record.remainingQty(),
        record.filledQty(),
        record.status(),
        record.idempotencyKey(),
        record.rejectReason(),
        record.createdAt(),
        record.updatedAt());
  }
}
