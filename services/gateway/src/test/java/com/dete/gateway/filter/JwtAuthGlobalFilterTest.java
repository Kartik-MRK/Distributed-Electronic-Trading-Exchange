package com.dete.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import com.dete.common.security.JwtTokenValidator;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class JwtAuthGlobalFilterTest {

  private static KeyPair keyPair;
  private static JwtTokenValidator validator;
  private static JwtAuthGlobalFilter filter;

  @BeforeAll
  static void setUp() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    keyPair = gen.generateKeyPair();
    validator = new JwtTokenValidator(keyPair.getPublic());
    filter = new JwtAuthGlobalFilter(validator);
  }

  private String generateToken(
      UUID accountId, String username, List<String> roles, long ttlMillis) {
    return Jwts.builder()
        .subject(accountId.toString())
        .claim("username", username)
        .claim("roles", roles)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ttlMillis))
        .signWith(keyPair.getPrivate())
        .compact();
  }

  @Test
  @DisplayName("Public path (/auth/login) passes without token")
  void testPublicPathPassesWithoutToken() {
    MockServerHttpRequest request = MockServerHttpRequest.post("/auth/login").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(chainCalled).isTrue();
  }

  @Test
  @DisplayName("Protected path (/orders) without token returns 401 Unauthorized")
  void testProtectedPathWithoutTokenReturns401() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/orders").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Protected path with expired token returns 401 Unauthorized")
  void testExpiredTokenReturns401() {
    String expiredToken =
        generateToken(UUID.randomUUID(), "trader_alice", List.of("TRADER"), -1000L);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
  }

  @Test
  @DisplayName("Admin path (/admin/audit) without ADMIN role returns 403 Forbidden")
  void testAdminPathWithoutAdminRoleReturns403() {
    String traderToken = generateToken(UUID.randomUUID(), "trader_bob", List.of("TRADER"), 60_000L);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/admin/audit")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + traderToken)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
  }

  @Test
  @DisplayName("Valid token injects X-User-Id, X-Account-Id, X-Username, X-User-Roles headers")
  void testValidTokenInjectsUserContextHeaders() {
    UUID accountId = UUID.randomUUID();
    String token = generateToken(accountId, "trader_carol", List.of("TRADER"), 60_000L);
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/orders")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          HttpHeaders headers = filterExchange.getRequest().getHeaders();
          assertThat(headers.getFirst("X-User-Id")).isEqualTo(accountId.toString());
          assertThat(headers.getFirst("X-Account-Id")).isEqualTo(accountId.toString());
          assertThat(headers.getFirst("X-Username")).isEqualTo("trader_carol");
          assertThat(headers.getFirst("X-User-Roles")).isEqualTo("TRADER");
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();
    assertThat(chainCalled).isTrue();
  }
}
