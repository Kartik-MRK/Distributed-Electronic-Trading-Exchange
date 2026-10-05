package com.dete.audit.controller;

import com.dete.audit.model.AuditLogEntry;
import com.dete.audit.model.AuditQueryCriteria;
import com.dete.audit.service.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/audit")
public class AuditAdminController {

  private final AuditService auditService;

  public AuditAdminController(AuditService auditService) {
    this.auditService = auditService;
  }

  @GetMapping
  public ResponseEntity<List<AuditLogEntry>> queryAuditLogs(
      @RequestParam(required = false) UUID subjectId,
      @RequestParam(required = false) String instrument,
      @RequestParam(required = false) String eventType,
      @RequestParam(required = false) String traceId,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
          Instant to,
      @RequestParam(defaultValue = "100") int limit,
      @RequestParam(defaultValue = "0") int offset) {

    AuditQueryCriteria criteria =
        new AuditQueryCriteria(subjectId, instrument, eventType, traceId, from, to, limit, offset);
    return ResponseEntity.ok(auditService.queryAuditLogs(criteria));
  }

  @GetMapping("/entries/{eventId}")
  public ResponseEntity<AuditLogEntry> getAuditEntry(@PathVariable UUID eventId) {
    return auditService
        .getEntryByEventId(eventId)
        .map(ResponseEntity::ok)
        .orElseGet(() -> ResponseEntity.notFound().build());
  }
}
