package com.dete.account.model;

import java.time.Instant;
import java.util.UUID;

public record Settlement(
    UUID settlementId,
    UUID tradeId,
    UUID buyerId,
    UUID sellerId,
    String instrument,
    long price,
    long quantity,
    Instant settledAt) {

  public static Settlement createNew(
      UUID tradeId, UUID buyerId, UUID sellerId, String instrument, long price, long quantity) {
    return new Settlement(
        UUID.randomUUID(), tradeId, buyerId, sellerId, instrument, price, quantity, Instant.now());
  }
}
