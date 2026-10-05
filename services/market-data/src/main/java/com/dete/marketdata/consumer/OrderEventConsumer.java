package com.dete.marketdata.consumer;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.events.order.OrderAcceptedEvent;
import com.dete.common.events.order.OrderCancelledEvent;
import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.common.events.order.OrderRejectedEvent;
import com.dete.marketdata.service.MarketDataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes order events and commands to maintain the read-side order book view and stream private
 * order updates to authenticated users.
 */
@Component
public class OrderEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

  public static final String TOPIC_ORDER_EVENTS = "order.events";
  public static final String TOPIC_ORDER_COMMANDS = "order.commands";

  private final MarketDataService marketDataService;
  private final ObjectMapper objectMapper;

  public OrderEventConsumer(MarketDataService marketDataService, ObjectMapper objectMapper) {
    this.marketDataService = marketDataService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = {TOPIC_ORDER_EVENTS, TOPIC_ORDER_COMMANDS},
      groupId = "${marketdata.consumer.order-group-id:market-data-order-group}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onOrderEvent(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      String payload = record.value();
      JsonNode node = objectMapper.readTree(payload);

      String eventType = node.has("eventType") ? node.get("eventType").asText() : "";

      if (OrderPlacedEvent.EVENT_TYPE.equals(eventType)
          || (node.has("orderId") && node.has("side") && node.has("quantity"))) {
        UUID orderId = UUID.fromString(node.get("orderId").asText());
        UUID accountId =
            node.has("accountId") ? UUID.fromString(node.get("accountId").asText()) : null;
        Instrument instrument =
            node.has("instrument") ? Instrument.valueOf(node.get("instrument").asText()) : null;
        OrderSide side = node.has("side") ? OrderSide.valueOf(node.get("side").asText()) : null;
        long price = node.has("price") ? node.get("price").asLong() : 0L;
        long quantity = node.has("quantity") ? node.get("quantity").asLong() : 0L;

        marketDataService.registerOrder(orderId, accountId, instrument, side, price, quantity);
        log.debug("Registered placed order: orderId={}, instrument={}", orderId, instrument);

      } else if (node.has("orderId")) {
        UUID orderId = UUID.fromString(node.get("orderId").asText());

        if (OrderAcceptedEvent.EVENT_TYPE.equals(eventType)
            || (node.has("sequenceNumber") && !node.has("fillQuantity"))) {
          long seq = node.has("sequenceNumber") ? node.get("sequenceNumber").asLong() : 0L;
          marketDataService.handleOrderAccepted(orderId, seq);

        } else if (OrderPartiallyFilledEvent.EVENT_TYPE.equals(eventType)
            || (node.has("fillQuantity") && node.has("remainingQuantity"))) {
          long filledQty = node.get("fillQuantity").asLong();
          long remainingQty = node.get("remainingQuantity").asLong();
          marketDataService.handleOrderPartiallyFilled(orderId, filledQty, remainingQty);

        } else if (OrderFilledEvent.EVENT_TYPE.equals(eventType) || node.has("fillQuantity")) {
          long filledQty = node.get("fillQuantity").asLong();
          marketDataService.handleOrderFilled(orderId, filledQty);

        } else if (OrderCancelledEvent.EVENT_TYPE.equals(eventType)
            || (node.has("remainingQuantity") && !node.has("fillQuantity"))) {
          long remainingQty = node.get("remainingQuantity").asLong();
          marketDataService.handleOrderCancelled(orderId, remainingQty);

        } else if (OrderRejectedEvent.EVENT_TYPE.equals(eventType) || node.has("reason")) {
          String reason = node.has("reason") ? node.get("reason").asText() : "Rejected";
          marketDataService.handleOrderRejected(orderId, reason);
        }
      }
    } catch (Exception e) {
      log.error("Failed to process order event: {}", record.value(), e);
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
