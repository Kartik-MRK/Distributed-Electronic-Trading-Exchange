package com.dete.common.events.order;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted for each partial fill of a resting order. */
public record OrderPartiallyFilledEvent(
    UUID eventId,
    UUID orderId,
    UUID tradeId,
    long fillQuantity, // scaled fixed-point
    long fillPrice, // scaled fixed-point
    long remainingQuantity, // scaled fixed-point
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_PARTIALLY_FILLED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }
}
