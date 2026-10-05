package com.dete.marketdata.consumer;

import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.marketdata.service.MarketDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes executed trades from 'trade.executions' topic. Appends to in-memory trade tape, updates
 * multi-interval OHLCV candles, updates last traded price, persists to database, and streams via
 * WebSocket.
 */
@Component
public class TradeExecutionConsumer {

  private static final Logger log = LoggerFactory.getLogger(TradeExecutionConsumer.class);
  public static final String TOPIC_TRADE_EXECUTIONS = "trade.executions";

  private final MarketDataService marketDataService;
  private final ObjectMapper objectMapper;

  public TradeExecutionConsumer(MarketDataService marketDataService, ObjectMapper objectMapper) {
    this.marketDataService = marketDataService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = TOPIC_TRADE_EXECUTIONS,
      groupId = "${marketdata.consumer.trade-group-id:market-data-trade-group}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onTradeExecuted(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      TradeExecutedEvent trade = objectMapper.readValue(record.value(), TradeExecutedEvent.class);
      marketDataService.handleTradeExecuted(trade);
      log.debug(
          "Processed TradeExecutedEvent: tradeId={}, instrument={}, price={}, qty={}",
          trade.tradeId(),
          trade.instrument(),
          trade.price(),
          trade.quantity());
    } catch (Exception e) {
      log.error("Failed to process trade execution message: {}", record.value(), e);
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
