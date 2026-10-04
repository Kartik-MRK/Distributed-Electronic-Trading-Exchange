package com.dete.common.events.order;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted when an order cannot be accepted by the matching engine or risk service. */
public record OrderRejectedEvent(
    UUID eventId, UUID orderId, String reason, Instant timestamp, int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_REJECTED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderRejectedEvent of(UUID orderId, String reason) {
    return new OrderRejectedEvent(UUID.randomUUID(), orderId, reason, Instant.now(), 1);
  }
}
