package com.dete.gateway.ratelimit;

import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component("deteRedisRateLimiter")
public class DeteRedisRateLimiter {

  private static final Logger log = LoggerFactory.getLogger(DeteRedisRateLimiter.class);

  private static final String LUA_RATE_LIMIT =
      """
      local key = KEYS[1]
      local now = tonumber(ARGV[1])
      local window = tonumber(ARGV[2])
      local max_attempts = tonumber(ARGV[3])
      local member = ARGV[4]
      local ttl = tonumber(ARGV[5])

      local clear_before = now - window
      redis.call('ZREMRANGEBYSCORE', key, 0, clear_before)

      local count = redis.call('ZCARD', key)
      if count >= max_attempts then
          local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
          local oldest_time = tonumber(oldest[2])
          local retry_after = math.ceil((oldest_time + window - now) / 1000)
          if retry_after < 1 then retry_after = 1 end
          return {0, retry_after}
      else
          redis.call('ZADD', key, now, member)
          redis.call('EXPIRE', key, ttl)
          return {1, max_attempts - (count + 1)}
      end
      """;

  private final ReactiveStringRedisTemplate redisTemplate;
  private final DefaultRedisScript<List> redisScript;

  public DeteRedisRateLimiter(ReactiveStringRedisTemplate redisTemplate) {
    this.redisTemplate = redisTemplate;
    this.redisScript = new DefaultRedisScript<>();
    this.redisScript.setScriptText(LUA_RATE_LIMIT);
    this.redisScript.setResultType(List.class);
  }

  public record RateLimitResult(boolean allowed, long remainingOrRetryAfter) {}

  public Mono<RateLimitResult> isAllowed(String key, int maxAttempts, int windowSeconds) {
    long now = System.currentTimeMillis();
    long windowMillis = (long) windowSeconds * 1000;
    String member = UUID.randomUUID().toString();
    int ttlSeconds = windowSeconds + 10;

    return redisTemplate
        .execute(
            redisScript,
            List.of(key),
            List.of(
                String.valueOf(now),
                String.valueOf(windowMillis),
                String.valueOf(maxAttempts),
                member,
                String.valueOf(ttlSeconds)))
        .next()
        .map(
            res -> {
              if (res != null && !res.isEmpty()) {
                long allowedFlag = ((Number) res.get(0)).longValue();
                long val = res.size() > 1 ? ((Number) res.get(1)).longValue() : 0L;
                return new RateLimitResult(allowedFlag == 1L, val);
              }
              return new RateLimitResult(true, maxAttempts);
            })
        .onErrorResume(
            e -> {
              log.error(
                  "Redis rate limiting failed for key {}: {}. Failing open.", key, e.getMessage());
              return Mono.just(new RateLimitResult(true, maxAttempts));
            });
  }
}
