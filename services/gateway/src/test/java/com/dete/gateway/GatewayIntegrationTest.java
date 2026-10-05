package com.dete.gateway;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matching;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;

import com.dete.common.test.RedisTestContainerBase;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {"spring.main.allow-bean-definition-overriding=true"})
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GatewayIntegrationTest extends RedisTestContainerBase {

  private static final KeyPair TEST_KEY_PAIR;
  private static WireMockServer wireMock;

  static {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      TEST_KEY_PAIR = gen.generateKeyPair();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test RSA key pair", e);
    }
  }

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    if (wireMock != null) {
      wireMock.stop();
    }
  }

  @DynamicPropertySource
  static void registerWireMockProperties(DynamicPropertyRegistry registry) {
    registry.add("AUTH_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
    registry.add("ORDER_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
    registry.add("ACCOUNT_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
    registry.add("MARKET_DATA_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
    registry.add("AUDIT_SERVICE_URL", () -> "http://localhost:" + wireMock.port());
    registry.add("MARKET_DATA_WS_URL", () -> "ws://localhost:" + wireMock.port());
  }

  @TestConfiguration
  static class TestSecurityConfig {
    @Bean
    @Primary
    public PublicKey rsaPublicKey() {
      return TEST_KEY_PAIR.getPublic();
    }
  }

  @Autowired private WebTestClient webTestClient;

  private String generateToken(
      UUID accountId, String username, List<String> roles, long ttlMillis) {
    return Jwts.builder()
        .subject(accountId.toString())
        .claim("username", username)
        .claim("roles", roles)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ttlMillis))
        .signWith(TEST_KEY_PAIR.getPrivate())
        .compact();
  }

  @Test
  @Order(1)
  @DisplayName(
      "Phase 8.1: Public route correctly forwards to downstream and injects correlation ID")
  void testPublicRouteForwarding() {
    wireMock.stubFor(
        post(urlEqualTo("/auth/login"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"token\":\"test-jwt-token\"}")));

    webTestClient
        .post()
        .uri("/auth/login")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{\"username\":\"demo\",\"password\":\"DemoPassword123!\"}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueMatches("X-Correlation-Id", ".+")
        .expectHeader()
        .valueMatches("X-Trace-Id", ".+")
        .expectBody()
        .jsonPath("$.token")
        .isEqualTo("test-jwt-token");

    wireMock.verify(
        postRequestedFor(urlEqualTo("/auth/login"))
            .withHeader("X-Correlation-Id", matching(".+"))
            .withHeader("X-Trace-Id", matching(".+")));
  }

  @Test
  @Order(2)
  @DisplayName(
      "Phase 8.2: Protected route without token returns 401 Unauthorized (downstream unreached)")
  void testProtectedPathRejectsUnauthenticated() {
    wireMock.resetRequests();

    webTestClient
        .get()
        .uri("/orders")
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401)
        .jsonPath("$.error")
        .isEqualTo("Unauthorized");

    wireMock.verify(0, getRequestedFor(urlEqualTo("/orders")));
  }

  @Test
  @Order(3)
  @DisplayName("Phase 8.3: Protected route with expired token returns 401 Unauthorized")
  void testProtectedPathRejectsExpiredToken() {
    wireMock.resetRequests();
    String expiredToken =
        generateToken(UUID.randomUUID(), "expired_trader", List.of("TRADER"), -10_000L);

    webTestClient
        .get()
        .uri("/orders")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken)
        .exchange()
        .expectStatus()
        .isUnauthorized()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(401);

    wireMock.verify(0, getRequestedFor(urlEqualTo("/orders")));
  }

  @Test
  @Order(4)
  @DisplayName(
      "Phase 8.4: Valid JWT forwards request and injects X-User-Id, X-Account-Id, X-Username, X-User-Roles")
  void testValidJwtInjectsUserHeaders() {
    UUID accountId = UUID.randomUUID();
    String token = generateToken(accountId, "trader_alice", List.of("TRADER"), 60_000L);

    wireMock.stubFor(
        get(urlEqualTo("/orders"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("[]")));

    webTestClient
        .get()
        .uri("/orders")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .valueMatches("X-Correlation-Id", ".+")
        .expectBody()
        .json("[]");

    wireMock.verify(
        getRequestedFor(urlEqualTo("/orders"))
            .withHeader("X-User-Id", equalTo(accountId.toString()))
            .withHeader("X-Account-Id", equalTo(accountId.toString()))
            .withHeader("X-Username", equalTo("trader_alice"))
            .withHeader("X-User-Roles", equalTo("TRADER")));
  }

  @Test
  @Order(5)
  @DisplayName(
      "Phase 8.5: Admin route (/admin/audit) rejects non-admin with 403 Forbidden and permits ADMIN with 200")
  void testAdminRoleEnforcement() {
    wireMock.resetRequests();
    UUID accountId = UUID.randomUUID();
    String traderToken = generateToken(accountId, "trader_bob", List.of("TRADER"), 60_000L);
    String adminToken = generateToken(accountId, "super_admin", List.of("ADMIN"), 60_000L);

    // 1. Non-admin -> 403 Forbidden
    webTestClient
        .get()
        .uri("/admin/audit")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + traderToken)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(403);

    wireMock.verify(0, getRequestedFor(urlEqualTo("/admin/audit")));

    // 2. Admin -> 200 OK
    wireMock.stubFor(
        get(urlEqualTo("/admin/audit"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("[]")));

    webTestClient
        .get()
        .uri("/admin/audit")
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
        .exchange()
        .expectStatus()
        .isOk();

    wireMock.verify(1, getRequestedFor(urlEqualTo("/admin/audit")));
  }

  @Test
  @Order(6)
  @DisplayName(
      "Phase 8.6: Redis sliding-window rate limiting triggers 429 and Retry-After header on limit breach")
  void testRateLimitingWithRedis() {
    wireMock.stubFor(
        get(urlEqualTo("/market-data/test-rate-limit"))
            .willReturn(
                aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                    .withBody("{\"price\":\"50000\"}")));

    String testIp = "203.0.113." + (System.currentTimeMillis() % 250);

    // Send 20 allowed requests
    for (int i = 0; i < 20; i++) {
      webTestClient
          .get()
          .uri("/market-data/test-rate-limit")
          .header("X-Forwarded-For", testIp)
          .exchange()
          .expectStatus()
          .isOk();
    }

    // 21st request breaches the limit -> 429
    webTestClient
        .get()
        .uri("/market-data/test-rate-limit")
        .header("X-Forwarded-For", testIp)
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
        .expectHeader()
        .valueMatches("Retry-After", "[0-9]+")
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(429)
        .jsonPath("$.error")
        .isEqualTo("Too Many Requests");
  }

  @Test
  @Order(7)
  @DisplayName("Phase 8.7: Circuit breaker / Bulkhead fallback returns 503 on service failure")
  void testCircuitBreakerFallback() {
    webTestClient
        .get()
        .uri("/fallback/order-service")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(503)
        .jsonPath("$.message")
        .isEqualTo("ORDER_SERVICE_UNAVAILABLE");

    // Bulkhead isolation check: account service fallback is distinct
    webTestClient
        .get()
        .uri("/fallback/account-service")
        .exchange()
        .expectStatus()
        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
        .expectBody()
        .jsonPath("$.status")
        .isEqualTo(503)
        .jsonPath("$.message")
        .isEqualTo("ACCOUNT_SERVICE_UNAVAILABLE");
  }
}
