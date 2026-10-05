package com.dete.matching.publisher;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.events.marketdata.OrderBookSnapshotEvent;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically generates and publishes full L2 order book snapshots to the 'market-data' Kafka
 * topic. Allows read-side services (like Market Data Service) to synchronize their in-memory state.
 */
@Component
@ConditionalOnProperty(
    name = "matching.snapshot.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OrderBookSnapshotPublisher {

  private static final Logger log = LoggerFactory.getLogger(OrderBookSnapshotPublisher.class);
  public static final String TOPIC_MARKET_DATA = "market-data";

  private final MatchingEngineService matchingEngineService;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final ObjectMapper objectMapper;

  @Value("${matching.snapshot.depth:50}")
  private int snapshotDepth = 50;

  public OrderBookSnapshotPublisher(
      MatchingEngineService matchingEngineService,
      KafkaTemplate<String, String> kafkaTemplate,
      ObjectMapper objectMapper) {
    this.matchingEngineService = matchingEngineService;
    this.kafkaTemplate = kafkaTemplate;
    this.objectMapper = objectMapper;
  }

  @Scheduled(fixedRateString = "${matching.snapshot.interval-ms:100}")
  public void publishSnapshots() {
    for (Instrument instrument : Instrument.values()) {
      try {
        publishInstrumentSnapshot(instrument);
      } catch (Exception e) {
        log.warn("Failed to publish order book snapshot for instrument {}", instrument, e);
      }
    }
  }

  public void publishInstrumentSnapshot(Instrument instrument) {
    matchingEngineService
        .getL2Depth(instrument, snapshotDepth)
        .thenAccept(
            l2 -> {
              try {
                List<OrderBookSnapshotEvent.SnapshotLevel> bids =
                    l2.bids().stream()
                        .map(
                            b ->
                                new OrderBookSnapshotEvent.SnapshotLevel(
                                    b.price(), b.volume(), b.orderCount()))
                        .toList();
                List<OrderBookSnapshotEvent.SnapshotLevel> asks =
                    l2.asks().stream()
                        .map(
                            a ->
                                new OrderBookSnapshotEvent.SnapshotLevel(
                                    a.price(), a.volume(), a.orderCount()))
                        .toList();

                OrderBookSnapshotEvent event =
                    new OrderBookSnapshotEvent(
                        UUID.randomUUID(),
                        instrument,
                        l2.sequenceNumber(),
                        bids,
                        asks,
                        Instant.now(),
                        1);

                String payload = objectMapper.writeValueAsString(event);
                kafkaTemplate.send(TOPIC_MARKET_DATA, instrument.symbol(), payload);
                log.trace(
                    "Published order book snapshot for {}: seq={}",
                    instrument,
                    l2.sequenceNumber());
              } catch (Exception e) {
                log.error("Failed to serialize or publish snapshot for {}", instrument, e);
              }
            });
  }
}
