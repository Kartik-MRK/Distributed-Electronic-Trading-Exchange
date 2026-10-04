package com.dete.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.auth.dto.AuthResponse;
import com.dete.auth.dto.ErrorResponse;
import com.dete.auth.dto.JwksResponse;
import com.dete.auth.dto.LoginRequest;
import com.dete.auth.dto.LogoutRequest;
import com.dete.auth.dto.RegisterRequest;
import com.dete.auth.dto.TokenRefreshRequest;
import com.dete.auth.dto.UserResponse;
import com.dete.auth.service.JwtKeyProvider;
import com.dete.common.security.JwtTokenValidator;
import com.dete.common.test.FullStackTestBase;
import io.jsonwebtoken.Claims;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest extends FullStackTestBase {

  @Autowired private TestRestTemplate restTemplate;

  @Autowired private JwtKeyProvider jwtKeyProvider;

  @org.junit.jupiter.api.BeforeEach
  void setUp() {
    restTemplate
        .getRestTemplate()
        .setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());
  }

  @Test
  @Order(1)
  @DisplayName(
      "Full Auth Lifecycle: register -> duplicate -> login -> jwks -> me -> refresh -> token rotation -> logout")
  void testCompleteAuthLifecycle() {
    String username = "alice_" + UUID.randomUUID().toString().substring(0, 8);
    String email = username + "@dete.io";
    String password = "StrongTradingPassword123!";

    // 1. Register new user
    RegisterRequest registerRequest = new RegisterRequest(username, email, password);
    ResponseEntity<UserResponse> regResp =
        restTemplate.postForEntity("/auth/register", registerRequest, UserResponse.class);

    assertThat(regResp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    UserResponse registeredUser = regResp.getBody();
    assertThat(registeredUser).isNotNull();
    assertThat(registeredUser.userId()).isNotNull();
    assertThat(registeredUser.username()).isEqualTo(username);
    assertThat(registeredUser.email()).isEqualTo(email);
    assertThat(registeredUser.isDemo()).isFalse();

    // 2. Reject duplicate registration
    ResponseEntity<ErrorResponse> dupResp =
        restTemplate.postForEntity("/auth/register", registerRequest, ErrorResponse.class);
    assertThat(dupResp.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);

    // 3. Login with credentials
    LoginRequest loginRequest = new LoginRequest(username, password);
    ResponseEntity<AuthResponse> loginResp =
        restTemplate.postForEntity("/auth/login", loginRequest, AuthResponse.class);

    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    AuthResponse authData = loginResp.getBody();
    assertThat(authData).isNotNull();
    assertThat(authData.userId()).isEqualTo(registeredUser.userId());
    assertThat(authData.accessToken()).isNotBlank();
    assertThat(authData.refreshToken()).isNotBlank();
    assertThat(authData.tokenType()).isEqualTo("Bearer");

    // 4. Validate RS256 token against JWKS public key
    JwtTokenValidator validator = new JwtTokenValidator(jwtKeyProvider.getPublicKey());
    assertThat(validator.isValid(authData.accessToken())).isTrue();
    Claims claims = validator.validateAndExtract(authData.accessToken());
    assertThat(validator.extractAccountId(claims)).isEqualTo(registeredUser.userId());
    assertThat(validator.extractUsername(claims)).isEqualTo(username);
    assertThat(validator.extractRoles(claims)).contains("USER");

    // 5. Test JWKS endpoint
    ResponseEntity<JwksResponse> jwksResp =
        restTemplate.getForEntity("/auth/.well-known/jwks.json", JwksResponse.class);
    assertThat(jwksResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(jwksResp.getBody()).isNotNull();
    assertThat(jwksResp.getBody().keys()).isNotEmpty();

    // 6. Test GET /auth/me with Bearer token
    HttpHeaders authHeaders = new HttpHeaders();
    authHeaders.setBearerAuth(authData.accessToken());
    ResponseEntity<UserResponse> meResp =
        restTemplate.exchange(
            "/auth/me", HttpMethod.GET, new HttpEntity<>(authHeaders), UserResponse.class);

    assertThat(meResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(meResp.getBody()).isNotNull();
    assertThat(meResp.getBody().userId()).isEqualTo(registeredUser.userId());
    assertThat(meResp.getBody().username()).isEqualTo(username);

    // 7. Test GET /auth/me without token -> 401 Unauthorized
    ResponseEntity<String> unauthMeResp = restTemplate.getForEntity("/auth/me", String.class);
    assertThat(unauthMeResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 8. Refresh token
    TokenRefreshRequest refreshRequest = new TokenRefreshRequest(authData.refreshToken());
    ResponseEntity<AuthResponse> refreshResp =
        restTemplate.postForEntity("/auth/refresh", refreshRequest, AuthResponse.class);

    assertThat(refreshResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    AuthResponse refreshedAuth = refreshResp.getBody();
    assertThat(refreshedAuth).isNotNull();
    assertThat(refreshedAuth.accessToken()).isNotBlank();
    assertThat(refreshedAuth.refreshToken()).isNotBlank();
    assertThat(refreshedAuth.refreshToken()).isNotEqualTo(authData.refreshToken());

    // 9. Old refresh token must be rejected (Token Rotation)
    ResponseEntity<ErrorResponse> staleRefreshResp =
        restTemplate.postForEntity("/auth/refresh", refreshRequest, ErrorResponse.class);
    assertThat(staleRefreshResp.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 10. Logout with refreshed token
    LogoutRequest logoutRequest = new LogoutRequest(refreshedAuth.refreshToken());
    ResponseEntity<Void> logoutResp =
        restTemplate.postForEntity("/auth/logout", logoutRequest, Void.class);
    assertThat(logoutResp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

    // 11. Token used in logout can no longer refresh
    ResponseEntity<ErrorResponse> postLogoutRefresh =
        restTemplate.postForEntity(
            "/auth/refresh",
            new TokenRefreshRequest(refreshedAuth.refreshToken()),
            ErrorResponse.class);
    assertThat(postLogoutRefresh.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @Order(2)
  @DisplayName("Demo User Seeding: seeded demo user logs in and carries DEMO role")
  void testDemoUserSeeding() {
    LoginRequest loginRequest = new LoginRequest("demo", "DemoPassword123!");
    ResponseEntity<AuthResponse> loginResp =
        restTemplate.postForEntity("/auth/login", loginRequest, AuthResponse.class);

    assertThat(loginResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    AuthResponse authData = loginResp.getBody();
    assertThat(authData).isNotNull();

    JwtTokenValidator validator = new JwtTokenValidator(jwtKeyProvider.getPublicKey());
    Claims claims = validator.validateAndExtract(authData.accessToken());
    assertThat(validator.extractRoles(claims)).contains("USER", "DEMO");
  }

  @Test
  @Order(3)
  @DisplayName(
      "Rate Limiting: 5 failed attempts allowed, 6th attempt triggers HTTP 429 + Retry-After")
  void testLoginRateLimiting() {
    String testIp = "198.51.100.99";
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-Forwarded-For", testIp);
    LoginRequest badLogin = new LoginRequest("non_existent_user", "WrongPassword123!");

    // 5 attempts should return 401 Unauthorized
    for (int i = 1; i <= 5; i++) {
      ResponseEntity<ErrorResponse> resp =
          restTemplate.exchange(
              "/auth/login",
              HttpMethod.POST,
              new HttpEntity<>(badLogin, headers),
              ErrorResponse.class);
      assertThat(resp.getStatusCode())
          .as("Attempt %d should return 401", i)
          .isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    // 6th attempt should return 429 Too Many Requests
    ResponseEntity<ErrorResponse> rateLimitedResp =
        restTemplate.exchange(
            "/auth/login",
            HttpMethod.POST,
            new HttpEntity<>(badLogin, headers),
            ErrorResponse.class);

    assertThat(rateLimitedResp.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(rateLimitedResp.getHeaders().containsKey(HttpHeaders.RETRY_AFTER)).isTrue();
    String retryAfter = rateLimitedResp.getHeaders().getFirst(HttpHeaders.RETRY_AFTER);
    assertThat(retryAfter).isNotNull();
    assertThat(Long.parseLong(retryAfter)).isGreaterThan(0L);
  }
}
