package com.dete.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class BulkheadGlobalFilterTest {

  private BulkheadGlobalFilter filter;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    filter = new BulkheadGlobalFilter(meterRegistry);
  }

  @Test
  @DisplayName("Normal request within capacity acquires and releases permit successfully")
  void testNormalRequestAllowed() {
    MockServerHttpRequest request = MockServerHttpRequest.post("/orders").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isTrue();
    assertThat(exchange.getResponse().getStatusCode()).isNull();
  }

  @Test
  @DisplayName("Internal and actuator endpoints bypass bulkhead checks")
  void testInternalEndpointsBypassBulkhead() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/actuator/health").build();
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
  @DisplayName("Bulkhead saturation rejects excess calls with 503 BULKHEAD_LIMIT_EXCEEDED")
  void testBulkheadSaturationRejection() {
    Bulkhead orderBulkhead = filter.getBulkhead(BulkheadGlobalFilter.SERVICE_ORDER);
    assertThat(orderBulkhead).isNotNull();

    // Exhaust all 50 permits for orderService
    for (int i = 0; i < 50; i++) {
      boolean acquired = orderBulkhead.tryAcquirePermission();
      assertThat(acquired).isTrue();
    }

    // 51st call must be rejected
    MockServerHttpRequest request = MockServerHttpRequest.post("/orders").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);

    // Release permits
    for (int i = 0; i < 50; i++) {
      orderBulkhead.onComplete();
    }
  }

  @Test
  @DisplayName("Saturated order service does not degrade account service (bulkhead isolation)")
  void testBulkheadIsolationBetweenServices() {
    Bulkhead orderBulkhead = filter.getBulkhead(BulkheadGlobalFilter.SERVICE_ORDER);
    assertThat(orderBulkhead).isNotNull();

    // Saturate order bulkhead completely
    for (int i = 0; i < 50; i++) {
      orderBulkhead.tryAcquirePermission();
    }

    // Account service request must still succeed immediately!
    MockServerHttpRequest accountRequest = MockServerHttpRequest.get("/accounts/me/balances").build();
    MockServerWebExchange accountExchange = MockServerWebExchange.from(accountRequest);

    AtomicBoolean accountChainCalled = new AtomicBoolean(false);
    GatewayFilterChain accountChain =
        filterExchange -> {
          accountChainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(accountExchange, accountChain)).verifyComplete();

    assertThat(accountChainCalled).isTrue();
    assertThat(accountExchange.getResponse().getStatusCode()).isNull();

    // Clean up
    for (int i = 0; i < 50; i++) {
      orderBulkhead.onComplete();
    }
  }
}
