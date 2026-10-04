package com.dete.common.events.order;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Command published to order.commands to modify price or quantity of a resting order. In the
 * matching engine, this executes as cancel-and-reinsert with a new sequence number.
 */
public record OrderModifyCommand(
    UUID eventId,
    UUID orderId,
    UUID accountId,
    Instrument instrument,
    long newPrice,
    long newQuantity,
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_MODIFY_COMMAND";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderModifyCommand of(
      UUID orderId, UUID accountId, Instrument instrument, long newPrice, long newQuantity) {
    return new OrderModifyCommand(
        UUID.randomUUID(), orderId, accountId, instrument, newPrice, newQuantity, Instant.now(), 1);
  }
}
