package com.dete.order.consumer;

import com.dete.common.events.order.OrderAcceptedEvent;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.events.order.OrderRejectedEvent;
import com.dete.order.service.OrderService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class OrderEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

  public static final String TOPIC_ORDER_EVENTS = "order.events";
  public static final String TOPIC_ORDER_EVENTS_DLQ = "order.events.DLQ";

  private final OrderService orderService;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  public OrderEventConsumer(
      OrderService orderService,
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper) {
    this.orderService = orderService;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = TOPIC_ORDER_EVENTS,
      groupId = "${spring.kafka.consumer.group-id:order-service-group}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onOrderEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      String payload = record.value();
      JsonNode node = objectMapper.readTree(payload);

      String eventType = node.has("eventType") ? node.get("eventType").asText() : "";
      UUID orderId = UUID.fromString(node.get("orderId").asText());

      if (OrderAcceptedEvent.EVENT_TYPE.equals(eventType) || node.has("sequenceNumber")) {
        orderService.handleOrderAccepted(orderId);
      } else if (OrderPartiallyFilledEvent.EVENT_TYPE.equals(eventType)
          || (node.has("fillQuantity") && node.has("remainingQuantity"))) {
        long filledQty = node.get("fillQuantity").asLong();
        long remainingQty = node.get("remainingQuantity").asLong();
        orderService.handleOrderPartiallyFilled(orderId, filledQty, remainingQty);
      } else if (OrderFilledEvent.EVENT_TYPE.equals(eventType) || node.has("fillQuantity")) {
        long filledQty = node.get("fillQuantity").asLong();
        orderService.handleOrderFilled(orderId, filledQty);
      } else if (OrderCancelledEvent.EVENT_TYPE.equals(eventType)
          || (node.has("remainingQuantity") && !node.has("fillQuantity"))) {
        orderService.handleOrderCancelled(orderId);
      } else if (OrderRejectedEvent.EVENT_TYPE.equals(eventType) || node.has("reason")) {
        String reason = node.has("reason") ? node.get("reason").asText() : "Rejected by engine";
        orderService.handleOrderRejected(orderId, reason);
      } else {
        log.warn("Unrecognized order event payload on topic {}: {}", TOPIC_ORDER_EVENTS, payload);
      }

      if (ack != null) {
        ack.acknowledge();
      }
    } catch (Exception e) {
      log.error(
          "Error processing order event from partition {} offset {}. Routing to DLQ...",
          record.partition(),
          record.offset(),
          e);
      routeToDlq(record, e.getMessage());
      if (ack != null) {
        ack.acknowledge(); // Prevent partition stalling
      }
    }
  }

  private void routeToDlq(ConsumerRecord<String, String> record, String errorMessage) {
    try {
      org.apache.kafka.clients.producer.ProducerRecord<String, String> dlqRecord =
          new org.apache.kafka.clients.producer.ProducerRecord<>(
              TOPIC_ORDER_EVENTS_DLQ, record.key(), record.value());
      dlqRecord.headers().add("X-Original-Topic", record.topic().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      dlqRecord.headers().add("X-Original-Partition", String.valueOf(record.partition()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      dlqRecord.headers().add("X-Original-Offset", String.valueOf(record.offset()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      dlqRecord.headers().add("X-Exception-Message", (errorMessage != null ? errorMessage : "unknown").getBytes(java.nio.charset.StandardCharsets.UTF_8));
      dlqRecord.headers().add("X-Failed-At", java.time.Instant.now().toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      dlqRecord.headers().add("X-Retry-Count", "3".getBytes(java.nio.charset.StandardCharsets.UTF_8));

      kafkaTemplate.send(dlqRecord);
      log.info(
          "Sent failed message to DLQ topic {} with metadata headers (offset={}, error={})",
          TOPIC_ORDER_EVENTS_DLQ,
          record.offset(),
          errorMessage);
    } catch (Exception dlqEx) {
      log.error("Fatal: failed to forward message to DLQ topic", dlqEx);
    }
  }
}
