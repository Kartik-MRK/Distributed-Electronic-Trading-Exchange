package com.dete.audit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import com.dete.audit.model.AuditLogEntry;
import com.dete.audit.service.AuditService;
import com.dete.common.events.audit.AuditEvent;
import com.dete.common.test.FullStackTestBase;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "spring.kafka.consumer.auto-offset-reset=earliest"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuditIntegrationTest extends FullStackTestBase {

  private static final KeyPair TEST_KEY_PAIR;

  static {
    try {
      KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
      gen.initialize(2048);
      TEST_KEY_PAIR = gen.generateKeyPair();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate test RSA key pair", e);
    }
  }

  @TestConfiguration
  static class TestSecurityConfig {
    @Bean
    @Primary
    public PublicKey rsaPublicKey() {
      return TEST_KEY_PAIR.getPublic();
    }
  }

  @Autowired private TestRestTemplate restTemplate;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private AuditService auditService;
  @Autowired private JdbcTemplate jdbcTemplate;

  private String generateAuthToken(UUID accountId, String username, List<String> roles) {
    return Jwts.builder()
        .subject(accountId.toString())
        .claim("username", username)
        .claim("roles", roles)
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600_000))
        .signWith(TEST_KEY_PAIR.getPrivate())
        .compact();
  }

  private HttpHeaders createHeaders(String token) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    if (token != null) {
      headers.setBearerAuth(token);
    }
    return headers;
  }

  @Test
  @Order(1)
  @DisplayName("Phase 7.1: Kafka consumer ingests audit event into audit.audit_log")
  void testKafkaAuditIngestion() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();
    UUID actorId = UUID.randomUUID();
    Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

    AuditEvent event =
        new AuditEvent(
            eventId,
            "ORDER_FILLED",
            "ORDER",
            subjectId,
            actorId,
            "BTC_USDT",
            "{\"orderId\":\"" + subjectId + "\",\"fillPrice\":65000}",
            "trace-e2e-100",
            now,
            1);

    kafkaTemplate.send("audit.events", eventId.toString(), objectMapper.writeValueAsString(event));

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              var entryOpt = auditService.getEntryByEventId(eventId);
              assertThat(entryOpt).isPresent();
              AuditLogEntry entry = entryOpt.get();
              assertThat(entry.eventId()).isEqualTo(eventId);
              assertThat(entry.eventType()).isEqualTo("ORDER_FILLED");
              assertThat(entry.subjectType()).isEqualTo("ORDER");
              assertThat(entry.subjectId()).isEqualTo(subjectId);
              assertThat(entry.actorId()).isEqualTo(actorId);
              assertThat(entry.instrument()).isEqualTo("BTC_USDT");
              assertThat(entry.traceId()).isEqualTo("trace-e2e-100");
              assertThat(entry.payload()).contains("fillPrice");
            });
  }

  @Test
  @Order(2)
  @DisplayName("Phase 7.2: Duplicate events are idempotently ignored without error")
  void testIdempotencyDuplicateIgnored() throws Exception {
    UUID eventId = UUID.randomUUID();
    UUID subjectId = UUID.randomUUID();

    AuditEvent event =
        new AuditEvent(
            eventId,
            "USER_REGISTERED",
            "USER",
            subjectId,
            null,
            null,
            "{\"email\":\"trader@dete.exchange\"}",
            "trace-reg-1",
            Instant.now(),
            1);

    // Send first time
    kafkaTemplate.send("audit.events", eventId.toString(), objectMapper.writeValueAsString(event));

    await()
        .atMost(10, TimeUnit.SECONDS)
        .untilAsserted(() -> assertThat(auditService.getEntryByEventId(eventId)).isPresent());

    // Send duplicate
    kafkaTemplate.send("audit.events", eventId.toString(), objectMapper.writeValueAsString(event));

    // Wait slightly and verify count is still exactly 1
    Thread.sleep(1000);
    Integer count =
        jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_log WHERE event_id = ?", Integer.class, eventId);
    assertThat(count).isEqualTo(1);
  }

  @Test
  @Order(3)
  @DisplayName("Phase 7.3: Database trigger strictly prevents UPDATE and DELETE")
  void testDatabaseLevelImmutability() {
    UUID eventId = UUID.randomUUID();
    AuditEvent event =
        new AuditEvent(
            eventId,
            "BALANCE_RESERVED",
            "ACCOUNT",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "USDT",
            "{\"amount\":10000}",
            "trace-lock-99",
            Instant.now(),
            1);

    auditService.recordAuditEvent(event);
    assertThat(auditService.getEntryByEventId(eventId)).isPresent();

    // Verify UPDATE is blocked by trigger
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE audit.audit_log SET event_type = 'TAMPERED' WHERE event_id = ?",
                    eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("is append-only and immutable");

    // Verify DELETE is blocked by trigger
    assertThatThrownBy(
            () -> jdbcTemplate.update("DELETE FROM audit.audit_log WHERE event_id = ?", eventId))
        .isInstanceOf(DataAccessException.class)
        .hasMessageContaining("is append-only and immutable");

    // Verify record remains untouched
    var entry = auditService.getEntryByEventId(eventId).orElseThrow();
    assertThat(entry.eventType()).isEqualTo("BALANCE_RESERVED");
  }

  @Test
  @Order(4)
  @DisplayName("Phase 7.4: Security enforcement on /admin/audit endpoints")
  void testSecurityEnforcement() {
    // 1. Unauthenticated -> 401
    ResponseEntity<String> unauthResponse =
        restTemplate.exchange("/admin/audit", HttpMethod.GET, new HttpEntity<>(null), String.class);
    assertThat(unauthResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

    // 2. Non-admin (role TRADER) -> 403
    String traderToken = generateAuthToken(UUID.randomUUID(), "regular_trader", List.of("TRADER"));
    HttpHeaders traderHeaders = createHeaders(traderToken);
    ResponseEntity<String> forbiddenResponse =
        restTemplate.exchange(
            "/admin/audit", HttpMethod.GET, new HttpEntity<>(traderHeaders), String.class);
    assertThat(forbiddenResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

    // 3. Admin (role ADMIN) -> 200
    String adminToken = generateAuthToken(UUID.randomUUID(), "super_admin", List.of("ADMIN"));
    HttpHeaders adminHeaders = createHeaders(adminToken);
    ResponseEntity<List> okResponse =
        restTemplate.exchange(
            "/admin/audit", HttpMethod.GET, new HttpEntity<>(adminHeaders), List.class);
    assertThat(okResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
  }

  @Test
  @Order(5)
  @DisplayName(
      "Phase 7.5: Flexible query filtering (subjectId, instrument, eventType, traceId, time range)")
  void testQueryFiltering() {
    UUID targetSubject = UUID.randomUUID();
    String traceTarget = "trace-special-filter-xyz";
    Instant baseTime = Instant.now().minus(1, ChronoUnit.HOURS);

    AuditEvent e1 =
        new AuditEvent(
            UUID.randomUUID(),
            "ORDER_PLACED",
            "ORDER",
            targetSubject,
            UUID.randomUUID(),
            "ETH_USDT",
            "{\"qty\":10}",
            traceTarget,
            baseTime,
            1);

    AuditEvent e2 =
        new AuditEvent(
            UUID.randomUUID(),
            "ORDER_CANCELLED",
            "ORDER",
            targetSubject,
            UUID.randomUUID(),
            "ETH_USDT",
            "{\"qty\":10}",
            "trace-other",
            baseTime.plus(5, ChronoUnit.MINUTES),
            1);

    AuditEvent e3 =
        new AuditEvent(
            UUID.randomUUID(),
            "TRADE_EXECUTED",
            "TRADE",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "SOL_USDT",
            "{\"price\":150}",
            "trace-sol-1",
            baseTime.plus(10, ChronoUnit.MINUTES),
            1);

    auditService.recordAuditEvent(e1);
    auditService.recordAuditEvent(e2);
    auditService.recordAuditEvent(e3);

    String adminToken = generateAuthToken(UUID.randomUUID(), "admin_tester", List.of("ADMIN"));
    HttpHeaders headers = createHeaders(adminToken);

    // 1. Filter by subjectId
    ResponseEntity<List> resSubject =
        restTemplate.exchange(
            "/admin/audit?subjectId=" + targetSubject,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertThat(resSubject.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resSubject.getBody()).hasSize(2);

    // 2. Filter by instrument
    ResponseEntity<List> resInstrument =
        restTemplate.exchange(
            "/admin/audit?instrument=SOL_USDT",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertThat(resInstrument.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resInstrument.getBody()).hasSize(1);

    // 3. Filter by eventType
    ResponseEntity<List> resEventType =
        restTemplate.exchange(
            "/admin/audit?eventType=ORDER_PLACED",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertThat(resEventType.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resEventType.getBody()).isNotEmpty();

    // 4. Filter by traceId
    ResponseEntity<List> resTrace =
        restTemplate.exchange(
            "/admin/audit?traceId=" + traceTarget,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertThat(resTrace.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resTrace.getBody()).hasSize(1);

    // 5. Filter by time range
    Instant from = baseTime.minus(1, ChronoUnit.MINUTES);
    Instant to = baseTime.plus(6, ChronoUnit.MINUTES);
    ResponseEntity<List> resTime =
        restTemplate.exchange(
            "/admin/audit?subjectId=" + targetSubject + "&from=" + from + "&to=" + to,
            HttpMethod.GET,
            new HttpEntity<>(headers),
            List.class);
    assertThat(resTime.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resTime.getBody()).hasSize(2);

    // 6. Get single entry by eventId
    ResponseEntity<AuditLogEntry> resSingle =
        restTemplate.exchange(
            "/admin/audit/entries/" + e1.eventId(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            AuditLogEntry.class);
    assertThat(resSingle.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(resSingle.getBody()).isNotNull();
    assertThat(resSingle.getBody().eventId()).isEqualTo(e1.eventId());
    assertThat(resSingle.getBody().payload()).contains("qty");

    // 7. Get non-existent entry by eventId -> 404
    ResponseEntity<String> res404 =
        restTemplate.exchange(
            "/admin/audit/entries/" + UUID.randomUUID(),
            HttpMethod.GET,
            new HttpEntity<>(headers),
            String.class);
    assertThat(res404.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
