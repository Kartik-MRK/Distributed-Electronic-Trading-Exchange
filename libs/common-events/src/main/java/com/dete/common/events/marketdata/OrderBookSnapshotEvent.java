package com.dete.common.events.marketdata;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Full order book snapshot emitted periodically (e.g., every 100ms) by the Matching Engine on topic
 * 'market-data' for read-side CQRS resynchronization.
 */
public record OrderBookSnapshotEvent(
    UUID eventId,
    Instrument instrument,
    long sequenceNumber,
    List<SnapshotLevel> bids,
    List<SnapshotLevel> asks,
    Instant timestamp,
    int version)
    implements DeteEvent {

  public static final String EVENT_TYPE = "ORDER_BOOK_SNAPSHOT";

  @Override
  public String eventType() {
    return EVENT_TYPE;
  }

  public static OrderBookSnapshotEvent of(
      Instrument instrument,
      long sequenceNumber,
      List<SnapshotLevel> bids,
      List<SnapshotLevel> asks) {
    return new OrderBookSnapshotEvent(
        UUID.randomUUID(), instrument, sequenceNumber, bids, asks, Instant.now(), 1);
  }

  public record SnapshotLevel(long price, long volume, int orderCount) {}
}
