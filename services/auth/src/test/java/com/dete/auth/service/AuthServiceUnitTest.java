package com.dete.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.auth.client.AccountServiceClient;
import com.dete.auth.dto.AuthResponse;
import com.dete.auth.dto.GuestLoginRequest;
import com.dete.auth.dto.InitiateRegisterRequest;
import com.dete.auth.dto.LoginRequest;
import com.dete.auth.dto.LogoutRequest;
import com.dete.auth.dto.OtpResponse;
import com.dete.auth.dto.RegisterRequest;
import com.dete.auth.dto.TokenRefreshRequest;
import com.dete.auth.dto.UserResponse;
import com.dete.auth.dto.VerifyOtpRequest;
import com.dete.auth.exception.AuthenticationException;
import com.dete.auth.exception.DuplicateResourceException;
import com.dete.auth.model.EmailOtp;
import com.dete.auth.model.RefreshToken;
import com.dete.auth.model.User;
import com.dete.auth.repository.EmailOtpRepository;
import com.dete.auth.repository.RefreshTokenRepository;
import com.dete.auth.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
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
  @Mock private EmailOtpRepository emailOtpRepository;
  @Mock private EmailOtpService emailOtpService;
  @Mock private AccountServiceClient accountServiceClient;

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
            auditProducerService,
            emailOtpRepository,
            emailOtpService,
            accountServiceClient);
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
    verify(accountServiceClient).fundInitialAccount(any(UUID.class));
    verify(auditProducerService)
        .publishAuthAuditEvent(eq("USER_REGISTERED"), any(UUID.class), anyString());
  }

  @Test
  @DisplayName("Register: throws DuplicateResourceException if username exists")
  void shouldThrowWhenUsernameExists() {
    RegisterRequest request =
        new RegisterRequest("existing_user", "new@dete.io", "StrongSecretPassword123!");
    when(userRepository.existsByUsername("existing_user")).thenReturn(true);

    assertThatThrownBy(() -> authService.register(request))
        .isInstanceOf(DuplicateResourceException.class)
        .hasMessageContaining("Username 'existing_user' is already in use");

    verify(userRepository, never()).save(any(User.class));
  }

  @Test
  @DisplayName("InitiateRegister: generates OTP and dispatches verification email")
  void shouldInitiateRegistrationAndDispatchOtp() {
    InitiateRegisterRequest request =
        new InitiateRegisterRequest(
            "alice@example.com", "Alice Smith", LocalDate.of(1995, 6, 15), "SecretPassword123!");

    when(emailOtpService.isValidEmail("alice@example.com")).thenReturn(true);
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(passwordService.hash(anyString())).thenReturn("$2a$12$hashedpwd");
    when(emailOtpService.generateOtpCode()).thenReturn("789123");

    OtpResponse response = authService.initiateRegistration(request);

    assertThat(response.email()).isEqualTo("alice@example.com");
    assertThat(response.debugOtp()).isNull();
    verify(emailOtpRepository).save(any(EmailOtp.class));
    verify(emailOtpService).dispatchOtpEmail("alice@example.com", "789123", "Alice Smith");
  }

  @Test
  @DisplayName("InitiateRegister: rejects invalid email address")
  void shouldRejectInvalidEmailOnInitiate() {
    InitiateRegisterRequest request =
        new InitiateRegisterRequest(
            "not-an-email", "Alice Smith", LocalDate.of(1995, 6, 15), "SecretPassword123!");
    when(emailOtpService.isValidEmail("not-an-email")).thenReturn(false);

    assertThatThrownBy(() -> authService.initiateRegistration(request))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Invalid email format");
  }

  @Test
  @DisplayName("VerifyOtp: verifies OTP, saves user, pre-funds USDT and issues tokens")
  void shouldVerifyOtpRegisterUserAndPreFund() {
    VerifyOtpRequest request = new VerifyOtpRequest("alice@example.com", "789123");
    EmailOtp otp =
        EmailOtp.createNew(
            "alice@example.com",
            "789123",
            "Alice Smith",
            LocalDate.of(1995, 6, 15),
            "$2a$12$hashedpwd",
            600);

    when(emailOtpRepository.findValidOtp("alice@example.com", "789123"))
        .thenReturn(Optional.of(otp));
    when(userRepository.existsByEmail("alice@example.com")).thenReturn(false);
    when(userRepository.existsByUsername("alice")).thenReturn(false);
    when(jwtService.generateAccessToken(any(User.class))).thenReturn("access-token-123");
    when(jwtService.generateRefreshToken()).thenReturn("refresh-token-123");
    when(jwtService.hashToken(anyString())).thenReturn("hashed-refresh-token");
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

    AuthResponse response = authService.verifyOtpAndRegister(request, "127.0.0.1");

    assertThat(response.accessToken()).isEqualTo("access-token-123");
    assertThat(response.user()).isNotNull();
    assertThat(response.user().fullName()).isEqualTo("Alice Smith");
    assertThat(response.user().role()).isEqualTo("ROLE_TRADER");

    verify(emailOtpRepository).markConsumed(otp.otpId());
    verify(userRepository).save(any(User.class));
    verify(accountServiceClient).fundInitialAccount(any(UUID.class));
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  @DisplayName("VerifyOtp: rejects invalid or expired code")
  void shouldRejectInvalidOrExpiredOtp() {
    VerifyOtpRequest request = new VerifyOtpRequest("alice@example.com", "000000");
    when(emailOtpRepository.findValidOtp("alice@example.com", "000000"))
        .thenReturn(Optional.empty());

    assertThatThrownBy(() -> authService.verifyOtpAndRegister(request, "127.0.0.1"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessageContaining("Invalid or expired verification code");
  }

  @Test
  @DisplayName("GuestLogin: provisions guest user, pre-funds 50,000 USDT and issues tokens")
  void shouldLoginAsGuestAndPreFund() {
    GuestLoginRequest request = new GuestLoginRequest("speedy");
    when(jwtService.generateAccessToken(any(User.class))).thenReturn("guest-access-token");
    when(jwtService.generateRefreshToken()).thenReturn("guest-refresh-token");
    when(jwtService.hashToken(anyString())).thenReturn("hashed-guest-refresh");
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

    AuthResponse response = authService.loginAsGuest(request, "127.0.0.1");

    assertThat(response.accessToken()).isEqualTo("guest-access-token");
    assertThat(response.user()).isNotNull();
    assertThat(response.user().role()).isEqualTo("ROLE_GUEST");
    assertThat(response.user().isDemo()).isTrue();

    verify(userRepository).save(any(User.class));
    verify(accountServiceClient).fundInitialAccount(any(UUID.class));
    verify(refreshTokenRepository).save(any(RefreshToken.class));
  }

  @Test
  @DisplayName("Login: successfully issues tokens and records audit event")
  void shouldLoginSuccessfully() {
    LoginRequest request = new LoginRequest("trader_bob", "CorrectPassword123!");
    User user =
        new User(
            UUID.randomUUID(), "trader_bob", "bob@dete.io", "$2a$12$hashed", Instant.now(), false);

    when(userRepository.findByIdentifier("trader_bob")).thenReturn(Optional.of(user));
    when(passwordService.matches("CorrectPassword123!", "$2a$12$hashed")).thenReturn(true);
    when(jwtService.generateAccessToken(user)).thenReturn("mock.access.token");
    when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
    when(jwtService.hashToken("raw-refresh-token")).thenReturn("hash-of-refresh-token");
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

    AuthResponse response = authService.login(request, "192.168.1.100");

    assertThat(response.accessToken()).isEqualTo("mock.access.token");
    assertThat(response.refreshToken()).isEqualTo("raw-refresh-token");
    assertThat(response.expiresIn()).isEqualTo(900L);
    assertThat(response.tokenType()).isEqualTo("Bearer");
    assertThat(response.user()).isNotNull();

    verify(rateLimiterService).checkLoginRateLimit("192.168.1.100");
    verify(refreshTokenRepository).save(any(RefreshToken.class));
    verify(auditProducerService)
        .publishAuthAuditEvent(eq("USER_LOGIN"), eq(user.userId()), anyString());
  }

  @Test
  @DisplayName("Login: throws AuthenticationException if password does not match")
  void shouldThrowWhenPasswordMismatch() {
    LoginRequest request = new LoginRequest("trader_bob", "WrongPassword!");
    User user =
        new User(
            UUID.randomUUID(), "trader_bob", "bob@dete.io", "$2a$12$hashed", Instant.now(), false);

    when(userRepository.findByIdentifier("trader_bob")).thenReturn(Optional.of(user));
    when(passwordService.matches("WrongPassword!", "$2a$12$hashed")).thenReturn(false);

    assertThatThrownBy(() -> authService.login(request, "192.168.1.100"))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid username/email or password");

    verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
  }

  @Test
  @DisplayName("Refresh: rotates refresh token, revoking old and saving new")
  void shouldRotateRefreshToken() {
    UUID userId = UUID.randomUUID();
    UUID oldTokenId = UUID.randomUUID();
    TokenRefreshRequest request = new TokenRefreshRequest("valid-refresh-token");

    when(jwtService.hashToken("valid-refresh-token")).thenReturn("hashed-valid-token");

    RefreshToken storedToken =
        new RefreshToken(
            oldTokenId,
            userId,
            "hashed-valid-token",
            Instant.now().plusSeconds(3600),
            false,
            Instant.now());
    when(refreshTokenRepository.findByTokenHash("hashed-valid-token"))
        .thenReturn(Optional.of(storedToken));

    User user = new User(userId, "bob", "bob@dete.io", "hash", Instant.now(), false);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));
    when(jwtService.generateAccessToken(user)).thenReturn("new-access-token");
    when(jwtService.generateRefreshToken()).thenReturn("new-raw-refresh-token");
    when(jwtService.hashToken("new-raw-refresh-token")).thenReturn("new-hashed-token");
    when(jwtService.getRefreshTokenExpirationSeconds()).thenReturn(604800L);
    when(jwtService.getAccessTokenExpirationSeconds()).thenReturn(900L);

    AuthResponse response = authService.refresh(request);

    assertThat(response.accessToken()).isEqualTo("new-access-token");
    assertThat(response.refreshToken()).isEqualTo("new-raw-refresh-token");

    verify(refreshTokenRepository).revokeByTokenId(oldTokenId);
    verify(refreshTokenRepository).save(any(RefreshToken.class));
    verify(auditProducerService)
        .publishAuthAuditEvent(eq("TOKEN_REFRESHED"), eq(userId), anyString());
  }

  @Test
  @DisplayName("Refresh: rejects revoked refresh token and revokes all user sessions")
  void shouldRejectRevokedRefreshToken() {
    UUID userId = UUID.randomUUID();
    TokenRefreshRequest request = new TokenRefreshRequest("revoked-token");
    when(jwtService.hashToken("revoked-token")).thenReturn("hashed-revoked-token");

    RefreshToken revokedToken =
        new RefreshToken(
            UUID.randomUUID(),
            userId,
            "hashed-revoked-token",
            Instant.now().plusSeconds(3600),
            true, // revoked!
            Instant.now());
    when(refreshTokenRepository.findByTokenHash("hashed-revoked-token"))
        .thenReturn(Optional.of(revokedToken));

    assertThatThrownBy(() -> authService.refresh(request))
        .isInstanceOf(AuthenticationException.class)
        .hasMessage("Invalid, expired, or revoked refresh token");

    verify(refreshTokenRepository).revokeAllByUserId(userId);
    verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
  }

  @Test
  @DisplayName("Logout: revokes token by hash and publishes USER_LOGOUT audit event")
  void shouldLogoutAndRevokeToken() {
    UUID userId = UUID.randomUUID();
    UUID tokenId = UUID.randomUUID();
    LogoutRequest request = new LogoutRequest("active-token");

    when(jwtService.hashToken("active-token")).thenReturn("hashed-active-token");
    RefreshToken storedToken =
        new RefreshToken(
            tokenId,
            userId,
            "hashed-active-token",
            Instant.now().plusSeconds(3600),
            false,
            Instant.now());
    when(refreshTokenRepository.findByTokenHash("hashed-active-token"))
        .thenReturn(Optional.of(storedToken));

    authService.logout(request);

    verify(refreshTokenRepository).revokeByTokenId(tokenId);
    verify(auditProducerService).publishAuthAuditEvent(eq("USER_LOGOUT"), eq(userId), anyString());
  }

  @Test
  @DisplayName("GetMe: returns comprehensive user profile")
  void shouldReturnUserProfile() {
    UUID userId = UUID.randomUUID();
    User user =
        new User(
            userId,
            "alice",
            "alice@dete.io",
            "hash",
            Instant.now(),
            false,
            "Alice Wonderland",
            LocalDate.of(1992, 4, 10),
            "ROLE_TRADER",
            true);
    when(userRepository.findById(userId)).thenReturn(Optional.of(user));

    UserResponse response = authService.getMe(userId);

    assertThat(response.userId()).isEqualTo(userId);
    assertThat(response.fullName()).isEqualTo("Alice Wonderland");
    assertThat(response.dateOfBirth()).isEqualTo(LocalDate.of(1992, 4, 10));
    assertThat(response.role()).isEqualTo("ROLE_TRADER");
    assertThat(response.isVerified()).isTrue();
  }
}
