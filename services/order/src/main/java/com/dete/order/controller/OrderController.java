package com.dete.order.controller;

import com.dete.order.dto.CancelOrderResponse;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.dto.ModifyOrderRequest;
import com.dete.order.dto.OrderResponse;
import com.dete.order.service.OrderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

  private final OrderService orderService;

  public OrderController(OrderService orderService) {
    this.orderService = orderService;
  }

  @PostMapping
  public ResponseEntity<OrderResponse> placeOrder(
      @AuthenticationPrincipal Object principal,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
      @Valid @RequestBody CreateOrderRequest request) {
    UUID accountId = resolveAccountId(principal);
    UUID idempotencyKey =
        idempotencyKeyHeader != null && !idempotencyKeyHeader.isBlank()
            ? UUID.fromString(idempotencyKeyHeader.trim())
            : UUID.randomUUID();

    OrderResponse response = orderService.placeOrder(request, accountId, idempotencyKey);
    return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
  }

  @DeleteMapping("/{orderId}")
  public ResponseEntity<CancelOrderResponse> cancelOrder(
      @AuthenticationPrincipal Object principal, @PathVariable UUID orderId) {
    UUID accountId = resolveAccountId(principal);
    CancelOrderResponse response = orderService.cancelOrder(orderId, accountId);
    return ResponseEntity.ok(response);
  }

  @PutMapping("/{orderId}")
  public ResponseEntity<OrderResponse> modifyOrder(
      @AuthenticationPrincipal Object principal,
      @PathVariable UUID orderId,
      @Valid @RequestBody ModifyOrderRequest request) {
    UUID accountId = resolveAccountId(principal);
    OrderResponse response = orderService.modifyOrder(orderId, accountId, request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{orderId}")
  public ResponseEntity<OrderResponse> getOrder(
      @AuthenticationPrincipal Object principal, @PathVariable UUID orderId) {
    UUID accountId = resolveAccountId(principal);
    OrderResponse response = orderService.getOrder(orderId, accountId);
    return ResponseEntity.ok(response);
  }

  @GetMapping
  public ResponseEntity<List<OrderResponse>> getOrders(
      @AuthenticationPrincipal Object principal,
      @RequestParam(value = "status", required = false) String status) {
    UUID accountId = resolveAccountId(principal);
    List<OrderResponse> orders = orderService.getActiveOrders(accountId);
    return ResponseEntity.ok(orders);
  }

  @GetMapping("/history")
  public ResponseEntity<List<OrderResponse>> getOrderHistory(
      @AuthenticationPrincipal Object principal,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    UUID accountId = resolveAccountId(principal);
    List<OrderResponse> history = orderService.getOrderHistory(accountId, limit, offset);
    return ResponseEntity.ok(history);
  }

  private UUID resolveAccountId(Object principal) {
    if (principal instanceof UUID u) {
      return u;
    }
    if (principal instanceof String s) {
      return UUID.fromString(s);
    }
    throw new IllegalArgumentException(
        "Unable to resolve authenticated account ID from principal: " + principal);
  }
}
