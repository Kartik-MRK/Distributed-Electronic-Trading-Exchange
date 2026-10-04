package com.dete.common.events.account;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/** Emitted after a trade is fully settled (buyer and seller balances updated). */
public record BalanceSettledEvent(
    UUID eventId,
    UUID tradeId,
    UUID buyAccountId,
    UUID sellAccountId,
    Instrument instrument,
    long price, // fixed-point
    long quantity, // fixed-point
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "BALANCE_SETTLED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }
}
