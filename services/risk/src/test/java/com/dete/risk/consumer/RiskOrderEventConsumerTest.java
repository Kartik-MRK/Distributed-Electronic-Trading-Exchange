package com.dete.risk.consumer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.risk.engine.RiskRuleEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;

class RiskOrderEventConsumerTest {
  @Test
  void cancellationWithUnfilledRemainderReleasesRiskSlot() throws Exception {
    UUID orderId = UUID.randomUUID();
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    RiskRuleEvaluator risk = mock(RiskRuleEvaluator.class);
    String payload = mapper.writeValueAsString(OrderCancelledEvent.of(orderId, 100L));
    new RiskOrderEventConsumer(risk, mapper)
        .onOrderEvent(new ConsumerRecord<>("order.events", 0, 0L, orderId.toString(), payload));
    verify(risk).onOrderClosed(null, orderId);
  }
}
