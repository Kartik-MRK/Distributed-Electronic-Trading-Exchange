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
    Instant timestamp) {
  public static PublicTrade from(TradeExecutedEvent trade) {
    return new PublicTrade(
        trade.tradeId(),
        trade.instrument(),
        trade.sequenceNumber(),
        trade.price(),
        trade.quantity(),
        trade.timestamp());
  }

  public static PublicTrade from(MarketDataTradeRecord trade) {
    return new PublicTrade(
        trade.tradeId(),
        trade.instrument(),
        trade.sequenceNumber(),
        trade.price(),
        trade.quantity(),
        trade.executedAt());
  }

  @com.fasterxml.jackson.annotation.JsonProperty("executedAt")
  public Instant executedAt() {
    return timestamp;
  }
}
