package com.dete.common.events.order;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Published to order.commands when a new order passes validation, risk check, and balance
 * reservation. This is the entry point into the matching engine pipeline.
 */
public record OrderPlacedEvent(
    UUID eventId,
    UUID orderId,
    UUID accountId,
    Instrument instrument,
    OrderSide side,
    OrderType orderType,
    long price, // scaled fixed-point; 0 for MARKET orders
    long quantity, // scaled fixed-point
    UUID idempotencyKey,
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_PLACED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderPlacedEvent of(
      UUID orderId,
      UUID accountId,
      Instrument instrument,
      OrderSide side,
      OrderType orderType,
      long price,
      long quantity,
      UUID idempotencyKey) {
    return new OrderPlacedEvent(
        UUID.randomUUID(),
        orderId,
        accountId,
        instrument,
        side,
        orderType,
        price,
        quantity,
        idempotencyKey,
        Instant.now(),
        1);
  }
}
