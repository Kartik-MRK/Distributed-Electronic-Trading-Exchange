package com.dete.audit.model;

import java.time.Instant;
import java.util.UUID;

/** Immutable representation of a persisted audit log record. */
public record AuditLogEntry(
    long entryId,
    UUID eventId,
    String eventType,
    String subjectType,
    UUID subjectId,
    UUID actorId,
    String instrument,
    String payload,
    String traceId,
    Instant eventTime,
    Instant ingestedAt) {}
