package com.dete.common.events.account;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted after funds are reserved for an order. Consumed by audit service. */
public record BalanceReservedEvent(
    UUID eventId,
    UUID accountId,
    UUID orderId,
    Instrument instrument,
    OrderSide side,
    long amount, // fixed-point
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "BALANCE_RESERVED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }
}
