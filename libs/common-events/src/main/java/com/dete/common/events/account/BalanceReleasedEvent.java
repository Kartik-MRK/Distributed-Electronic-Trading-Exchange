package com.dete.common.events.account;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted when a reservation is released (order cancelled or rejected). */
public record BalanceReleasedEvent(
    UUID eventId,
    UUID accountId,
    UUID orderId,
    long amount, // fixed-point
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "BALANCE_RELEASED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }
}
