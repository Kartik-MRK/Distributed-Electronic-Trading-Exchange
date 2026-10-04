package com.dete.auth.service;

import com.dete.common.events.audit.AuditEvent;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class AuditProducerService {

  private static final Logger log = LoggerFactory.getLogger(AuditProducerService.class);
  public static final String AUDIT_TOPIC = "audit.events";
  public static final String SUBJECT_TYPE_USER = "USER";

  private final KafkaTemplate<String, Object> kafkaTemplate;

  public AuditProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
    this.kafkaTemplate = kafkaTemplate;
  }

  public void publishAuthAuditEvent(String eventType, UUID userId, String payload) {
    UUID eventId = UUID.randomUUID();
    Instant now = Instant.now();
    String traceId =
        UUID.randomUUID().toString(); // Will integrate with OTel tracer when tracer is active

    AuditEvent event =
        new AuditEvent(
            eventId, eventType, SUBJECT_TYPE_USER, userId, userId, null, payload, traceId, now, 1);

    try {
      kafkaTemplate
          .send(AUDIT_TOPIC, userId.toString(), event)
          .whenComplete(
              (result, ex) -> {
                if (ex != null) {
                  log.error(
                      "Failed to publish audit event [{}] for user {}: {}",
                      eventType,
                      userId,
                      ex.getMessage());
                } else {
                  log.debug(
                      "Successfully published audit event [{}] for user {}", eventType, userId);
                }
              });
    } catch (Exception e) {
      log.error("Error sending audit event [{}] to Kafka: {}", eventType, e.getMessage());
    }
  }
}
