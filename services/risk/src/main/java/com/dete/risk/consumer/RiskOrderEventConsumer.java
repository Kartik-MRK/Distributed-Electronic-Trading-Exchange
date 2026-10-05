package com.dete.risk.consumer;

import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderRejectedEvent;
import com.dete.risk.engine.RiskRuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes order terminal events from order.events to decrement open order counts and release
 * resting order prices.
 */
@Component
public class RiskOrderEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(RiskOrderEventConsumer.class);
  public static final String TOPIC_ORDER_EVENTS = "order.events";

  private final RiskRuleEvaluator riskRuleEvaluator;
  private final ObjectMapper objectMapper;

  public RiskOrderEventConsumer(RiskRuleEvaluator riskRuleEvaluator, ObjectMapper objectMapper) {
    this.riskRuleEvaluator = riskRuleEvaluator;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = TOPIC_ORDER_EVENTS,
      groupId = "${spring.kafka.consumer.group-id:risk-service-group}")
  public void onOrderEvent(ConsumerRecord<String, String> record) {
    try {
      String json = record.value();
      JsonNode node = objectMapper.readTree(json);

      String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
      UUID orderId = node.has("orderId") ? UUID.fromString(node.get("orderId").asText()) : null;
      UUID accountId =
          node.has("accountId") ? UUID.fromString(node.get("accountId").asText()) : null;

      boolean isTerminal =
          OrderFilledEvent.EVENT_TYPE.equals(eventType)
              || OrderCancelledEvent.EVENT_TYPE.equals(eventType)
              || OrderRejectedEvent.EVENT_TYPE.equals(eventType)
              || node.has("reason")
              || (node.has("remainingQuantity") && node.get("remainingQuantity").asLong() == 0L);

      if (isTerminal && orderId != null) {
        riskRuleEvaluator.onOrderClosed(accountId, orderId);
        log.debug("Processed terminal order event {} for account {}", orderId, accountId);
      }
    } catch (Exception e) {
      log.error("Failed to process order event in Risk Service", e);
    }
  }
}
