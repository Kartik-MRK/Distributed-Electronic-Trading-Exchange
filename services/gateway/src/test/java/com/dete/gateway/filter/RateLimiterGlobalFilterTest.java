package com.dete.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.gateway.ratelimit.DeteRedisRateLimiter;
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

class RateLimiterGlobalFilterTest {

  private DeteRedisRateLimiter redisRateLimiter;
  private RateLimiterGlobalFilter filter;

  @BeforeEach
  void setUp() {
    redisRateLimiter = mock(DeteRedisRateLimiter.class);
    filter = new RateLimiterGlobalFilter(redisRateLimiter);
  }

  @Test
  @DisplayName("Authenticated order placement uses key ratelimit:order:{userId} with limit 20")
  void testAuthenticatedOrderPlacementLimit() {
    String userId = "user-123";
    MockServerHttpRequest request = MockServerHttpRequest.post("/orders").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put(JwtAuthGlobalFilter.USER_ID_ATTR, userId);

    when(redisRateLimiter.isAllowed(eq("ratelimit:order:" + userId), eq(20), eq(60)))
        .thenReturn(Mono.just(new DeteRedisRateLimiter.RateLimitResult(true, 19)));

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isTrue();
    verify(redisRateLimiter).isAllowed("ratelimit:order:" + userId, 20, 60);
  }

  @Test
  @DisplayName("Authenticated general request uses key ratelimit:user:{userId} with limit 100")
  void testAuthenticatedGeneralLimit() {
    String userId = "user-456";
    MockServerHttpRequest request = MockServerHttpRequest.get("/accounts/me").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put(JwtAuthGlobalFilter.USER_ID_ATTR, userId);

    when(redisRateLimiter.isAllowed(eq("ratelimit:user:" + userId), eq(100), eq(60)))
        .thenReturn(Mono.just(new DeteRedisRateLimiter.RateLimitResult(true, 99)));

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isTrue();
    verify(redisRateLimiter).isAllowed("ratelimit:user:" + userId, 100, 60);
  }

  @Test
  @DisplayName("Unauthenticated request uses key ratelimit:ip:{clientIp} with limit 20")
  void testUnauthenticatedIpLimit() {
    MockServerHttpRequest request =
        MockServerHttpRequest.get("/market-data").header("X-Forwarded-For", "198.51.100.1").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    when(redisRateLimiter.isAllowed(eq("ratelimit:ip:198.51.100.1"), eq(20), eq(60)))
        .thenReturn(Mono.just(new DeteRedisRateLimiter.RateLimitResult(true, 19)));

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isTrue();
    verify(redisRateLimiter).isAllowed("ratelimit:ip:198.51.100.1", 20, 60);
  }

  @Test
  @DisplayName("Exceeding rate limit returns 429 Too Many Requests with Retry-After header")
  void testExceedingRateLimitReturns429() {
    String userId = "user-789";
    MockServerHttpRequest request = MockServerHttpRequest.post("/orders").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);
    exchange.getAttributes().put(JwtAuthGlobalFilter.USER_ID_ATTR, userId);

    when(redisRateLimiter.isAllowed(eq("ratelimit:order:" + userId), eq(20), eq(60)))
        .thenReturn(Mono.just(new DeteRedisRateLimiter.RateLimitResult(false, 45)));

    AtomicBoolean chainCalled = new AtomicBoolean(false);
    GatewayFilterChain chain =
        filterExchange -> {
          chainCalled.set(true);
          return Mono.empty();
        };

    StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

    assertThat(chainCalled).isFalse();
    assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    assertThat(exchange.getResponse().getHeaders().getFirst("Retry-After")).isEqualTo("45");
  }
}
