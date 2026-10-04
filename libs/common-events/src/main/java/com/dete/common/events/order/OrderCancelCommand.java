package com.dete.common.events.order;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Command published to order.commands to cancel a resting order in the matching engine. */
public record OrderCancelCommand(
    UUID eventId,
    UUID orderId,
    UUID accountId,
    Instrument instrument,
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_CANCEL_COMMAND";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderCancelCommand of(UUID orderId, UUID accountId, Instrument instrument) {
    return new OrderCancelCommand(
        UUID.randomUUID(), orderId, accountId, instrument, Instant.now(), 1);
  }
}
