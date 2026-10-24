package com.dete.matching.config;

import com.dete.matching.consumer.OrderCommandConsumer;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component
public class MatchingRecoveryHealth implements HealthIndicator {
  private final OrderCommandConsumer consumer;

  public MatchingRecoveryHealth(OrderCommandConsumer consumer) {
    this.consumer = consumer;
  }

  @Override
  public Health health() {
    return consumer.isLiveProcessingEnabled()
        ? Health.up().build()
        : Health.down().withDetail("recovery", "Order book recovery is incomplete").build();
  }
}
