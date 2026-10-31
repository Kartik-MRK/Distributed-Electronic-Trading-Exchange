package com.dete.marketdata.model;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.trade.TradeExecutedEvent;
import java.time.Instant;
import java.util.UUID;

/** Public execution data deliberately excludes account IDs and private order IDs. */
public record PublicTrade(
    UUID tradeId,
    Instrument instrument,
    long sequenceNumber,
    long price,
    long quantity,
    Instant timestamp,
    UUID epochId,
    Instant marketTime,
    Long elapsedMs) {
  public PublicTrade(
      UUID tradeId,
      Instrument instrument,
      long sequenceNumber,
      long price,
      long quantity,
      Instant timestamp) {
    this(tradeId, instrument, sequenceNumber, price, quantity, timestamp, null, null, null);
  }

  public static PublicTrade from(TradeExecutedEvent trade) {
    return new PublicTrade(
        trade.tradeId(),
        trade.instrument(),
        trade.sequenceNumber(),
        trade.price(),
        trade.quantity(),
        trade.timestamp(),
        trade.simulation() == null ? null : trade.simulation().epochId(),
        trade.simulation() == null ? null : trade.simulation().marketTime(),
        trade.simulation() == null ? null : trade.simulation().elapsedMs());
  }

  public static PublicTrade from(MarketDataTradeRecord trade) {
    return new PublicTrade(
        trade.tradeId(),
        trade.instrument(),
        trade.sequenceNumber(),
        trade.price(),
        trade.quantity(),
        trade.executedAt(),
        trade.simulation() == null ? null : trade.simulation().epochId(),
        trade.simulation() == null ? null : trade.simulation().marketTime(),
        trade.simulation() == null ? null : trade.simulation().elapsedMs());
  }

  @com.fasterxml.jackson.annotation.JsonProperty("executedAt")
  public Instant executedAt() {
    return timestamp;
  }
}
