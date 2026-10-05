package com.dete.audit.service;

import com.dete.audit.model.AuditLogEntry;
import com.dete.audit.model.AuditQueryCriteria;
import com.dete.audit.repository.AuditLogRepository;
import com.dete.common.events.audit.AuditEvent;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

  private static final Logger log = LoggerFactory.getLogger(AuditService.class);

  private final AuditLogRepository auditLogRepository;

  public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  /**
   * Records an audit event immutably into the audit store.
   *
   * @param event the audit event
   * @return true if inserted, false if ignored due to duplicate eventId
   */
  public boolean recordAuditEvent(AuditEvent event) {
    Objects.requireNonNull(event, "AuditEvent cannot be null");
    Objects.requireNonNull(event.eventId(), "event.eventId cannot be null");
    Objects.requireNonNull(event.eventType(), "event.eventType cannot be null");
    Objects.requireNonNull(event.subjectType(), "event.subjectType cannot be null");
    Objects.requireNonNull(event.subjectId(), "event.subjectId cannot be null");

    int rows = auditLogRepository.insert(event);
    return rows > 0;
  }

  public Optional<AuditLogEntry> getEntryByEventId(UUID eventId) {
    Objects.requireNonNull(eventId, "eventId cannot be null");
    return auditLogRepository.findByEventId(eventId);
  }

  public List<AuditLogEntry> queryAuditLogs(AuditQueryCriteria criteria) {
    Objects.requireNonNull(criteria, "criteria cannot be null");
    return auditLogRepository.findByCriteria(criteria);
  }
}
