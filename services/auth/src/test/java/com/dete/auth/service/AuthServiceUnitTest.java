package com.dete.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.auth.dto.AuthResponse;
import com.dete.auth.dto.LoginRequest;
import com.dete.auth.dto.LogoutRequest;
import com.dete.auth.dto.RegisterRequest;
import com.dete.auth.dto.TokenRefreshRequest;
import com.dete.auth.dto.UserResponse;
import com.dete.auth.exception.AuthenticationException;
import com.dete.auth.exception.DuplicateResourceException;
import com.dete.auth.model.RefreshToken;
import com.dete.auth.model.User;
import com.dete.auth.repository.RefreshTokenRepository;
import com.dete.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceUnitTest {

  @Mock private UserRepository userRepository;
  @Mock private RefreshTokenRepository refreshTokenRepository;
  @Mock private PasswordService passwordService;
  @Mock private JwtService jwtService;
  @Mock private RateLimiterService rateLimiterService;
  @Mock private AuditProducerService auditProducerService;

  private AuthService authService;

  @BeforeEach
  void setUp() {
    authService =
        new AuthService(
            userRepository,
            refreshTokenRepository,
            passwordService,
            jwtService,
            rateLimiterService,
            auditProducerService);
  }

  @Test
  @DisplayName("Register: successfully registers user and publishes audit event")
  void shouldRegisterNewUser() {
    RegisterRequest request =
        new RegisterRequest("trader_bob", "bob@dete.io", "StrongSecretPassword123!");

    when(userRepository.existsByUsername("trader_bob")).thenReturn(false);
    when(userRepository.existsByEmail("bob@dete.io")).thenReturn(false);
    when(passwordService.hash(request.password())).thenReturn("$2a$12$hashed");
    when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

    UserResponse response = authService.register(request);

    assertThat(response.username()).isEqualTo("trader_bob");
    assertThat(response.email()).isEqualTo("bob@dete.io");
    assertThat(response.isDemo()).isFalse();

    verify(userRepository).save(any(User.class));
    verify(auditProducerService)
        .publishAuthAuditEvent(eq("USER_REGISTERED"), eq(response.userId()), any());
  }

  @Test
  @DisplayName("Register: throws DuplicateResourceException if username exists")
  void shouldRejectDuplicateUsernameOnRegister() {
    RegisterRequest request =
        new RegisterRequest("existing_user", "new@dete.io", "StrongSecretPassword123!");

    when(userRepository.existsByUsername("existing_user")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("Username 'existing_user' is already in use");

    verify(userRepository, never()).save(any());
  }

  @Test
  @DisplayName("Login: successfully issues tokens and records audit event")
  void shouldLoginSuccessfully() {
    LoginRequest request = new LoginRequest("trader_bob", "Secret123!");
    User user = User.createNew("trader_bob", "bob@dete.io", "$2a$12$hashed", false);

    when(userRepository.findByIdentifier("trader_bob")).thenReturn(Optional.of(user));
    when(passwordService.matches("Secret123!", "$2a$12$hashed")).thenReturn(true);
    when(jwtService.generateAccessToken(user)).thenReturn("mock.jwt.token");
    when(jwtService.generateRefreshToken()).thenReturn("mock-refresh-token");
    when(jwtService.hashToken("mock-refresh-token")).thenReturn("hash123");
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

    AuthResponse response = authService.login(request, "127.0.0.1");

    assertThat(response.userId()).isEqualTo(user.userId());
    assertThat(response.accessToken()).isEqualTo("mock.jwt.token");
    assertThat(response.refreshToken()).isEqualTo("mock-refresh-token");

    verify(rateLimiterService).checkLoginRateLimit("127.0.0.1");
    verify(refreshTokenRepository).save(any(RefreshToken.class));
    verify(auditProducerService).publishAuthAuditEvent(eq("USER_LOGIN"), eq(user.userId()), any());
  }

  @Test
  @DisplayName("Login: throws AuthenticationException if password does not match")
  void shouldRejectLoginWithWrongPassword() {
    LoginRequest request = new LoginRequest("trader_bob", "WrongPass");
    User user = User.createNew("trader_bob", "bob@dete.io", "$2a$12$hashed", false);

    when(userRepository.findByIdentifier("trader_bob")).thenReturn(Optional.of(user));
    when(passwordService.matches("WrongPass", "$2a$12$hashed")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(request, "127.0.0.1"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Invalid username/email or password");

    verify(refreshTokenRepository, never()).save(any());
  }

  @Test
  @DisplayName("Refresh: rotates refresh token, revoking old and saving new")
  void shouldRotateRefreshTokenSuccessfully() {
    TokenRefreshRequest request = new TokenRefreshRequest("old-raw-token");
    UUID userId = UUID.randomUUID();
    User user = new User(userId, "trader_bob", "bob@dete.io", "pass", Instant.now(), false);

    RefreshToken oldStored =
        new RefreshToken(
            UUID.randomUUID(),
            userId,
            "old-hash",
            Instant.now().plusSeconds(3600),
            false,
            Instant.now());

    when(jwtService.hashToken("old-raw-token")).thenReturn("old-hash");
    when(refreshTokenRepository.findByTokenHash("old-hash")).thenReturn(Optional.of(oldStored));
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtService.generateAccessToken(user)).thenReturn("new.access.token");
    when(jwtService.generateRefreshToken()).thenReturn("new-raw-token");
    when(jwtService.hashToken("new-raw-token")).thenReturn("new-hash");
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

    AuthResponse response = authService.refresh(request);

    assertThat(response.accessToken()).isEqualTo("new.access.token");
    assertThat(response.refreshToken()).isEqualTo("new-raw-token");

    // Old token must be revoked
    verify(refreshTokenRepository).revokeByTokenId(oldStored.tokenId());
    // New token must be saved
    verify(refreshTokenRepository).save(any(RefreshToken.class));
    verify(auditProducerService).publishAuthAuditEvent(eq("TOKEN_REFRESHED"), eq(userId), any());
  }

  @Test
  @DisplayName("Refresh: rejects revoked refresh token and revokes all user sessions")
  void shouldRejectRevokedRefreshToken() {
    TokenRefreshRequest request = new TokenRefreshRequest("revoked-token");
    UUID userId = UUID.randomUUID();

    RefreshToken revokedToken =
        new RefreshToken(
            UUID.randomUUID(),
            userId,
            "revoked-hash",
            Instant.now().plusSeconds(3600),
            true, // revoked!
            Instant.now());

    when(jwtService.hashToken("revoked-token")).thenReturn("revoked-hash");
    when(refreshTokenRepository.findByTokenHash("revoked-hash"))
        .thenReturn(Optional.of(revokedToken));

    assertThatThrownBy(() -> authService.refresh(request))
        .isInstanceOf(AuthenticationException.class);

    // Should revoke all sessions for this user due to potential token theft
    verify(refreshTokenRepository).revokeAllByUserId(userId);
  }

  @Test
  @DisplayName("Logout: revokes token by hash and publishes USER_LOGOUT audit event")
  void shouldLogoutAndRevokeToken() {
    LogoutRequest request = new LogoutRequest("active-token");
    UUID tokenId = UUID.randomUUID();
    UUID userId = UUID.randomUUID();
    RefreshToken token =
        new RefreshToken(
            tokenId, userId, "hash123", Instant.now().plusSeconds(3600), false, Instant.now());

    when(jwtService.hashToken("active-token")).thenReturn("hash123");
    when(refreshTokenRepository.findByTokenHash("hash123")).thenReturn(Optional.of(token));

    authService.logout(request);

    verify(refreshTokenRepository).revokeByTokenId(tokenId);
    verify(auditProducerService).publishAuthAuditEvent("USER_LOGOUT", userId, "{}");
  }
}
