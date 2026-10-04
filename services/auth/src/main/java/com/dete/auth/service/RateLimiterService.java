package com.dete.auth.service;

import com.dete.auth.exception.RateLimitExceededException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RateLimiterService {

  private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

  private final StringRedisTemplate redisTemplate;
  private final int maxAttempts;
  private final int windowSeconds;

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

  private final DefaultRedisScript<List> redisScript;

  public RateLimiterService(
      StringRedisTemplate redisTemplate,
      @Value("${auth.rate-limit.login-max-attempts:5}") int maxAttempts,
      @Value("${auth.rate-limit.login-window-seconds:60}") int windowSeconds) {
    this.redisTemplate = redisTemplate;
    this.maxAttempts = maxAttempts;
    this.windowSeconds = windowSeconds;

    this.redisScript = new DefaultRedisScript<>();
    this.redisScript.setScriptText(LUA_RATE_LIMIT);
    this.redisScript.setResultType(List.class);
  }

  /**
   * Checks whether the client IP has exceeded the login attempt rate limit.
   *
   * @param clientIp IP address of the client
   * @throws RateLimitExceededException if max attempts exceeded in the sliding window
   */
  @SuppressWarnings("unchecked")
  public void checkLoginRateLimit(String clientIp) {
    String key = "ratelimit:login:" + (clientIp != null ? clientIp : "unknown");
    long now = System.currentTimeMillis();
    long windowMillis = (long) windowSeconds * 1000;
    String member = UUID.randomUUID().toString();
    int ttlSeconds = windowSeconds + 10;

    try {
      List<Long> result =
          redisTemplate.execute(
              redisScript,
              List.of(key),
              String.valueOf(now),
              String.valueOf(windowMillis),
              String.valueOf(maxAttempts),
              member,
              String.valueOf(ttlSeconds));

      if (result != null && !result.isEmpty() && result.get(0) == 0L) {
        long retryAfter = result.size() > 1 ? result.get(1) : windowSeconds;
        log.warn("Rate limit breached for IP {}. Retry-After: {}s", clientIp, retryAfter);
        throw new RateLimitExceededException(
            "Too many login attempts. Please try again later.", retryAfter);
      }
    } catch (RateLimitExceededException e) {
      throw e;
    } catch (Exception e) {
      log.error("Redis rate limiter failed: {}. Allowing request as fallback.", e.getMessage());
      // Fail open if Redis is temporarily unreachable
    }
  }

  public void resetRateLimit(String clientIp) {
    String key = "ratelimit:login:" + (clientIp != null ? clientIp : "unknown");
    try {
      redisTemplate.delete(key);
    } catch (Exception e) {
      log.warn("Failed to reset rate limit key {}: {}", key, e.getMessage());
    }
  }
}
