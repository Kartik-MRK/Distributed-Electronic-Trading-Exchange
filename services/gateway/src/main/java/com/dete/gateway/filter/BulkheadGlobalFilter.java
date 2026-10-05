package com.dete.gateway.filter;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Bulkhead isolation filter enforcing strict concurrency partitions across downstream services.
 * Ensures that slow or saturating downstreams (e.g. order-service during peak spikes) cannot
 * exhaust thread pools or gateway connections, protecting account, auth, and market-data traffic.
 */
@Component
public class BulkheadGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(BulkheadGlobalFilter.class);

  public static final String SERVICE_ORDER = "orderService";
  public static final String SERVICE_ACCOUNT = "accountService";
  public static final String SERVICE_AUTH = "authService";
  public static final String SERVICE_MARKET_DATA = "marketDataService";
  public static final String SERVICE_AUDIT = "auditService";

  private final Map<String, Bulkhead> bulkheads = new ConcurrentHashMap<>();

  public BulkheadGlobalFilter(MeterRegistry meterRegistry) {
    BulkheadRegistry registry = BulkheadRegistry.ofDefaults();

    initBulkhead(registry, meterRegistry, SERVICE_ORDER, 50, 20);
    initBulkhead(registry, meterRegistry, SERVICE_ACCOUNT, 50, 20);
    initBulkhead(registry, meterRegistry, SERVICE_AUTH, 50, 20);
    initBulkhead(registry, meterRegistry, SERVICE_MARKET_DATA, 100, 20);
    initBulkhead(registry, meterRegistry, SERVICE_AUDIT, 30, 20);
  }

  private void initBulkhead(
      BulkheadRegistry registry,
      MeterRegistry meterRegistry,
      String name,
      int maxConcurrentCalls,
      long maxWaitDurationMs) {
    BulkheadConfig config =
        BulkheadConfig.custom()
            .maxConcurrentCalls(maxConcurrentCalls)
            .maxWaitDuration(Duration.ofMillis(maxWaitDurationMs))
            .build();
    Bulkhead bulkhead = registry.bulkhead(name, config);
    bulkheads.put(name, bulkhead);

    meterRegistry.gauge(
        "resilience4j.bulkhead.available.concurrent.calls",
        java.util.List.of(io.micrometer.core.instrument.Tag.of("name", name)),
        bulkhead,
        b -> b.getMetrics().getAvailableConcurrentCalls());

    meterRegistry.gauge(
        "resilience4j.bulkhead.max.allowed.concurrent.calls",
        java.util.List.of(io.micrometer.core.instrument.Tag.of("name", name)),
        bulkhead,
        b -> b.getMetrics().getMaxAllowedConcurrentCalls());
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getURI().getPath();

    if (path.startsWith("/actuator")
        || path.startsWith("/internal")
        || path.startsWith("/fallback")) {
      return chain.filter(exchange);
    }

    String serviceKey = resolveServiceKey(path);
    if (serviceKey == null) {
      return chain.filter(exchange);
    }

    Bulkhead bulkhead = bulkheads.get(serviceKey);
    if (bulkhead == null) {
      return chain.filter(exchange);
    }

    if (!bulkhead.tryAcquirePermission()) {
      log.warn(
          "Bulkhead capacity exhausted for service '{}'. Rejecting request to {}", serviceKey, path);
      exchange.getResponse().setStatusCode(HttpStatus.SERVICE_UNAVAILABLE);
      exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
      String body =
          String.format(
              "{\"status\":503,\"error\":\"BULKHEAD_LIMIT_EXCEEDED\",\"message\":\"Concurrent"
                  + " capacity limit exceeded for service %s\"}",
              serviceKey);
      byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
      return exchange
          .getResponse()
          .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    return chain
        .filter(exchange)
        .doFinally(
            signalType -> {
              bulkhead.onComplete();
            });
  }

  public Bulkhead getBulkhead(String serviceKey) {
    return bulkheads.get(serviceKey);
  }

  private String resolveServiceKey(String path) {
    if (path.startsWith("/orders") || path.startsWith("/admin/dlq")) {
      return SERVICE_ORDER;
    } else if (path.startsWith("/accounts")) {
      return SERVICE_ACCOUNT;
    } else if (path.startsWith("/auth")) {
      return SERVICE_AUTH;
    } else if (path.startsWith("/market-data") || path.startsWith("/ws")) {
      return SERVICE_MARKET_DATA;
    } else if (path.startsWith("/admin/audit")) {
      return SERVICE_AUDIT;
    }
    return null;
  }

  @Override
  public int getOrder() {
    // Run after JWT authentication and rate limiting, but before forwarding to downstream
    return 1;
  }
}
