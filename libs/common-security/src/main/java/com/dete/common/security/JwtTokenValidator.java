package com.dete.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared JWT token validator used by all services except the Auth Service.
 *
 * <p>Uses RS256 (asymmetric). The Auth Service holds the private key and signs tokens. All other
 * services hold only the public key and verify with it.
 *
 * <p>The public key is distributed via environment variable or Kubernetes Secret.
 */
public class JwtTokenValidator {

  private static final Logger log = LoggerFactory.getLogger(JwtTokenValidator.class);

  private final PublicKey publicKey;

  public JwtTokenValidator(PublicKey publicKey) {
    this.publicKey = publicKey;
  }

  /**
   * Validate a raw JWT string and extract its claims.
   *
   * @param token the raw token (without "Bearer " prefix)
   * @return parsed claims if valid
   * @throws JwtException if the token is invalid, expired, or malformed
   */
  public Claims validateAndExtract(String token) {
    return Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();
  }

  /** Check if a token is valid without throwing. Use for quick guard checks. */
  public boolean isValid(String token) {
    try {
      Claims claims = validateAndExtract(token);
      return !claims.getExpiration().before(new Date());
    } catch (JwtException | IllegalArgumentException e) {
      log.debug("JWT validation failed: {}", e.getMessage());
      return false;
    }
  }

  /** Extract accountId (subject) from a validated token. */
  public UUID extractAccountId(Claims claims) {
    return UUID.fromString(claims.getSubject());
  }

  /** Extract username from a validated token. */
  public String extractUsername(Claims claims) {
    return claims.get("username", String.class);
  }

  /** Extract roles from a validated token. */
  @SuppressWarnings("unchecked")
  public List<String> extractRoles(Claims claims) {
    return claims.get("roles", List.class);
  }
}
