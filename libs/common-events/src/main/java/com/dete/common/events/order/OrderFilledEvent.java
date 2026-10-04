package com.dete.common.events.order;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted when an order is completely filled. */
public record OrderFilledEvent(
    UUID eventId,
    UUID orderId,
    UUID tradeId,
    long fillQuantity, // scaled fixed-point
    long fillPrice, // scaled fixed-point
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_FILLED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }
}
