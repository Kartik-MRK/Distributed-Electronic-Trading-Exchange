package com.dete.auth.service;

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
import com.dete.auth.exception.ResourceNotFoundException;
import com.dete.auth.model.EmailOtp;
import com.dete.auth.model.RefreshToken;
import com.dete.auth.model.User;
import com.dete.auth.repository.EmailOtpRepository;
import com.dete.auth.repository.RefreshTokenRepository;
import com.dete.auth.repository.UserRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

  private static final Logger log = LoggerFactory.getLogger(AuthService.class);

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final PasswordService passwordService;
  private final JwtService jwtService;
  private final RateLimiterService rateLimiterService;
  private final AuditProducerService auditProducerService;
  private final EmailOtpRepository emailOtpRepository;
  private final EmailOtpService emailOtpService;
  private final AccountServiceClient accountServiceClient;

  @Autowired
  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordService passwordService,
      JwtService jwtService,
      RateLimiterService rateLimiterService,
      AuditProducerService auditProducerService,
      @Autowired(required = false) EmailOtpRepository emailOtpRepository,
      @Autowired(required = false) EmailOtpService emailOtpService,
      @Autowired(required = false) AccountServiceClient accountServiceClient) {
    this.userRepository = userRepository;
    this.refreshTokenRepository = refreshTokenRepository;
    this.passwordService = passwordService;
    this.jwtService = jwtService;
    this.rateLimiterService = rateLimiterService;
    this.auditProducerService = auditProducerService;
    this.emailOtpRepository = emailOtpRepository;
    this.emailOtpService = emailOtpService;
    this.accountServiceClient = accountServiceClient;
  }

  /** Backwards-compatible constructor for unit testing. */
  public AuthService(
      UserRepository userRepository,
      RefreshTokenRepository refreshTokenRepository,
      PasswordService passwordService,
      JwtService jwtService,
      RateLimiterService rateLimiterService,
      AuditProducerService auditProducerService) {
    this(
        userRepository,
        refreshTokenRepository,
        passwordService,
        jwtService,
        rateLimiterService,
        auditProducerService,
        null,
        null,
        null);
  }

  /** Phase 1 Step 1: Initiate sign-up with email OTP validation. */
  @Transactional
  public OtpResponse initiateRegistration(InitiateRegisterRequest request) {
    String email = request.email().trim().toLowerCase();

    if (emailOtpService != null && !emailOtpService.isValidEmail(email)) {
      throw new IllegalArgumentException("Invalid email format: '" + email + "'");
    }

    if (userRepository.existsByEmail(email)) {
      throw new DuplicateResourceException(
          "An account with email '" + email + "' already exists. Please sign in.");
    }

    String passwordHash = passwordService.hash(request.password());
    String otpCode = (emailOtpService != null) ? emailOtpService.generateOtpCode() : "123456";

    long expirySeconds = 600; // 10 minutes
    if (emailOtpRepository != null) {
      EmailOtp otp =
          EmailOtp.createNew(
              email,
              otpCode,
              request.fullName(),
              request.dateOfBirth(),
              passwordHash,
              expirySeconds);
      emailOtpRepository.save(otp);
    }

    if (emailOtpService != null) {
      emailOtpService.dispatchOtpEmail(email, otpCode, request.fullName());
    }

    auditProducerService.publishAuthAuditEvent(
        "OTP_GENERATED", UUID.randomUUID(), "{\"email\":\"" + email + "\"}");

    return OtpResponse.of(
        email, "Verification one-time passcode sent to " + email, expirySeconds);
  }

  /** Phase 1 Step 2: Verify OTP and create user with 50,000 USDT pre-funding. */
  @Transactional
  public AuthResponse verifyOtpAndRegister(VerifyOtpRequest request, String clientIp) {
    String email = request.email().trim().toLowerCase();
    String code = request.otp().trim();

    if (emailOtpRepository == null) {
      throw new IllegalStateException("Email OTP verification repository not initialized");
    }

    EmailOtp otp =
        emailOtpRepository
            .findValidOtp(email, code)
            .orElseThrow(
                () ->
                    new AuthenticationException(
                        "Invalid or expired verification code. Please request a new OTP."));

    emailOtpRepository.markConsumed(otp.otpId());

    if (userRepository.existsByEmail(email)) {
      throw new DuplicateResourceException(
          "An account with email '" + email + "' is already registered");
    }

    // Derive a unique username from email prefix
    String baseUsername = email.split("@")[0].replaceAll("[^a-zA-Z0-9_]", "_");
    if (baseUsername.length() > 40) {
      baseUsername = baseUsername.substring(0, 40);
    }
    String username = baseUsername;
    int suffix = 1;
    while (userRepository.existsByUsername(username)) {
      username = baseUsername + "_" + suffix++;
    }

    User user =
        User.createVerified(
            username, email, otp.passwordHash(), otp.fullName(), otp.dateOfBirth(), "ROLE_TRADER");
    userRepository.save(user);

    // Provision 50,000 USDT welcome liquidity
    if (accountServiceClient != null) {
      accountServiceClient.fundInitialAccount(user.userId());
    }

    String accessToken = jwtService.generateAccessToken(user);
    String rawRefreshToken = jwtService.generateRefreshToken();
    String tokenHash = jwtService.hashToken(rawRefreshToken);
    Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenExpirationSeconds());

    RefreshToken refreshToken = RefreshToken.createNew(user.userId(), tokenHash, expiresAt);
    refreshTokenRepository.save(refreshToken);

    auditProducerService.publishAuthAuditEvent(
        "USER_REGISTERED_VERIFIED",
        user.userId(),
        "{\"email\":\"" + email + "\",\"clientIp\":\"" + clientIp + "\"}");

    return AuthResponse.of(
        user.userId(),
        user.username(),
        accessToken,
        rawRefreshToken,
        jwtService.getAccessTokenExpirationSeconds(),
        UserResponse.fromUser(user));
  }

  /** Phase 1 Step 3: Instant 1-Click Guest Trader Login with 50,000 USDT. */
  @Transactional
  public AuthResponse loginAsGuest(GuestLoginRequest request, String clientIp) {
    if (clientIp != null && !clientIp.isBlank()) {
      rateLimiterService.checkLoginRateLimit(clientIp);
    }

    UUID guestId = UUID.randomUUID();
    String shortId = guestId.toString().substring(0, 8);
    String guestUsername =
        (request != null && request.nickname() != null && !request.nickname().isBlank())
            ? "guest_" + request.nickname().trim().replaceAll("[^a-zA-Z0-9_]", "_")
            : "guest_" + shortId;

    if (userRepository.existsByUsername(guestUsername)) {
      guestUsername = guestUsername + "_" + shortId;
    }
    String guestEmail = guestUsername + "@dete.guest";

    User guestUser = User.createGuest(guestId, guestUsername, guestEmail);
    userRepository.save(guestUser);

    // Provision 50,000 USDT initial funds
    if (accountServiceClient != null) {
      accountServiceClient.fundInitialAccount(guestUser.userId());
    }

    String accessToken = jwtService.generateAccessToken(guestUser);
    String rawRefreshToken = jwtService.generateRefreshToken();
    String tokenHash = jwtService.hashToken(rawRefreshToken);
    Instant expiresAt = Instant.now().plusSeconds(jwtService.getRefreshTokenExpirationSeconds());

    RefreshToken refreshToken = RefreshToken.createNew(guestUser.userId(), tokenHash, expiresAt);
    refreshTokenRepository.save(refreshToken);

    auditProducerService.publishAuthAuditEvent(
        "GUEST_LOGIN",
        guestUser.userId(),
        "{\"guestUsername\":\"" + guestUsername + "\",\"clientIp\":\"" + clientIp + "\"}");

    return AuthResponse.of(
        guestUser.userId(),
        guestUser.username(),
        accessToken,
        rawRefreshToken,
        jwtService.getAccessTokenExpirationSeconds(),
        UserResponse.fromUser(guestUser));
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

    if (accountServiceClient != null) {
      accountServiceClient.fundInitialAccount(user.userId());
    }

    auditProducerService.publishAuthAuditEvent(
        "USER_REGISTERED", user.userId(), "{\"username\":\"" + user.username() + "\"}");

    return UserResponse.fromUser(user);
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
        jwtService.getAccessTokenExpirationSeconds(),
        UserResponse.fromUser(user));
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
      if (storedToken.revoked()) {
        refreshTokenRepository.revokeAllByUserId(storedToken.userId());
      }
      throw new AuthenticationException("Invalid, expired, or revoked refresh token");
    }

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
        jwtService.getAccessTokenExpirationSeconds(),
        UserResponse.fromUser(user));
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
    return UserResponse.fromUser(user);
  }
}
