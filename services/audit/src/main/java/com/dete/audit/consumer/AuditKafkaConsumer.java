package com.dete.audit.consumer;

import com.dete.audit.service.AuditService;
import com.dete.common.events.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class AuditKafkaConsumer {

  private static final Logger log = LoggerFactory.getLogger(AuditKafkaConsumer.class);

  public static final String AUDIT_EVENTS_TOPIC = "audit.events";

  private final AuditService auditService;
  private final ObjectMapper objectMapper;

  public AuditKafkaConsumer(AuditService auditService, ObjectMapper objectMapper) {
    this.auditService = auditService;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = AUDIT_EVENTS_TOPIC,
      groupId = "${spring.kafka.consumer.group-id:audit-service-group}")
  public void consume(ConsumerRecord<String, String> record, Acknowledgment ack) {
    try {
      log.debug(
          "Received audit event on topic {} partition {} offset {}",
          record.topic(),
          record.partition(),
          record.offset());

      AuditEvent event = objectMapper.readValue(record.value(), AuditEvent.class);
      auditService.recordAuditEvent(event);
      ack.acknowledge();
    } catch (Exception e) {
      log.error(
          "Failed to process audit event from topic {} offset {}: {}",
          record.topic(),
          record.offset(),
          e.getMessage(),
          e);
      // Acknowledge to prevent poison pill from indefinitely stalling the consumer partition
      ack.acknowledge();
    }
  }
}
