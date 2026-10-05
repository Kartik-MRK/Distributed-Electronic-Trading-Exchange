package com.dete.marketdata.consumer;

import com.dete.common.events.marketdata.OrderBookSnapshotEvent;
import com.dete.marketdata.service.MarketDataService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes periodic full order book snapshots from 'market-data' topic to resynchronize the
 * read-side L2 order book view with the Matching Engine.
 */
@Component
public class MarketDataSnapshotConsumer {

  private static final Logger log = LoggerFactory.getLogger(MarketDataSnapshotConsumer.class);
  public static final String TOPIC_MARKET_DATA = "market-data";

  private final MarketDataService marketDataService;
  private final ObjectMapper objectMapper;

  public MarketDataSnapshotConsumer(
      MarketDataService marketDataService, ObjectMapper objectMapper) {
    this.marketDataService = marketDataService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = TOPIC_MARKET_DATA,
      groupId = "${marketdata.consumer.snapshot-group-id:market-data-snapshot-group}",
      containerFactory = "kafkaListenerContainerFactory")
  public void onOrderBookSnapshot(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      OrderBookSnapshotEvent snapshot =
          objectMapper.readValue(record.value(), OrderBookSnapshotEvent.class);
      marketDataService.handleOrderBookSnapshot(snapshot);
      log.debug(
          "Processed OrderBookSnapshotEvent for {}: seq={}",
          snapshot.instrument(),
          snapshot.sequenceNumber());
    } catch (Exception e) {
      log.error("Failed to process market data snapshot message: {}", record.value(), e);
    } finally {
      if (ack != null) {
        ack.acknowledge();
      }
    }
  }
}
