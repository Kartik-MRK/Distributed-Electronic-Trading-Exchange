package com.dete.account.service;

import com.dete.account.model.OutboxMessage;
import com.dete.account.repository.OutboxRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "account.outbox",
    name = "poller-enabled",
    havingValue = "true",
    matchIfMissing = true)
public class OutboxPublisher {

  private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

  private final OutboxRepository outboxRepository;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final int batchSize;

  public OutboxPublisher(
      OutboxRepository outboxRepository,
      KafkaTemplate<String, Object> kafkaTemplate,
      @Value("${account.outbox.batch-size:50}") int batchSize) {
    this.outboxRepository = outboxRepository;
    this.kafkaTemplate = kafkaTemplate;
    this.batchSize = batchSize;
  }

  @Scheduled(fixedDelayString = "${account.outbox.fixed-delay-ms:100}")
  public void publishOutboxMessages() {
    List<OutboxMessage> pendingMessages = outboxRepository.findUnpublished(batchSize);
    if (pendingMessages.isEmpty()) {
      return;
    }

    for (OutboxMessage message : pendingMessages) {
      try {
        kafkaTemplate
            .send(message.topic(), message.outboxId().toString(), message.payload())
            .get(5, java.util.concurrent.TimeUnit.SECONDS);
        outboxRepository.markPublished(message.outboxId());
        log.debug("Published outbox event {} to topic {}", message.outboxId(), message.topic());
      } catch (Exception e) {
        log.error(
            "Failed to publish outbox event {} to topic {}: {}",
            message.outboxId(),
            message.topic(),
            e.getMessage());
      }
    }
  }
}
