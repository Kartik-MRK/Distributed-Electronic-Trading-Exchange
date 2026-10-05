package com.dete.gateway.filter;

import com.dete.gateway.ratelimit.DeteRedisRateLimiter;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@Component
public class RateLimiterGlobalFilter implements GlobalFilter, Ordered {

  private static final Logger log = LoggerFactory.getLogger(RateLimiterGlobalFilter.class);

  private final DeteRedisRateLimiter redisRateLimiter;

  public RateLimiterGlobalFilter(DeteRedisRateLimiter redisRateLimiter) {
    this.redisRateLimiter = redisRateLimiter;
  }

  @Override
  public int getOrder() {
    return -20;
  }

  @Override
  public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
    String path = exchange.getRequest().getPath().value();
    if (path.startsWith("/actuator/")
        || path.startsWith("/fallback/")
        || path.startsWith("/internal/metrics/")
        || path.startsWith("/simulator/")) {
      return chain.filter(exchange);
    }

    String userId = exchange.getAttribute(JwtAuthGlobalFilter.USER_ID_ATTR);
    HttpMethod method = exchange.getRequest().getMethod();

    String key;
    int limit;
    int windowSeconds = 60;

    if (userId != null && !userId.isBlank()) {
      if (HttpMethod.POST.equals(method) && path.startsWith("/orders")) {
        // Authenticated order placement: 20 req/min
        key = "ratelimit:order:" + userId;
        limit = 20;
      } else {
        // Authenticated general: 100 req/min
        key = "ratelimit:user:" + userId;
        limit = 100;
      }
    } else {
      // Unauthenticated: 20 req/min per IP
      String clientIp = resolveClientIp(exchange);
      key = "ratelimit:ip:" + clientIp;
      limit = 20;
    }

    return redisRateLimiter
        .isAllowed(key, limit, windowSeconds)
        .flatMap(
            result -> {
              if (!result.allowed()) {
                long retryAfter = result.remainingOrRetryAfter();
                log.warn("Rate limit exceeded for key {}. Retry-After: {}s", key, retryAfter);
                return tooManyRequestsResponse(exchange, retryAfter);
              }
              return chain.filter(exchange);
            });
  }

  private String resolveClientIp(ServerWebExchange exchange) {
    String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
    if (xRealIp != null && !xRealIp.isBlank()) {
      return xRealIp.trim();
    }
    if (exchange.getRequest().getRemoteAddress() != null
        && exchange.getRequest().getRemoteAddress().getAddress() != null) {
      return exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
    }
    return "unknown";
  }

  private Mono<Void> tooManyRequestsResponse(ServerWebExchange exchange, long retryAfter) {
    ServerHttpResponse response = exchange.getResponse();
    response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
    response.getHeaders().set("Retry-After", String.valueOf(retryAfter));
    response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
    String body =
        String.format(
            "{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded. Please try again in %d seconds.\"}",
            retryAfter);
    DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
    return response.writeWith(Mono.just(buffer));
  }
}
