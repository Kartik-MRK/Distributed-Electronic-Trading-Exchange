package com.dete.gateway.filter;

import java.util.UUID;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.util.context.Context;

@Component
public class CorrelationIdGlobalFilter implements GlobalFilter, Ordered {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
  public static final String TRACE_ID_HEADER = "X-Trace-Id";

  @Override
  public int getOrder() {
    return -100;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    ServerHttpRequest request = exchange.getRequest();
    String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = request.getHeaders().getFirst(TRACE_ID_HEADER);
    }
    if (correlationId == null || correlationId.isBlank()) {
      correlationId = UUID.randomUUID().toString();
    }

    final String traceId = correlationId;

    // Mutate downstream request headers
    ServerHttpRequest mutatedRequest =
        request
            .mutate()
            .header(CORRELATION_ID_HEADER, traceId)
            .header(TRACE_ID_HEADER, traceId)
            .build();

    // Set response headers
    exchange.getResponse().getHeaders().set(CORRELATION_ID_HEADER, traceId);
    exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);

    ServerWebExchange mutatedExchange = exchange.mutate().request(mutatedRequest).build();

    return chain.filter(mutatedExchange).contextWrite(Context.of("correlationId", traceId));
  }
}
