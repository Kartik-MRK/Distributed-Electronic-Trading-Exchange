package com.dete.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class CorrelationIdGlobalFilterTest {

  private final CorrelationIdGlobalFilter filter = new CorrelationIdGlobalFilter();

  @Test
  @DisplayName("Generates new correlation ID when none is provided")
  void testGeneratesCorrelationId() {
    MockServerHttpRequest request = MockServerHttpRequest.get("/market-data").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          String corrId =
              filterExchange
                  .getRequest()
                  .getHeaders()
                  .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
          assertThat(corrId).isNotBlank();
          assertThat(filterExchange.getRequest().getHeaders().getFirst("X-Trace-Id"))
              .isEqualTo(corrId);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isTrue();
    String respHeader =
        exchange
            .getResponse()
            .getHeaders()
            .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER);
    assertThat(respHeader).isNotBlank();
  }

  @Test
  @DisplayName("Preserves existing correlation ID when provided in request")
  void testPreservesExistingCorrelationId() {
    String existingId = "client-trace-12345";
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/market-data")
            .header(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER, existingId)
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    GatewayFilterChain chain =
        filterExchange -> {
          assertThat(
                  filterExchange
                      .getRequest()
                      .getHeaders()
                      .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
              .isEqualTo(existingId);
          assertThat(filterExchange.getRequest().getHeaders().getFirst("X-Trace-Id"))
              .isEqualTo(existingId);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(
            exchange
                .getResponse()
                .getHeaders()
                .getFirst(CorrelationIdGlobalFilter.CORRELATION_ID_HEADER))
        .isEqualTo(existingId);
  }
}
