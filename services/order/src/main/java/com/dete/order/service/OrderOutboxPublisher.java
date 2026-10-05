package com.dete.order.service;

import com.dete.order.model.OutboxRecord;
import com.dete.order.repository.OrderOutboxRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = "order.outbox.poller-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OrderOutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OrderOutboxPublisher.class);

  private final OrderOutboxRepository outboxRepository;
  private final KafkaTemplate<String, String> kafkaTemplate;
  private final int batchSize;

  public OrderOutboxPublisher(
      OrderOutboxRepository outboxRepository,
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${order.outbox.batch-size:50}") int batchSize) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.batchSize = batchSize;
  }

  @Scheduled(
      fixedDelayString = "${order.outbox.fixed-delay-ms:50}",
      initialDelayString = "${order.outbox.initial-delay-ms:500}")
  public void publishOutboxMessages() {
    List<OutboxRecord> records = outboxRepository.fetchUnpublished(batchSize);
    if (records.isEmpty()) {
      return;
    }

    log.debug("Found {} unpublished order outbox records", records.size());
    List<UUID> publishedIds = new ArrayList<>();

    for (OutboxRecord record : records) {
      try {
        kafkaTemplate.send(record.topic(), record.key(), record.payload()).get(5, TimeUnit.SECONDS);
        publishedIds.add(record.outboxId());
      } catch (Exception e) {
        log.error(
            "Failed to publish outbox record {} to topic {}", record.outboxId(), record.topic(), e);
        break; // Stop batch on first failure to preserve order
      }
    }

    if (!publishedIds.isEmpty()) {
      outboxRepository.markAsPublished(publishedIds);
      log.debug("Successfully published and marked {} outbox messages", publishedIds.size());
    }
  }
}
