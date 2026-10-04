package com.dete.auth.model;

import java.time.Instant;
import java.util.UUID;

/** Domain entity representing a user in the auth service. */
public record User(
    UUID userId,
    String username,
    String email,
    String passwordHash,
    Instant createdAt,
    boolean isDemo) {

  public static User createNew(String username, String email, String passwordHash, boolean isDemo) {
    return new User(UUID.randomUUID(), username, email, passwordHash, Instant.now(), isDemo);
  }
}
