package com.dete.simulator.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;

public class AuthDtos {

  public record LoginRequest(
      @JsonProperty("identifier") String username,
      String password) {}

  public record RegisterRequest(String username, String email, String password) {}

  public record AuthResponse(
      UUID userId,
      String username,
      String accessToken,
      String refreshToken,
      String tokenType,
      long expiresIn) {}
}
