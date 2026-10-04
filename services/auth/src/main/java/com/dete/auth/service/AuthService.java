package com.dete.auth.service;

import com.dete.auth.dto.AuthResponse;
import com.dete.auth.dto.LoginRequest;
import com.dete.auth.dto.LogoutRequest;
import com.dete.auth.dto.RegisterRequest;
import com.dete.auth.dto.TokenRefreshRequest;
import com.dete.auth.dto.UserResponse;
import com.dete.auth.exception.AuthenticationException;
import com.dete.auth.exception.DuplicateResourceException;
import com.dete.auth.exception.ResourceNotFoundException;
import com.dete.auth.model.RefreshToken;
import com.dete.auth.model.User;
import com.dete.auth.repository.RefreshTokenRepository;
import com.dete.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordService passwordService;
  private final JwtService jwtService;
  private final RateLimiterService rateLimiterService;
  private final AuditProducerService auditProducerService;

  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordService passwordService,
      JwtService jwtService,
      RateLimiterService rateLimiterService,
      AuditProducerService auditProducerService) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordService = passwordService;
    this.jwtService = jwtService;
    this.rateLimiterService = rateLimiterService;
    this.auditProducerService = auditProducerService;
  }

  @Transactional
  public UserResponse register(RegisterRequest request) {
    String username = request.username().trim();
    String email = request.email().trim().toLowerCase();

    if (userRepository.existsByUsername(username)) {
      throw new DuplicateResourceException("Username '" + username + "' is already in use");
    }

    if (userRepository.existsByEmail(email)) {
      throw new DuplicateResourceException("Email '" + email + "' is already in use");
    }

    String passwordHash = passwordService.hash(request.password());
    User user = User.createNew(username, email, passwordHash, false);
    userRepository.save(user);

    auditProducerService.publishAuthAuditEvent(
        "USER_REGISTERED", user.userId(), "{\"username\":\"" + user.username() + "\"}");

    return new UserResponse(
        user.userId(), user.username(), user.email(), user.createdAt(), user.isDemo());
  }

  @Transactional
  public AuthResponse login(LoginRequest request, String clientIp) {
    rateLimiterService.checkLoginRateLimit(clientIp);

    String identifier = request.identifier().trim();
    User user =
        userRepository
            .findByIdentifier(identifier)
            .orElseThrow(() -> new AuthenticationException("Invalid username/email or password"));

    if (!passwordService.matches(request.password(), user.passwordHash())) {
      throw new AuthenticationException("Invalid username/email or password");
    }

    String accessToken = jwtService.generateAccessToken(user);
    String rawRefreshToken = jwtService.generateRefreshToken();
    String tokenHash = jwtService.hashToken(rawRefreshToken);
    Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenExpirationSeconds());

    RefreshToken refreshToken = RefreshToken.createNew(user.userId(), tokenHash, expiresAt);
    refreshTokenRepository.save(refreshToken);

    auditProducerService.publishAuthAuditEvent(
        "USER_LOGIN", user.userId(), "{\"clientIp\":\"" + clientIp + "\"}");

    return AuthResponse.of(
        user.userId(),
        user.username(),
        accessToken,
        rawRefreshToken,
        jwtService.getAccessTokenExpirationSeconds());
  }

  @Transactional
  public AuthResponse refresh(TokenRefreshRequest request) {
    String tokenHash = jwtService.hashToken(request.refreshToken());
    RefreshToken storedToken =
        refreshTokenRepository
            .findByTokenHash(tokenHash)
            .orElseThrow(
                () -> new AuthenticationException("Invalid, expired, or revoked refresh token"));

    if (!storedToken.isValid()) {
      // If a revoked or expired token is presented, revoke all sessions for this user for security
      if (storedToken.revoked()) {
        refreshTokenRepository.revokeAllByUserId(storedToken.userId());
      }
      throw new AuthenticationException("Invalid, expired, or revoked refresh token");
    }

    // Token rotation: immediately revoke the old refresh token
    refreshTokenRepository.revokeByTokenId(storedToken.tokenId());

    User user =
        userRepository
            .findById(storedToken.userId())
            .orElseThrow(() -> new AuthenticationException("User account not found"));

    String newAccessToken = jwtService.generateAccessToken(user);
    String newRawRefreshToken = jwtService.generateRefreshToken();
    String newTokenHash = jwtService.hashToken(newRawRefreshToken);
    Instant newExpiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenExpirationSeconds());

    refreshTokenRepository.save(RefreshToken.createNew(user.userId(), newTokenHash, newExpiresAt));

    auditProducerService.publishAuthAuditEvent("TOKEN_REFRESHED", user.userId(), "{}");

    return AuthResponse.of(
        user.userId(),
        user.username(),
        newAccessToken,
        newRawRefreshToken,
        jwtService.getAccessTokenExpirationSeconds());
  }

  @Transactional
  public void logout(LogoutRequest request) {
    if (request != null && request.refreshToken() != null && !request.refreshToken().isBlank()) {
      String tokenHash = jwtService.hashToken(request.refreshToken());
      Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByTokenHash(tokenHash);
      if (tokenOpt.isPresent()) {
        RefreshToken token = tokenOpt.get();
        refreshTokenRepository.revokeByTokenId(token.tokenId());
        auditProducerService.publishAuthAuditEvent("USER_LOGOUT", token.userId(), "{}");
      }
    }
  }

  @Transactional(readOnly = true)
  public UserResponse getMe(UUID userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    return new UserResponse(
        user.userId(), user.username(), user.email(), user.createdAt(), user.isDemo());
  }
}
