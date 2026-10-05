package com.dete.simulator.dto;

import java.util.UUID;

public class AuthDtos {

  public record LoginRequest(String username, String password) {}

  public record RegisterRequest(String username, String email, String password) {}

  public record AuthResponse(
      UUID userId,
      String username,
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn) {}
}
