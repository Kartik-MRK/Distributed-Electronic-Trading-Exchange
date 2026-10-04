package com.dete.common.events.audit;

import com.dete.common.events.DeteEvent;
import java.time.Instant;
import java.util.UUID;

/**
 * Generic audit envelope. Every significant state change in the system produces an AuditEvent on
 * the audit.events topic. The Audit Service stores these immutably.
 */
public record AuditEvent(
    UUID eventId,
    String eventType, // the wrapped event type, e.g. "ORDER_FILLED"
    String subjectType, // ORDER | ACCOUNT | USER | TRADE
    UUID subjectId,
    UUID actorId, // which user/bot triggered this
    String instrument, // null if not applicable
    String payload, // JSON serialisation of the original event
    String traceId, // OTel trace ID for cross-service correlation
    Instant timestamp,
    int version)
    implements DeteEvent {

  @Override
  public String eventType() {
    return eventType;
  }
}
