package com.dete.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.auth.model.User;
import com.dete.common.security.JwtTokenValidator;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private JwtKeyProvider keyProvider;
  private JwtService jwtService;
  private JwtTokenValidator validator;

  @BeforeEach
  void setUp() {
    keyProvider = new JwtKeyProvider("test-key-id");
    jwtService = new JwtService(keyProvider, 900, 604800);
    validator = new JwtTokenValidator(keyProvider.getPublicKey());
  }

  @Test
  @DisplayName("Should generate valid RS256 JWT access token verifiable by JwtTokenValidator")
  void shouldGenerateAndValidateAccessToken() {
    UUID userId = UUID.randomUUID();
    User user = User.createNew("trader1", "trader1@dete.io", "hashedPass", false);

    String token = jwtService.generateAccessToken(user);

    assertThat(token).isNotBlank();
    assertThat(validator.isValid(token)).isTrue();

    Claims claims = validator.validateAndExtract(token);
    assertThat(validator.extractAccountId(claims)).isEqualTo(user.userId());
    assertThat(validator.extractUsername(claims)).isEqualTo("trader1");
    assertThat(validator.extractRoles(claims)).containsExactly("USER");
  }

  @Test
  @DisplayName("Should include DEMO role for demo users")
  void shouldIncludeDemoRoleForDemoUser() {
    User demoUser = User.createNew("demo", "demo@dete.io", "hashedPass", true);

    String token = jwtService.generateAccessToken(demoUser);

    Claims claims = validator.validateAndExtract(token);
    assertThat(validator.extractRoles(claims)).containsExactlyInAnyOrder("USER", "DEMO");
  }

  @Test
  @DisplayName("Should generate secure random refresh token and deterministic SHA-256 hash")
  void shouldGenerateRefreshTokenAndDeterministicHash() {
    String refreshToken1 = jwtService.generateRefreshToken();
    String refreshToken2 = jwtService.generateRefreshToken();

    assertThat(refreshToken1).isNotBlank().hasSizeGreaterThanOrEqualTo(40);
    assertThat(refreshToken2).isNotBlank().isNotEqualTo(refreshToken1);

    String hash1 = jwtService.hashToken(refreshToken1);
    String hash2 = jwtService.hashToken(refreshToken1);
    String hashDifferent = jwtService.hashToken(refreshToken2);

    assertThat(hash1).hasSize(64); // SHA-256 hex length
    assertThat(hash1).isEqualTo(hash2);
    assertThat(hash1).isNotEqualTo(hashDifferent);
  }

  @Test
  @DisplayName("Should provide JWKS with valid RSA parameters")
  void shouldProvideValidJwks() {
    var jwks = keyProvider.getJwksResponse();

    assertThat(jwks.keys()).hasSize(1);
    var keyMap = jwks.keys().get(0);
    assertThat(keyMap.get("kty")).isEqualTo("RSA");
    assertThat(keyMap.get("alg")).isEqualTo("RS256");
    assertThat(keyMap.get("kid")).isEqualTo("test-key-id");
    assertThat(keyMap.get("n")).isNotNull();
    assertThat(keyMap.get("e")).isNotNull();
  }
}
