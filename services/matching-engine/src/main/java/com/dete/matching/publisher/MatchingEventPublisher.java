package com.dete.matching.publisher;

import com.dete.common.events.order.OrderFilledEvent;
import com.dete.common.events.order.OrderPartiallyFilledEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.matching.engine.model.MatchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes trade executions and order lifecycle events to Kafka. Guarantees atomic dispatch of
 * trade and order events produced by match results.
 */
@Component
public class MatchingEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(MatchingEventPublisher.class);

  public static final String TOPIC_TRADE_EXECUTIONS = "trade.executions";
  public static final String TOPIC_ORDER_EVENTS = "order.events";

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Value("${matching.producer.sync:true}")
  private boolean synchronous = true;

  public MatchingEventPublisher(
      KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  /** Publishes all events contained in a MatchResult to Kafka. */
  public void publishMatchResult(MatchResult result) {
    if (result == null) {
      return;
    }

    try {
      // 1. Order Accepted
      if (result.acceptedEvent() != null) {
        publishOrderEvent(result.acceptedEvent().orderId().toString(), result.acceptedEvent());
      }

      // 2. Trades
      for (TradeExecutedEvent trade : result.trades()) {
        publishTrade(trade);
      }

      // 3. Fills and Partial Fills
      for (OrderFilledEvent filled : result.filledEvents()) {
        publishOrderEvent(filled.orderId().toString(), filled);
      }
      for (OrderPartiallyFilledEvent partial : result.partiallyFilledEvents()) {
        publishOrderEvent(partial.orderId().toString(), partial);
      }

      // 4. Cancellations
      if (result.cancelledEvent() != null) {
        publishOrderEvent(result.cancelledEvent().orderId().toString(), result.cancelledEvent());
      }

      // 5. Rejections
      if (result.rejectedEvent() != null) {
        publishOrderEvent(result.rejectedEvent().orderId().toString(), result.rejectedEvent());
      }
    } catch (Exception e) {
      log.error("Failed to publish matching engine events", e);
      throw new RuntimeException("Failed to publish matching events", e);
    }
  }

  private void publishTrade(TradeExecutedEvent trade) throws Exception {
    String payload = objectMapper.writeValueAsString(trade);
    String key = trade.instrument().symbol();
    if (synchronous) {
      kafkaTemplate.send(TOPIC_TRADE_EXECUTIONS, key, payload).get(5, TimeUnit.SECONDS);
    } else {
      kafkaTemplate.send(TOPIC_TRADE_EXECUTIONS, key, payload);
    }
    log.debug(
        "Published TradeExecutedEvent: tradeId={}, instrument={}, price={}, qty={}",
        trade.tradeId(),
        trade.instrument(),
        trade.price(),
        trade.quantity());
  }

  private void publishOrderEvent(String key, Object event) throws Exception {
    String payload = objectMapper.writeValueAsString(event);
    if (synchronous) {
      kafkaTemplate.send(TOPIC_ORDER_EVENTS, key, payload).get(5, TimeUnit.SECONDS);
    } else {
      kafkaTemplate.send(TOPIC_ORDER_EVENTS, key, payload);
    }
    log.debug("Published order event to {}: key={}", TOPIC_ORDER_EVENTS, key);
  }
}
