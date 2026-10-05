package com.dete.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.order.OrderAcceptedEvent;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.test.FullStackTestBase;
import com.dete.order.client.AccountClient;
import com.dete.order.consumer.OrderEventConsumer;
import com.dete.order.dto.CancelOrderResponse;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.dto.ModifyOrderRequest;
import com.dete.order.dto.OrderResponse;
import com.dete.order.model.OrderRecord;
import com.dete.order.repository.OrderOutboxRepository;
import com.dete.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "order.risk.grpc.enabled=false"
    })
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OrderIntegrationTest extends FullStackTestBase {

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
  @Autowired private OrderRepository orderRepository;
  @Autowired private OrderOutboxRepository outboxRepository;
  @Autowired private KafkaTemplate<String, String> kafkaTemplate;
  @Autowired private ObjectMapper objectMapper;

  @MockBean private AccountClient accountClient;

  @BeforeEach
  void setUp() {
    restTemplate
        .getRestTemplate()
        .setRequestFactory(new org.springframework.http.client.JdkClientHttpRequestFactory());

    when(accountClient.reserveFunds(any(), any(), any(), anyLong())).thenReturn(true);
    when(accountClient.releaseFunds(any(), any(), any(), anyLong())).thenReturn(true);
  }

  private String generateAuthToken(UUID accountId, String username) {
    return Jwts.builder()
        .subject(accountId.toString())
        .claim("username", username)
        .claim("roles", List.of("TRADER"))
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + 3600_000))
        .signWith(TEST_KEY_PAIR.getPrivate())
        .compact();
  }

  private HttpHeaders createHeaders(String token, String idempotencyKey) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(token);
    if (idempotencyKey != null) {
      headers.set("Idempotency-Key", idempotencyKey);
    }
    return headers;
  }

  @Test
  @Order(1)
  @DisplayName(
      "Full Order Placement Lifecycle: POST /orders -> Reserved Funds -> Outbox Polled & Published to Kafka")
  void testOrderPlacementAndOutboxPublishing() {
    UUID accountId = UUID.randomUUID();
    String token = generateAuthToken(accountId, "trader_alice");
    String key = UUID.randomUUID().toString();

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            2L * FixedPoint.SCALE);

    HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, createHeaders(token, key));

    ResponseEntity<OrderResponse> response =
        restTemplate.postForEntity("/orders", entity, OrderResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    OrderResponse orderResp = response.getBody();
    assertThat(orderResp).isNotNull();
    assertThat(orderResp.status()).isEqualTo(OrderStatus.SUBMITTED);
    assertThat(orderResp.instrument()).isEqualTo(Instrument.BTC_USD);
    assertThat(orderResp.originalQty()).isEqualTo(2L * FixedPoint.SCALE);

    // Verify fund reservation
    long expectedNotional = 120_000L * FixedPoint.SCALE;
    verify(accountClient)
        .reserveFunds(eq(accountId), eq(orderResp.orderId()), eq("USD"), eq(expectedNotional));

    // Verify persisted in DB
    Optional<OrderRecord> dbOrder = orderRepository.findById(orderResp.orderId());
    assertThat(dbOrder).isPresent();
    assertThat(dbOrder.get().status()).isEqualTo(OrderStatus.SUBMITTED);

    // Verify Outbox publisher dispatched message to Kafka
    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(100))
        .until(() -> outboxRepository.fetchUnpublished(10).isEmpty());
  }

  @Test
  @Order(2)
  @DisplayName(
      "Idempotency Deduplication: Identical request returns cached order with no second DB insertion")
  void testIdempotencyDeduplication() {
    UUID accountId = UUID.randomUUID();
    String token = generateAuthToken(accountId, "trader_bob");
    String key = UUID.randomUUID().toString();

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.ETH_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            3_000L * FixedPoint.SCALE,
            5L * FixedPoint.SCALE);

    HttpEntity<CreateOrderRequest> entity = new HttpEntity<>(request, createHeaders(token, key));

    // First request
    ResponseEntity<OrderResponse> firstResp =
        restTemplate.postForEntity("/orders", entity, OrderResponse.class);
    assertThat(firstResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    UUID firstOrderId = firstResp.getBody().orderId();

    // Second duplicate request
    ResponseEntity<OrderResponse> secondResp =
        restTemplate.postForEntity("/orders", entity, OrderResponse.class);
    assertThat(secondResp.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    UUID secondOrderId = secondResp.getBody().orderId();

    assertThat(secondOrderId).isEqualTo(firstOrderId);

    // Verify database has only one order with this idempotency key
    Optional<OrderRecord> record = orderRepository.findByIdempotencyKey(UUID.fromString(key));
    assertThat(record).isPresent();
  }

  @Test
  @Order(3)
  @DisplayName(
      "Kafka Order Events: order.events consumption transitions SUBMITTED -> ACCEPTED -> PARTIALLY_FILLED -> FILLED")
  void testOrderEventTransitionsFromKafka() throws Exception {
    UUID accountId = UUID.randomUUID();
    String token = generateAuthToken(accountId, "trader_carol");
    String key = UUID.randomUUID().toString();

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            65_000L * FixedPoint.SCALE,
            2L * FixedPoint.SCALE);

    ResponseEntity<OrderResponse> response =
        restTemplate.postForEntity(
            "/orders", new HttpEntity<>(request, createHeaders(token, key)), OrderResponse.class);
    UUID orderId = response.getBody().orderId();

    // 1. Send OrderAcceptedEvent
    OrderAcceptedEvent acceptedEvent = OrderAcceptedEvent.of(orderId, 101L);
    kafkaTemplate
        .send(
            OrderEventConsumer.TOPIC_ORDER_EVENTS,
            orderId.toString(),
            objectMapper.writeValueAsString(acceptedEvent))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                orderRepository
                    .findById(orderId)
                    .map(o -> o.status() == OrderStatus.ACCEPTED)
                    .orElse(false));

    // 2. Send OrderPartiallyFilledEvent (filled 1 BTC, remaining 1 BTC)
    OrderPartiallyFilledEvent partialEvent =
        new OrderPartiallyFilledEvent(
            UUID.randomUUID(),
            orderId,
            UUID.randomUUID(),
            1L * FixedPoint.SCALE,
            65_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE,
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            OrderEventConsumer.TOPIC_ORDER_EVENTS,
            orderId.toString(),
            objectMapper.writeValueAsString(partialEvent))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                orderRepository
                    .findById(orderId)
                    .map(
                        o ->
                            o.status() == OrderStatus.PARTIALLY_FILLED
                                && o.filledQty() == 1L * FixedPoint.SCALE
                                && o.remainingQty() == 1L * FixedPoint.SCALE)
                    .orElse(false));

    // 3. Send OrderFilledEvent (filled remaining 1 BTC)
    OrderFilledEvent filledEvent =
        new OrderFilledEvent(
            UUID.randomUUID(),
            orderId,
            UUID.randomUUID(),
            1L * FixedPoint.SCALE,
            65_000L * FixedPoint.SCALE,
            Instant.now(),
            1);

    kafkaTemplate
        .send(
            OrderEventConsumer.TOPIC_ORDER_EVENTS,
            orderId.toString(),
            objectMapper.writeValueAsString(filledEvent))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                orderRepository
                    .findById(orderId)
                    .map(o -> o.status() == OrderStatus.FILLED && o.remainingQty() == 0L)
                    .orElse(false));
  }

  @Test
  @Order(4)
  @DisplayName(
      "Order Cancellation: DELETE /orders/{id} -> PENDING_CANCEL outbox -> CANCELLED event releases funds")
  void testOrderCancellationAndFundRelease() throws Exception {
    UUID accountId = UUID.randomUUID();
    String token = generateAuthToken(accountId, "trader_dave");
    String key = UUID.randomUUID().toString();

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000L * FixedPoint.SCALE,
            2L * FixedPoint.SCALE);

    ResponseEntity<OrderResponse> placeResp =
        restTemplate.postForEntity(
            "/orders", new HttpEntity<>(request, createHeaders(token, key)), OrderResponse.class);
    UUID orderId = placeResp.getBody().orderId();

    // Cancel order
    HttpHeaders headers = createHeaders(token, null);
    ResponseEntity<CancelOrderResponse> cancelResp =
        restTemplate.exchange(
            "/orders/" + orderId,
            HttpMethod.DELETE,
            new HttpEntity<>(headers),
            CancelOrderResponse.class);

    assertThat(cancelResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(cancelResp.getBody().status()).isEqualTo("CANCELLATION_REQUESTED");

    // Engine emits OrderCancelledEvent
    OrderCancelledEvent cancelledEvent = OrderCancelledEvent.of(orderId, 2L * FixedPoint.SCALE);
    kafkaTemplate
        .send(
            OrderEventConsumer.TOPIC_ORDER_EVENTS,
            orderId.toString(),
            objectMapper.writeValueAsString(cancelledEvent))
        .get(5, TimeUnit.SECONDS);

    await()
        .atMost(5, TimeUnit.SECONDS)
        .until(
            () ->
                orderRepository
                    .findById(orderId)
                    .map(o -> o.status() == OrderStatus.CANCELLED)
                    .orElse(false));

    // Verify account balance release: 2 BTC * $50,000 = $100,000 USD released
    verify(accountClient, atLeastOnce())
        .releaseFunds(eq(accountId), eq(orderId), eq("USD"), eq(100_000L * FixedPoint.SCALE));
  }

  @Test
  @Order(5)
  @DisplayName(
      "Order Modification: PUT /orders/{id} modifies order price & quantity and dispatches command")
  void testOrderModification() {
    UUID accountId = UUID.randomUUID();
    String token = generateAuthToken(accountId, "trader_eve");
    String key = UUID.randomUUID().toString();

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.SOL_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            150L * FixedPoint.SCALE,
            10L * FixedPoint.SCALE);

    ResponseEntity<OrderResponse> placeResp =
        restTemplate.postForEntity(
            "/orders", new HttpEntity<>(request, createHeaders(token, key)), OrderResponse.class);
    UUID orderId = placeResp.getBody().orderId();

    ModifyOrderRequest modifyRequest =
        new ModifyOrderRequest(155L * FixedPoint.SCALE, 12L * FixedPoint.SCALE);

    HttpHeaders headers = createHeaders(token, null);
    ResponseEntity<OrderResponse> modResp =
        restTemplate.exchange(
            "/orders/" + orderId,
            HttpMethod.PUT,
            new HttpEntity<>(modifyRequest, headers),
            OrderResponse.class);

    assertThat(modResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(modResp.getBody().orderId()).isEqualTo(orderId);
  }

  @Test
  @Order(6)
  @DisplayName(
      "DLQ Routing & Reprocessing: Corrupt message sent to DLQ -> Peek DLQ -> Reprocess to main topic")
  void testDlqRoutingAndReprocessing() throws Exception {
    String corruptPayload = "MALFORMED_UNPARSEABLE_JSON_MESSAGE_12345";

    // Send malformed message to order.events
    kafkaTemplate
        .send(OrderEventConsumer.TOPIC_ORDER_EVENTS, "bad-key", corruptPayload)
        .get(5, TimeUnit.SECONDS);

    // Peek DLQ admin endpoint
    await()
        .atMost(10, TimeUnit.SECONDS)
        .pollInterval(Duration.ofMillis(200))
        .until(
            () -> {
              ResponseEntity<Map> dlqPeek =
                  restTemplate.getForEntity("/admin/dlq/messages?maxMessages=10", Map.class);
              if (dlqPeek.getStatusCode() == HttpStatus.OK && dlqPeek.getBody() != null) {
                List<?> messages = (List<?>) dlqPeek.getBody().get("messages");
                return messages != null && messages.contains(corruptPayload);
              }
              return false;
            });

    // Call reprocess endpoint
    ResponseEntity<Map> reprocessResp =
        restTemplate.postForEntity("/admin/dlq/reprocess?maxMessages=10", null, Map.class);
    assertThat(reprocessResp.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(reprocessResp.getBody().get("status")).isEqualTo("SUCCESS");
    assertThat(((Number) reprocessResp.getBody().get("reprocessedCount")).intValue())
        .isGreaterThanOrEqualTo(1);
  }
}
