package com.dete.audit.model;

import java.time.Instant;
import java.util.UUID;

/** Filter criteria for querying audit log records. */
public record AuditQueryCriteria(
    UUID subjectId,
    String instrument,
    String eventType,
    String traceId,
    Instant from,
    Instant to,
    int limit,
    int offset) {

  public AuditQueryCriteria {
    if (limit <= 0) {
      limit = 100;
    }
    if (limit > 1000) {
      limit = 1000;
    }
    if (offset < 0) {
      offset = 0;
    }
  }
}
