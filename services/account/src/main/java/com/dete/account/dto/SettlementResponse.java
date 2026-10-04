package com.dete.account.dto;

import com.dete.account.model.Settlement;
import com.dete.common.domain.types.FixedPoint;
import java.time.Instant;
import java.util.UUID;

public record SettlementResponse(
    UUID settlementId,
    UUID tradeId,
    UUID buyerId,
    UUID sellerId,
    String instrument,
    long price,
    double priceDisplay,
    long quantity,
    double quantityDisplay,
    long notionalValue,
    double notionalValueDisplay,
    Instant settledAt) {

  public static SettlementResponse from(Settlement s) {
    long notional =
        FixedPoint.ofScaled(s.price()).multiply(FixedPoint.ofScaled(s.quantity())).scaledValue();
    return new SettlementResponse(
        s.settlementId(),
        s.tradeId(),
        s.buyerId(),
        s.sellerId(),
        s.instrument(),
        s.price(),
        FixedPoint.ofScaled(s.price()).toDouble(),
        s.quantity(),
        FixedPoint.ofScaled(s.quantity()).toDouble(),
        notional,
        FixedPoint.ofScaled(notional).toDouble(),
        s.settledAt());
  }
}
