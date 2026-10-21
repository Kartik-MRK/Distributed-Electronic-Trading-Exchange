package com.dete.auth.dto;

import java.util.UUID;

public record AuthResponse(
    UUID userId,
    String username,
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresIn,
    UserResponse user) {

  public AuthResponse(
      UUID userId,
      String username,
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn) {
    this(userId, username, accessToken, refreshToken, tokenType, expiresIn, null);
  }

  public static AuthResponse of(
      UUID userId, String username, String accessToken, String refreshToken, long expiresIn) {
    return new AuthResponse(userId, username, accessToken, refreshToken, "Bearer", expiresIn, null);
  }

  public static AuthResponse of(
      UUID userId,
      String username,
      String accessToken,
      String refreshToken,
      long expiresIn,
      UserResponse user) {
    return new AuthResponse(userId, username, accessToken, refreshToken, "Bearer", expiresIn, user);
  }
}
