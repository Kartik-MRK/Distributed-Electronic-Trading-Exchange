package com.dete.common.events.order;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted by the Matching Engine when an order enters the order book. */
public record OrderAcceptedEvent(
    UUID eventId, UUID orderId, long sequenceNumber, Instant timestamp, int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_ACCEPTED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderAcceptedEvent of(UUID orderId, long sequenceNumber) {
    return new OrderAcceptedEvent(UUID.randomUUID(), orderId, sequenceNumber, Instant.now(), 1);
  }
}
