package com.dete.order.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.test.FullStackTestBase;
import com.dete.order.dto.CreateOrderRequest;
import com.dete.order.exception.PreTradeRiskException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.grpc.BindableService;
import io.grpc.MethodDescriptor;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.ServerServiceDefinition;
import io.grpc.stub.ServerCalls;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "spring.main.allow-bean-definition-overriding=true",
      "order.risk.grpc.enabled=true",
      "order.outbox.poller-enabled=false",
      "resilience4j.circuitbreaker.instances.riskService.sliding-window-size=10",
      "resilience4j.circuitbreaker.instances.riskService.minimum-number-of-calls=5",
      "resilience4j.circuitbreaker.instances.riskService.failure-rate-threshold=50",
      "resilience4j.circuitbreaker.instances.riskService.wait-duration-in-open-state=5000ms"
    })
class OrderRiskCircuitBreakerTest extends FullStackTestBase {

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

  private static int mockGrpcPort;
  private static Server mockServer;
  private static final AtomicBoolean mockShouldApprove = new AtomicBoolean(true);
  private static final AtomicBoolean mockShouldFail = new AtomicBoolean(false);

  @Autowired private PreTradeRiskValidator grpcRiskClient;
  @Autowired private CircuitBreakerRegistry circuitBreakerRegistry;
  @MockBean private AccountClient accountClient;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    try {
      ObjectMapper mapper = new ObjectMapper();
      MethodDescriptor<ValidateOrderRequest, ValidateOrderResponse> method =
          MethodDescriptor.<ValidateOrderRequest, ValidateOrderResponse>newBuilder()
              .setType(MethodDescriptor.MethodType.UNARY)
              .setFullMethodName("com.dete.risk.proto.RiskService/ValidateOrder")
              .setRequestMarshaller(
                  new MethodDescriptor.Marshaller<>() {
                    @Override
                    public InputStream stream(ValidateOrderRequest value) {
                      try {
                        return new ByteArrayInputStream(mapper.writeValueAsBytes(value));
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }

                    @Override
                    public ValidateOrderRequest parse(InputStream stream) {
                      try {
                        return mapper.readValue(stream, ValidateOrderRequest.class);
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }
                  })
              .setResponseMarshaller(
                  new MethodDescriptor.Marshaller<>() {
                    @Override
                    public InputStream stream(ValidateOrderResponse value) {
                      try {
                        return new ByteArrayInputStream(mapper.writeValueAsBytes(value));
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }

                    @Override
                    public ValidateOrderResponse parse(InputStream stream) {
                      try {
                        return mapper.readValue(stream, ValidateOrderResponse.class);
                      } catch (Exception e) {
                        throw new RuntimeException(e);
                      }
                    }
                  })
              .build();

      BindableService mockService =
          () ->
              ServerServiceDefinition.builder("com.dete.risk.proto.RiskService")
                  .addMethod(
                      method,
                      ServerCalls.asyncUnaryCall(
                          (request, responseObserver) -> {
                            if (mockShouldFail.get()) {
                              responseObserver.onError(
                                  io.grpc.Status.UNAVAILABLE
                                      .withDescription("Risk service down")
                                      .asRuntimeException());
                            } else if (mockShouldApprove.get()) {
                              responseObserver.onNext(ValidateOrderResponse.approve());
                              responseObserver.onCompleted();
                            } else {
                              responseObserver.onNext(
                                  ValidateOrderResponse.reject("Simulated Risk Rejection"));
                              responseObserver.onCompleted();
                            }
                          }))
                  .build();

      mockServer = ServerBuilder.forPort(0).addService(mockService).build().start();
      mockGrpcPort = mockServer.getPort();

      registry.add("order.risk.grpc.host", () -> "127.0.0.1");
      registry.add("order.risk.grpc.port", () -> mockGrpcPort);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @BeforeEach
  void setUp() {
    mockShouldApprove.set(true);
    mockShouldFail.set(false);
    circuitBreakerRegistry.circuitBreaker("riskService").reset();
  }

  @AfterEach
  void tearDown() {
    mockShouldApprove.set(true);
    mockShouldFail.set(false);
  }

  @Test
  @DisplayName("When Risk Service approves order, validateOrder completes without exception")
  void testOrderApprovedByRiskService() {
    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    grpcRiskClient.validateOrder(request, UUID.randomUUID());
  }

  @Test
  @DisplayName("When Risk Service rejects order, validateOrder throws PreTradeRiskException")
  void testOrderRejectedByRiskService() {
    mockShouldApprove.set(false);

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    assertThatThrownBy(() -> grpcRiskClient.validateOrder(request, UUID.randomUUID()))
        .isInstanceOf(PreTradeRiskException.class)
        .hasMessageContaining("Simulated Risk Rejection");
  }

  @Test
  @DisplayName(
      "Fail-Closed & Circuit Breaker: When Risk Service is down, calls fail closed and circuit breaker transitions to OPEN")
  void testFailClosedAndCircuitBreakerTrip() {
    CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("riskService");
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

    // Simulate Risk Service returning UNAVAILABLE
    mockShouldFail.set(true);

    CreateOrderRequest request =
        new CreateOrderRequest(
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE);

    // Make 5 consecutive failing calls (minimum-number-of-calls is 5)
    for (int i = 0; i < 5; i++) {
      assertThatThrownBy(() -> grpcRiskClient.validateOrder(request, UUID.randomUUID()))
          .isInstanceOf(com.dete.order.exception.RiskServiceUnavailableException.class)
          .hasMessageContaining("RISK_SERVICE_UNAVAILABLE");
    }

    // Circuit breaker must now be OPEN
    assertThat(cb.getState()).isEqualTo(CircuitBreaker.State.OPEN);

    // Subsequent calls while OPEN fail closed immediately via circuit breaker
    assertThatThrownBy(() -> grpcRiskClient.validateOrder(request, UUID.randomUUID()))
        .isInstanceOf(com.dete.order.exception.RiskServiceUnavailableException.class)
        .hasMessageContaining("RISK_SERVICE_UNAVAILABLE");
  }
}
