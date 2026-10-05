package com.dete.order.client;

import com.dete.order.dto.CreateOrderRequest;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.UUID;

public interface PreTradeRiskValidator {

  @CircuitBreaker(name = "riskService")
  void validateOrder(CreateOrderRequest request, UUID accountId);
}
