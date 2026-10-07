package com.dete.order.client;

import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.exception.InvalidOrderException;
import com.dete.order.exception.PreTradeRiskException;
import com.dete.order.exception.RiskServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.grpc.CallOptions;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.MethodDescriptor;
import io.grpc.stub.ClientCalls;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * gRPC client connecting to the Risk Service with Resilience4j circuit breaking. Adheres strictly
 * to fail-closed semantics: any failure or breaker trip rejects the order.
 */
@Component
@Primary
@ConditionalOnProperty(
    name = "order.risk.grpc.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GrpcRiskClient implements PreTradeRiskValidator {

  private static final Logger log = LoggerFactory.getLogger(GrpcRiskClient.class);

  private final ManagedChannel channel;
  private final MethodDescriptor<ValidateOrderRequest, ValidateOrderResponse> validateOrderMethod;

  @Autowired
  public GrpcRiskClient(
      @Value("${order.risk.grpc.host:localhost}") String host,
      @Value("${order.risk.grpc.port:9095}") int port,
      ObjectMapper objectMapper) {
    this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
    this.validateOrderMethod = createValidateOrderMethod(objectMapper);
  }

  // Constructor for testing with an externally provided channel
  public GrpcRiskClient(ManagedChannel channel, ObjectMapper objectMapper) {
    this.channel = channel;
    this.validateOrderMethod = createValidateOrderMethod(objectMapper);
  }

  @Override
  @CircuitBreaker(name = "riskService", fallbackMethod = "riskFallback")
  public void validateOrder(CreateOrderRequest request, UUID accountId) {
    if (request == null) {
      throw new InvalidOrderException("Order request cannot be null");
    }

    ValidateOrderRequest grpcRequest =
        new ValidateOrderRequest(
            UUID.randomUUID(),
            accountId,
            request.instrument(),
            request.side(),
            request.orderType(),
            request.price(),
            request.quantity());

    ValidateOrderResponse response;
    try {
      response =
          ClientCalls.blockingUnaryCall(
              channel,
              validateOrderMethod,
              CallOptions.DEFAULT.withDeadlineAfter(3, TimeUnit.SECONDS),
              grpcRequest);
    } catch (Exception e) {
      log.error(
          "Risk Service gRPC call failed for account {}. Failing closed: {}",
          accountId,
          e.getMessage());
      throw new PreTradeRiskException(
          "Pre-trade risk check unavailable (fail closed): " + e.getMessage(), e);
    }

    if (response == null || !response.approved()) {
      String reason =
          response != null ? response.rejectionReason() : "No response from Risk Service";
      log.warn("Risk Service rejected order for account {}: {}", accountId, reason);
      throw new PreTradeRiskException(reason);
    }

    log.debug("Risk Service approved order for account {} on {}", accountId, request.instrument());
  }

  /**
   * Fallback method executed when Risk Service throws an error or the circuit breaker is OPEN.
   * Strictly enforces the "Fail Closed" invariant (never bypass risk).
   */
  public void riskFallback(CreateOrderRequest request, UUID accountId, Throwable t) {
    if (t instanceof PreTradeRiskException pte) {
      throw pte;
    }
    if (t instanceof InvalidOrderException ioe) {
      throw ioe;
    }
    log.error(
        "Risk Service unavailable or circuit breaker OPEN for account {}. Failing closed: {}",
        accountId,
        t.getMessage());
    throw new RiskServiceUnavailableException(
        "RISK_SERVICE_UNAVAILABLE: Pre-trade risk check unavailable (fail closed): "
            + t.getMessage(),
        t);
  }

  @PreDestroy
  public void shutdown() {
    if (channel != null && !channel.isShutdown()) {
      try {
        channel.shutdown().awaitTermination(3, TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        channel.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }
  }

  private static MethodDescriptor<ValidateOrderRequest, ValidateOrderResponse>
      createValidateOrderMethod(ObjectMapper mapper) {
    return MethodDescriptor.<ValidateOrderRequest, ValidateOrderResponse>newBuilder()
        .setType(MethodDescriptor.MethodType.UNARY)
        .setFullMethodName(
            MethodDescriptor.generateFullMethodName(
                "com.dete.risk.proto.RiskService", "ValidateOrder"))
        .setSampledToLocalTracing(true)
        .setRequestMarshaller(new JsonMarshaller<>(ValidateOrderRequest.class, mapper))
        .setResponseMarshaller(new JsonMarshaller<>(ValidateOrderResponse.class, mapper))
        .build();
  }

  private static class JsonMarshaller<T> implements MethodDescriptor.Marshaller<T> {
    private final Class<T> targetClass;
    private final ObjectMapper objectMapper;

    public JsonMarshaller(Class<T> targetClass, ObjectMapper objectMapper) {
      this.targetClass = targetClass;
      this.objectMapper = objectMapper;
    }

    @Override
    public InputStream stream(T value) {
      try {
        return new ByteArrayInputStream(objectMapper.writeValueAsBytes(value));
      } catch (Exception e) {
        throw new RuntimeException("Failed to serialize gRPC message", e);
      }
    }

    @Override
    public T parse(InputStream stream) {
      try {
        return objectMapper.readValue(stream, targetClass);
      } catch (Exception e) {
        throw new RuntimeException("Failed to deserialize gRPC message", e);
      }
    }
  }
}
