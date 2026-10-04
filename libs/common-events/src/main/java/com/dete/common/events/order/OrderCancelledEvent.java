package com.dete.common.events.order;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted when an order is cancelled (by user request or IOC/FOK remainder). */
public record OrderCancelledEvent(
    UUID eventId,
    UUID orderId,
    long remainingQuantity, // scaled fixed-point
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_CANCELLED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderCancelledEvent of(UUID orderId, long remainingQuantity) {
    return new OrderCancelledEvent(UUID.randomUUID(), orderId, remainingQuantity, Instant.now(), 1);
  }
}
