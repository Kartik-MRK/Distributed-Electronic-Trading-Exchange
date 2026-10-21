package com.dete.auth.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Domain entity representing a user in the auth service. */
public record User(
    UUID userId,
    String username,
    String email,
    String passwordHash,
    Instant createdAt,
    boolean isDemo,
    String fullName,
    LocalDate dateOfBirth,
    String role,
    boolean isVerified) {

  /** Backwards-compatible constructor for 6-arg usage. */
  public User(
      UUID userId,
      String username,
      String email,
      String passwordHash,
      Instant createdAt,
      boolean isDemo) {
    this(
        userId,
        username,
        email,
        passwordHash,
        createdAt,
        isDemo,
        username,
        null,
        isDemo ? "ROLE_GUEST" : "ROLE_TRADER",
        true);
  }

  public static User createNew(String username, String email, String passwordHash, boolean isDemo) {
    return new User(UUID.randomUUID(), username, email, passwordHash, Instant.now(), isDemo);
  }

  public static User createVerified(
      String username,
      String email,
      String passwordHash,
      String fullName,
      LocalDate dateOfBirth,
      String role) {
    return new User(
        UUID.randomUUID(),
        username,
        email,
        passwordHash,
        Instant.now(),
        false,
        fullName != null && !fullName.isBlank() ? fullName : username,
        dateOfBirth,
        role != null ? role : "ROLE_TRADER",
        true);
  }

  public static User createGuest(UUID userId, String guestUsername, String email) {
    return new User(
        userId,
        guestUsername,
        email,
        "GUEST_NO_PASSWORD",
        Instant.now(),
        true,
        "Guest Trader",
        null,
        "ROLE_GUEST",
        true);
  }
}
