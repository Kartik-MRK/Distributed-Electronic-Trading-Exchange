package com.dete.auth.service;

import com.dete.auth.model.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  private final JwtKeyProvider keyProvider;
  private final long accessTokenExpirationSeconds;
  private final long refreshTokenExpirationSeconds;
  private final SecureRandom secureRandom = new SecureRandom();

  public JwtService(
      JwtKeyProvider keyProvider,
      @Value("${auth.jwt.access-token-expiration-seconds:900}") long accessTokenExpirationSeconds,
      @Value("${auth.jwt.refresh-token-expiration-seconds:604800}")
          long refreshTokenExpirationSeconds) {
    this.keyProvider = keyProvider;
    this.accessTokenExpirationSeconds = accessTokenExpirationSeconds;
    this.refreshTokenExpirationSeconds = refreshTokenExpirationSeconds;
  }

  public String generateAccessToken(User user) {
    Instant now = Instant.now();
    Instant expiresAt = now.plusSeconds(accessTokenExpirationSeconds);

    List<String> roles = user.isDemo() ? List.of("USER", "DEMO") : List.of("USER");

    return Jwts.builder()
        .header()
        .keyId(keyProvider.getKeyId())
        .and()
        .subject(user.userId().toString())
        .claim("username", user.username())
        .claim("roles", roles)
        .issuedAt(Date.from(now))
        .expiration(Date.from(expiresAt))
        .signWith(keyProvider.getPrivateKey(), Jwts.SIG.RS256)
        .compact();
  }

  public String generateRefreshToken() {
    byte[] randomBytes = new byte[32];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  public String hashToken(String rawToken) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 algorithm not available", e);
    }
  }

  public Claims extractClaims(String token) {
    return Jwts.parser()
        .verifyWith(keyProvider.getPublicKey())
        .build()
        .parseSignedClaims(token)
        .getPayload();
  }

  public long getAccessTokenExpirationSeconds() {
    return accessTokenExpirationSeconds;
  }

  public long getRefreshTokenExpirationSeconds() {
    return refreshTokenExpirationSeconds;
  }
}
