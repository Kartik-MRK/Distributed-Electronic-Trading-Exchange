package com.dete.common.domain.simulation;

import java.time.Instant;
import java.util.UUID;

public record MarketClockSnapshot(
    UUID epochId,
    Instant initializedAt,
    Instant serverNow,
    UUID sessionId,
    UUID ownerUserId,
    String status,
    long fence,
    long elapsedMs,
    long remainingMs,
    Instant startsAt,
    Instant expiresAt,
    Instant leaseUntil,
    boolean tradingAllowed,
    Integer durationSeconds,
    String completionReason) {
  public SimulationContext context() {
    return new SimulationContext(
        epochId,
        sessionId,
        fence,
        initializedAt,
        initializedAt.plusMillis(elapsedMs),
        elapsedMs,
        serverNow);
  }
}
