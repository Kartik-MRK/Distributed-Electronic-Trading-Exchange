package com.dete.matching.consumer;

import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.model.MatchResult;
import com.dete.matching.publisher.MatchingEventPublisher;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes incoming order commands from 'order.commands' and dispatches them to the
 * MatchingEngineService on the appropriate instrument's single-writer thread.
 */
@Component
public class OrderCommandConsumer {

  private static final Logger log = LoggerFactory.getLogger(OrderCommandConsumer.class);

  public static final String TOPIC_ORDER_COMMANDS = "order.commands";

  private final MatchingEngineService matchingEngineService;
  private final MatchingEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  private volatile boolean liveProcessingEnabled = false;
  private final java.util.Map<String, MatchResult> pendingPublications = new java.util.HashMap<>();

  public OrderCommandConsumer(
      MatchingEngineService matchingEngineService,
      MatchingEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.matchingEngineService = matchingEngineService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  public void setLiveProcessingEnabled(boolean enabled) {
    this.liveProcessingEnabled = enabled;
  }

  public boolean isLiveProcessingEnabled() {
    return liveProcessingEnabled;
  }

  public synchronized void clearPendingPublications() {
    pendingPublications.clear();
  }

  @KafkaListener(
      topics = TOPIC_ORDER_COMMANDS,
      groupId = "${matching.consumer.group-id:matching-engine-group}",
      containerFactory = "kafkaListenerContainerFactory")
  public synchronized void onOrderCommand(
      ConsumerRecord<String, String> record, Acknowledgment ack) {
    if (!liveProcessingEnabled) {
      throw new IllegalStateException("Live matching is paused; command must not be acknowledged");
    }

    String recordId = record.topic() + ":" + record.partition() + ":" + record.offset();
    try {
      MatchResult pending = pendingPublications.get(recordId);
      if (pending != null) {
        eventPublisher.publishMatchResult(pending);
        if (ack != null) ack.acknowledge();
        pendingPublications.remove(recordId);
        return;
      }
      String payload = record.value();
      JsonNode node = objectMapper.readTree(payload);

      // Determine command type from explicit eventType field or JSON shape
      String eventType = node.has("eventType") ? node.get("eventType").asText() : "";

      if (OrderModifyCommand.EVENT_TYPE.equals(eventType)
          || (node.has("newPrice") && node.has("newQuantity"))) {
        OrderModifyCommand command = objectMapper.treeToValue(node, OrderModifyCommand.class);
        log.debug("Consumed OrderModifyCommand for orderId: {}", command.orderId());
        MatchResult result = matchingEngineService.modifyOrder(command).join();
        pendingPublications.put(recordId, result);
        eventPublisher.publishMatchResult(result);
      } else if (OrderCancelCommand.EVENT_TYPE.equals(eventType)
          || (node.has("orderId")
              && !node.has("price")
              && !node.has("newPrice")
              && !node.has("side"))) {
        OrderCancelCommand command = objectMapper.treeToValue(node, OrderCancelCommand.class);
        log.debug("Consumed OrderCancelCommand for orderId: {}", command.orderId());
        MatchResult result = matchingEngineService.cancelOrder(command).join();
        pendingPublications.put(recordId, result);
        eventPublisher.publishMatchResult(result);
      } else {
        OrderPlacedEvent event = objectMapper.treeToValue(node, OrderPlacedEvent.class);
        log.debug(
            "Consumed OrderPlacedEvent for orderId: {}, instrument: {}",
            event.orderId(),
            event.instrument());
        MatchResult result = matchingEngineService.processOrder(event).join();
        pendingPublications.put(recordId, result);
        eventPublisher.publishMatchResult(result);
      }

      if (ack != null) {
        ack.acknowledge();
      }
      pendingPublications.remove(recordId);
    } catch (Exception e) {
      // A failure before a complete result is captured may have partially mutated the book.
      // Retrying it in-place could suppress its outputs through order-id deduplication.
      // Require recovery from the committed log before processing any more commands.
      if (!pendingPublications.containsKey(recordId)) liveProcessingEnabled = false;
      log.error(
          "Failed to process order command from partition {} offset {}",
          record.partition(),
          record.offset(),
          e);
      throw new IllegalStateException("Order command was not completed", e);
    }
  }
}
