package com.dete.auth.dto;

import com.dete.auth.model.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record UserResponse(
    UUID userId,
    String username,
    String email,
    Instant createdAt,
    boolean isDemo,
    String fullName,
    LocalDate dateOfBirth,
    String role,
    boolean isVerified) {

  /** Backwards-compatible constructor for 5-parameter calls. */
  public UserResponse(
      UUID userId, String username, String email, Instant createdAt, boolean isDemo) {
    this(
        userId,
        username,
        email,
        createdAt,
        isDemo,
        username,
        null,
        isDemo ? "ROLE_GUEST" : "ROLE_TRADER",
        true);
  }

  public static UserResponse fromUser(User user) {
    return new UserResponse(
        user.userId(),
        user.username(),
        user.email(),
        user.createdAt(),
        user.isDemo(),
        user.fullName() != null ? user.fullName() : user.username(),
        user.dateOfBirth(),
        user.role() != null ? user.role() : (user.isDemo() ? "ROLE_GUEST" : "ROLE_TRADER"),
        user.isVerified());
  }
}
