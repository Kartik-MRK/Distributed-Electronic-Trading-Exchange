package com.dete.order.service;

import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.order.client.AccountClient;
import com.dete.order.client.PreTradeRiskValidator;
import com.dete.order.dto.CancelOrderResponse;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.dto.ModifyOrderRequest;
import com.dete.order.dto.OrderResponse;
import com.dete.order.exception.InvalidOrderException;
import com.dete.order.exception.OrderNotCancellableException;
import com.dete.order.exception.OrderNotFoundException;
import com.dete.order.model.OrderRecord;
import com.dete.order.repository.OrderOutboxRepository;
import com.dete.order.repository.OrderRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

  private static final Logger log = LoggerFactory.getLogger(OrderService.class);
  public static final String TOPIC_ORDER_COMMANDS = "order.commands";

  private final OrderRepository orderRepository;
  private final OrderOutboxRepository outboxRepository;
  private final AccountClient accountClient;
  private final PreTradeRiskValidator riskValidator;
  private final IdempotencyService idempotencyService;
  private final ObjectMapper objectMapper;

  public OrderService(
      OrderRepository orderRepository,
      OrderOutboxRepository outboxRepository,
      AccountClient accountClient,
      PreTradeRiskValidator riskValidator,
      IdempotencyService idempotencyService,
      ObjectMapper objectMapper) {
    this.orderRepository = orderRepository;
    this.outboxRepository = outboxRepository;
    this.accountClient = accountClient;
    this.riskValidator = riskValidator;
    this.idempotencyService = idempotencyService;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public OrderResponse placeOrder(CreateOrderRequest request, UUID accountId, UUID idempotencyKey) {
    // 1. Idempotency check via Redis
    Optional<OrderResponse> cached = idempotencyService.getCachedOrder(idempotencyKey);
    if (cached.isPresent()) {
      log.info("Returning cached order response for idempotency key {}", idempotencyKey);
      return cached.get();
    }

    // 2. Backup idempotency check via DB
    Optional<OrderRecord> existingDbOrder = orderRepository.findByIdempotencyKey(idempotencyKey);
    if (existingDbOrder.isPresent()) {
      OrderResponse response = OrderResponse.fromRecord(existingDbOrder.get());
      idempotencyService.cacheOrder(idempotencyKey, response);
      return response;
    }

    // 3. Pre-trade Risk Validation
    riskValidator.validateOrder(request, accountId);

    // 4. Calculate required funds reservation
    UUID orderId = UUID.randomUUID();
    String reserveAsset;
    long reserveAmount;

    if (request.side() == OrderSide.BUY) {
      reserveAsset = request.instrument().quoteAsset();
      if (request.price() != null && request.price() > 0) {
        reserveAmount = FixedPoint.multiply(request.price(), request.quantity());
      } else {
        // Safeguard for market buy: if price not provided, require positive price or explicit
        // notional
        throw new InvalidOrderException(
            "Limit price is required for fund reservation on BUY order");
      }
    } else {
      reserveAsset = request.instrument().baseAsset();
      reserveAmount = request.quantity();
    }

    // 5. Synchronous balance reservation via Account Service
    accountClient.reserveFunds(accountId, orderId, reserveAsset, reserveAmount);

    // 6. Persist order in SUBMITTED status
    OrderRecord order =
        OrderRecord.createNew(
            orderId,
            accountId,
            request.instrument(),
            request.side(),
            request.orderType(),
            request.price(),
            request.quantity(),
            idempotencyKey);
    orderRepository.save(order);

    // 7. Write OrderPlacedEvent to outbox table
    OrderPlacedEvent event =
        OrderPlacedEvent.of(
            orderId,
            accountId,
            request.instrument(),
            request.side(),
            request.orderType(),
            request.price() != null ? request.price() : 0L,
            request.quantity(),
            idempotencyKey);

    try {
      String payload = objectMapper.writeValueAsString(event);
      outboxRepository.save(TOPIC_ORDER_COMMANDS, request.instrument().name(), payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize OrderPlacedEvent", e);
    }

    OrderResponse response = OrderResponse.fromRecord(order);
    idempotencyService.cacheOrder(idempotencyKey, response);

    log.info(
        "Successfully placed order {} for account {} on {} [side={}, qty={}]",
        orderId,
        accountId,
        request.instrument(),
        request.side(),
        request.quantity());
    return response;
  }

  @Transactional
  public CancelOrderResponse cancelOrder(UUID orderId, UUID accountId) {
    OrderRecord order =
        orderRepository
            .findByIdAndAccountId(orderId, accountId)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order " + orderId + " not found for account " + accountId));

    if (!order.isCancellable()) {
      throw new OrderNotCancellableException(
          String.format("Order %s in status %s cannot be cancelled", orderId, order.status()));
    }

    OrderCancelCommand command = OrderCancelCommand.of(orderId, accountId, order.instrument());
    try {
      String payload = objectMapper.writeValueAsString(command);
      outboxRepository.save(TOPIC_ORDER_COMMANDS, order.instrument().name(), payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize OrderCancelCommand", e);
    }

    log.info("Dispatched cancellation command for order {} on {}", orderId, order.instrument());
    return new CancelOrderResponse(
        orderId, "CANCELLATION_REQUESTED", "Cancellation command submitted to matching engine");
  }

  @Transactional
  public OrderResponse modifyOrder(UUID orderId, UUID accountId, ModifyOrderRequest request) {
    OrderRecord order =
        orderRepository
            .findByIdAndAccountId(orderId, accountId)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order " + orderId + " not found for account " + accountId));

    if (!order.isCancellable()) {
      throw new OrderNotCancellableException(
          String.format("Order %s in status %s cannot be modified", orderId, order.status()));
    }

    long newPrice = request.newPrice() != null ? request.newPrice() : order.price();
    long newQty = request.newQuantity() > 0 ? request.newQuantity() : order.originalQty();

    OrderModifyCommand command =
        OrderModifyCommand.of(orderId, accountId, order.instrument(), newPrice, newQty);
    try {
      String payload = objectMapper.writeValueAsString(command);
      outboxRepository.save(TOPIC_ORDER_COMMANDS, order.instrument().name(), payload);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize OrderModifyCommand", e);
    }

    log.info("Dispatched modification command for order {} on {}", orderId, order.instrument());
    return OrderResponse.fromRecord(order);
  }

  public OrderResponse getOrder(UUID orderId, UUID accountId) {
    OrderRecord order =
        orderRepository
            .findByIdAndAccountId(orderId, accountId)
            .orElseThrow(
                () ->
                    new OrderNotFoundException(
                        "Order " + orderId + " not found for account " + accountId));
    return OrderResponse.fromRecord(order);
  }

  public List<OrderResponse> getActiveOrders(UUID accountId) {
    return orderRepository.findActiveByAccountId(accountId).stream()
        .map(OrderResponse::fromRecord)
        .toList();
  }

  public List<OrderResponse> getOrderHistory(UUID accountId, int limit, int offset) {
    int safeLimit = Math.min(Math.max(limit, 1), 100);
    int safeOffset = Math.max(offset, 0);
    return orderRepository.findHistoryByAccountId(accountId, safeLimit, safeOffset).stream()
        .map(OrderResponse::fromRecord)
        .toList();
  }

  @Transactional
  public void handleOrderAccepted(UUID orderId) {
    orderRepository.updateStatusAndFills(orderId, OrderStatus.ACCEPTED, null, null, null);
    log.debug("Updated order {} status to ACCEPTED", orderId);
  }

  @Transactional
  public void handleOrderFilled(UUID orderId, long filledQty) {
    orderRepository
        .findById(orderId)
        .ifPresent(
            order -> {
              orderRepository.updateStatusAndFills(
                  orderId, OrderStatus.FILLED, order.originalQty(), 0L, null);
              log.debug("Updated order {} status to FILLED", orderId);
            });
  }

  @Transactional
  public void handleOrderPartiallyFilled(UUID orderId, long filledQty, long remainingQty) {
    orderRepository.updateStatusAndFills(
        orderId, OrderStatus.PARTIALLY_FILLED, filledQty, remainingQty, null);
    log.debug(
        "Updated order {} status to PARTIALLY_FILLED (filled={}, remaining={})",
        orderId,
        filledQty,
        remainingQty);
  }

  @Transactional
  public void handleOrderCancelled(UUID orderId) {
    orderRepository
        .findById(orderId)
        .ifPresent(
            order -> {
              if (order.status() == OrderStatus.CANCELLED) {
                return; // Idempotent
              }

              orderRepository.updateStatusAndFills(
                  orderId, OrderStatus.CANCELLED, null, null, null);
              log.info("Updated order {} status to CANCELLED", orderId);

              // Release remaining unspent funds
              releaseRemainingFunds(order);
            });
  }

  @Transactional
  public void handleOrderRejected(UUID orderId, String reason) {
    orderRepository
        .findById(orderId)
        .ifPresent(
            order -> {
              if (order.status() == OrderStatus.REJECTED) {
                return; // Idempotent
              }

              orderRepository.updateStatusAndFills(
                  orderId, OrderStatus.REJECTED, null, null, reason);
              log.warn("Updated order {} status to REJECTED (reason: {})", orderId, reason);

              // Release reserved funds
              releaseRemainingFunds(order);
            });
  }

  private void releaseRemainingFunds(OrderRecord order) {
    try {
      String releaseAsset;
      long releaseAmount;

      if (order.side() == OrderSide.BUY) {
        releaseAsset = order.instrument().quoteAsset();
        releaseAmount =
            order.price() != null && order.price() > 0
                ? FixedPoint.multiply(order.price(), order.remainingQty())
                : 0L;
      } else {
        releaseAsset = order.instrument().baseAsset();
        releaseAmount = order.remainingQty();
      }

      if (releaseAmount > 0) {
        accountClient.releaseFunds(order.accountId(), order.orderId(), releaseAsset, releaseAmount);
        log.info(
            "Released {} {} reserved funds for order {}",
            releaseAmount,
            releaseAsset,
            order.orderId());
      }
    } catch (Exception e) {
      log.error("Failed to release funds for order {}", order.orderId(), e);
    }
  }
}
