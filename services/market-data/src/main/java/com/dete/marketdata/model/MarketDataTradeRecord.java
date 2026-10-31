package com.dete.marketdata.model;

import com.dete.common.domain.enums.Instrument;
import java.time.Instant;
import java.util.UUID;

/** Historical trade record matching market_data.trades table schema. */
public record MarketDataTradeRecord(
    UUID tradeId,
    Instrument instrument,
    long sequenceNumber,
    long price,
    long quantity,
    UUID buyOrderId,
    UUID sellOrderId,
    UUID buyAccountId,
    UUID sellAccountId,
    Instant executedAt,
    com.dete.common.domain.simulation.SimulationContext simulation) {
  public MarketDataTradeRecord(
      UUID tradeId,
      Instrument instrument,
      long sequenceNumber,
      long price,
      long quantity,
      UUID buyOrderId,
      UUID sellOrderId,
      UUID buyAccountId,
      UUID sellAccountId,
      Instant executedAt) {
    this(
        tradeId,
        instrument,
        sequenceNumber,
        price,
        quantity,
        buyOrderId,
        sellOrderId,
        buyAccountId,
        sellAccountId,
        executedAt,
        null);
  }
}
