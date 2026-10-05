package com.dete.order.service;

import com.dete.order.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class IdempotencyService {

  private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);
  private static final String KEY_PREFIX = "idempotency:order:";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;

  public IdempotencyService(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      @Value("${order.idempotency.ttl-hours:24}") int ttlHours) {
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.ttl = Duration.ofHours(ttlHours);
  }

  public Optional<OrderResponse> getCachedOrder(UUID idempotencyKey) {
    if (idempotencyKey == null) {
      return Optional.empty();
    }
    try {
      String json = redisTemplate.opsForValue().get(KEY_PREFIX + idempotencyKey);
      if (json != null && !json.isBlank()) {
        OrderResponse cached = objectMapper.readValue(json, OrderResponse.class);
        return Optional.of(cached);
      }
    } catch (Exception e) {
      log.warn("Failed to check Redis idempotency cache for key {}", idempotencyKey, e);
    }
    return Optional.empty();
  }

  public void cacheOrder(UUID idempotencyKey, OrderResponse response) {
    if (idempotencyKey == null || response == null) {
      return;
    }
    try {
      String json = objectMapper.writeValueAsString(response);
      redisTemplate.opsForValue().set(KEY_PREFIX + idempotencyKey, json, ttl);
    } catch (Exception e) {
      log.warn("Failed to cache order response in Redis for key {}", idempotencyKey, e);
    }
  }
}
