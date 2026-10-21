package com.dete.auth.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Domain entity representing a pending registration OTP. */
public record EmailOtp(
    UUID otpId,
    String email,
    String otpCode,
    String fullName,
    LocalDate dateOfBirth,
    String passwordHash,
    Instant expiresAt,
    boolean consumed,
    Instant createdAt) {

  public static EmailOtp createNew(
      String email,
      String otpCode,
      String fullName,
      LocalDate dateOfBirth,
      String passwordHash,
      long expirationSeconds) {
    Instant now = Instant.now();
    return new EmailOtp(
        UUID.randomUUID(),
        email,
        otpCode,
        fullName,
        dateOfBirth,
        passwordHash,
        now.plusSeconds(expirationSeconds),
        false,
        now);
  }

  public boolean isValid() {
    return !consumed && Instant.now().isBefore(expiresAt);
  }
}
