package com.dete.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.order.client.AccountClient;
import com.dete.order.client.PreTradeRiskValidator;
import com.dete.order.dto.CancelOrderResponse;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.dto.ModifyOrderRequest;
import com.dete.order.dto.OrderResponse;
import com.dete.order.model.OrderRecord;
import com.dete.order.repository.OrderOutboxRepository;
import com.dete.order.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
class OrderServiceUnitTest {

  @Mock private OrderRepository orderRepository;
  @Mock private OrderOutboxRepository outboxRepository;
  @Mock private AccountClient accountClient;
  @Mock private PreTradeRiskValidator riskValidator;
  @Mock private IdempotencyService idempotencyService;

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
  private OrderService orderService;

  @BeforeEach
  void setUp() {
    orderService =
        new OrderService(
            orderRepository,
            outboxRepository,
            accountClient,
            riskValidator,
            idempotencyService,
            objectMapper);
  }

  @Test
  @DisplayName("Place LIMIT BUY Order: reserves quote funds, saves order and outbox message")
  void testPlaceLimitBuyOrder() {
    UUID accountId = UUID.randomUUID();
    UUID idempotencyKey = UUID.randomUUID();
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000L * FixedPoint.SCALE, // $50,000
            2L * FixedPoint.SCALE); // 2 BTC

    when(idempotencyService.getCachedOrder(idempotencyKey)).thenReturn(Optional.empty());
    when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

    OrderResponse response = orderService.placeOrder(request, accountId, idempotencyKey);

    assertThat(response).isNotNull();
    assertThat(response.accountId()).isEqualTo(accountId);
    assertThat(response.instrument()).isEqualTo(Instrument.BTC_USD);
    assertThat(response.status()).isEqualTo(OrderStatus.SUBMITTED);
    assertThat(response.originalQty()).isEqualTo(2L * FixedPoint.SCALE);
    assertThat(response.remainingQty()).isEqualTo(2L * FixedPoint.SCALE);

    // Verify fund reservation: 2 BTC * $50,000 = $100,000 USD
    long expectedNotional = 100_000L * FixedPoint.SCALE;
    verify(accountClient)
        .reserveFunds(eq(accountId), eq(response.orderId()), eq("USD"), eq(expectedNotional));

    // Verify order and outbox records persisted
    verify(orderRepository).save(any(OrderRecord.class));
    verify(outboxRepository).save(eq("order.commands"), eq("BTC_USD"), any(String.class));
    verify(idempotencyService).cacheOrder(eq(idempotencyKey), eq(response));
  }

  @Test
  @DisplayName("Place SELL Order: reserves base asset (BTC) quantity")
  void testPlaceSellOrder() {
    UUID accountId = UUID.randomUUID();
    UUID idempotencyKey = UUID.randomUUID();
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            3L * FixedPoint.SCALE);

    when(idempotencyService.getCachedOrder(idempotencyKey)).thenReturn(Optional.empty());
    when(orderRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());

    OrderResponse response = orderService.placeOrder(request, accountId, idempotencyKey);

    // Verify fund reservation: 3 BTC
    verify(accountClient)
        .reserveFunds(eq(accountId), eq(response.orderId()), eq("BTC"), eq(3L * FixedPoint.SCALE));
  }

  @Test
  @DisplayName("Duplicate request with cached idempotency key returns cached response immediately")
  void testDuplicateCachedIdempotencyKey() {
    UUID accountId = UUID.randomUUID();
    UUID idempotencyKey = UUID.randomUUID();
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    OrderRecord cachedRecord =
        OrderRecord.createNew(
            UUID.randomUUID(),
            accountId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE,
            idempotencyKey);

    OrderResponse cachedResponse = OrderResponse.fromRecord(cachedRecord);

    when(idempotencyService.getCachedOrder(idempotencyKey)).thenReturn(Optional.of(cachedResponse));

    OrderResponse response = orderService.placeOrder(request, accountId, idempotencyKey);

    assertThat(response).isEqualTo(cachedResponse);
    verify(orderRepository, never()).save(any());
    verify(accountClient, never()).reserveFunds(any(), any(), any(), any(Long.class));
  }

  @Test
  @DisplayName("Cancel order records outbox cancel command and returns CANCELLATION_REQUESTED")
  void testCancelOrder() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    OrderRecord existing =
        OrderRecord.createNew(
            orderId,
            accountId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE,
            UUID.randomUUID());

    when(orderRepository.findByIdAndAccountId(orderId, accountId))
        .thenReturn(Optional.of(existing));

    CancelOrderResponse cancelResp = orderService.cancelOrder(orderId, accountId);

    assertThat(cancelResp.orderId()).isEqualTo(orderId);
    assertThat(cancelResp.status()).isEqualTo("CANCELLATION_REQUESTED");

    verify(outboxRepository).save(eq("order.commands"), eq("BTC_USD"), any(String.class));
  }

  @Test
  @DisplayName("Modify order enqueues OrderModifyCommand to outbox")
  void testModifyOrder() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    OrderRecord existing =
        OrderRecord.createNew(
            orderId,
            accountId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000L * FixedPoint.SCALE,
            2L * FixedPoint.SCALE,
            UUID.randomUUID());

    when(orderRepository.findByIdAndAccountId(orderId, accountId))
        .thenReturn(Optional.of(existing));

    ModifyOrderRequest modifyReq =
        new ModifyOrderRequest(52_000L * FixedPoint.SCALE, 3L * FixedPoint.SCALE);

    OrderResponse modified = orderService.modifyOrder(orderId, accountId, modifyReq);

    assertThat(modified.orderId()).isEqualTo(orderId);
    verify(outboxRepository).save(eq("order.commands"), eq("BTC_USD"), any(String.class));
  }

  @Test
  @DisplayName(
      "handleOrderCancelled updates status to CANCELLED and releases remaining reserved funds")
  void testHandleOrderCancelled() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();

    OrderRecord resting =
        new OrderRecord(
            orderId,
            accountId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            50_000L * FixedPoint.SCALE,
            2L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE, // 1 BTC remaining
            1L * FixedPoint.SCALE, // 1 BTC filled
            OrderStatus.ACCEPTED,
            UUID.randomUUID(),
            null,
            Instant.now(),
            Instant.now());

    when(orderRepository.findById(orderId)).thenReturn(Optional.of(resting));

    orderService.handleOrderCancelled(orderId);

    verify(orderRepository).updateStatusAndFills(orderId, OrderStatus.CANCELLED, null, null, null);
    // Remaining 1 BTC at $50,000 = $50,000 USD to release
    long expectedRelease = 50_000L * FixedPoint.SCALE;
    verify(accountClient).releaseFunds(accountId, orderId, "USD", expectedRelease);
  }
}
