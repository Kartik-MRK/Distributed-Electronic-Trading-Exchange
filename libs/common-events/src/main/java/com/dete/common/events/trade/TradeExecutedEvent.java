package com.dete.common.events.trade;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * The most important event in the system. Emitted by the Matching Engine when two orders match.
 * Triggers settlement in the Account/Ledger Service.
 */
public record TradeExecutedEvent(
    UUID eventId,
    UUID tradeId,
    Instrument instrument,
    UUID buyOrderId,
    UUID sellOrderId,
    UUID buyAccountId,
    UUID sellAccountId,
    long price, // fixed-point fill price
    long quantity, // fixed-point fill quantity
    long sequenceNumber, // global, per-instrument, monotonically increasing
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "TRADE_EXECUTED";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  /** Notional value = price * quantity / SCALE */
  public long notionalValue() {
    return (long) ((double) price * quantity / com.dete.common.domain.types.FixedPoint.SCALE);
  }
}
