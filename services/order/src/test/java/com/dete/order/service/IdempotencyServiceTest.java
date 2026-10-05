package com.dete.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import com.dete.common.domain.types.FixedPoint;
import com.dete.order.dto.OrderResponse;
import com.dete.order.model.OrderRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

  @Mock private StringRedisTemplate redisTemplate;
  @Mock private ValueOperations<String, String> valueOperations;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
  private IdempotencyService idempotencyService;

  @BeforeEach
  void setUp() {
    idempotencyService = new IdempotencyService(redisTemplate, objectMapper, 24);
  }

  @Test
  @DisplayName("Returns empty optional when key is null or not found in Redis")
  void testGetCachedOrderNotFound() {
    assertThat(idempotencyService.getCachedOrder(null)).isEmpty();

    UUID key = UUID.randomUUID();
    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("idempotency:order:" + key)).thenReturn(null);

    assertThat(idempotencyService.getCachedOrder(key)).isEmpty();
  }

  @Test
  @DisplayName("Returns cached OrderResponse when found in Redis")
  void testGetCachedOrderFound() throws Exception {
    UUID key = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    OrderRecord record =
        OrderRecord.createNew(
            orderId,
            accountId,
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE,
            key);

    OrderResponse expected = OrderResponse.fromRecord(record);
    String json = objectMapper.writeValueAsString(expected);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    when(valueOperations.get("idempotency:order:" + key)).thenReturn(json);

    Optional<OrderResponse> result = idempotencyService.getCachedOrder(key);

    assertThat(result).isPresent();
    assertThat(result.get().orderId()).isEqualTo(orderId);
    assertThat(result.get().accountId()).isEqualTo(accountId);
  }

  @Test
  @DisplayName("Caches OrderResponse with 24-hour TTL in Redis")
  void testCacheOrder() {
    UUID key = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    UUID accountId = UUID.randomUUID();

    OrderRecord record =
        OrderRecord.createNew(
            orderId,
            accountId,
            Instrument.ETH_USD,
            OrderSide.SELL,
            OrderType.LIMIT,
            3_000L * FixedPoint.SCALE,
            10L * FixedPoint.SCALE,
            key);

    OrderResponse response = OrderResponse.fromRecord(record);

    when(redisTemplate.opsForValue()).thenReturn(valueOperations);

    idempotencyService.cacheOrder(key, response);

    verify(valueOperations)
        .set(eq("idempotency:order:" + key), any(String.class), eq(Duration.ofHours(24)));
  }
}
