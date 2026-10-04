package com.dete.auth.model;

import java.time.Instant;
import java.util.UUID;

/** Domain entity representing a stored refresh token hash. */
public record RefreshToken(
    UUID tokenId,
    UUID userId,
    String tokenHash,
    Instant expiresAt,
    boolean revoked,
    Instant createdAt) {

  public static RefreshToken createNew(UUID userId, String tokenHash, Instant expiresAt) {
    return new RefreshToken(UUID.randomUUID(), userId, tokenHash, expiresAt, false, Instant.now());
  }

  public boolean isExpired() {
    return Instant.now().isAfter(expiresAt);
  }

  public boolean isValid() {
    return !revoked && !isExpired();
  }
}
