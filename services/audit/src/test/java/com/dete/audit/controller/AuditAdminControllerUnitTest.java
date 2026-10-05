package com.dete.audit.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.audit.model.AuditLogEntry;
import com.dete.audit.model.AuditQueryCriteria;
import com.dete.audit.service.AuditService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class AuditAdminControllerUnitTest {

  private AuditService auditService;
  private AuditAdminController controller;

  @BeforeEach
  void setUp() {
    auditService = mock(AuditService.class);
    controller = new AuditAdminController(auditService);
  }

  @Test
  @DisplayName("queryAuditLogs maps query params to criteria and returns 200")
  void testQueryAuditLogs() {
    UUID subjectId = UUID.randomUUID();
    AuditLogEntry entry =
        new AuditLogEntry(
            1L,
            UUID.randomUUID(),
            "ORDER_PLACED",
            "ORDER",
            subjectId,
            UUID.randomUUID(),
            "BTC_USDT",
            "{}",
            "trace-1",
            Instant.now(),
            Instant.now());

    when(auditService.queryAuditLogs(any(AuditQueryCriteria.class))).thenReturn(List.of(entry));

    ResponseEntity<List<AuditLogEntry>> response =
        controller.queryAuditLogs(
            subjectId, "BTC_USDT", "ORDER_PLACED", "trace-1", null, null, 50, 0);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody()).hasSize(1);
    verify(auditService).queryAuditLogs(any(AuditQueryCriteria.class));
  }

  @Test
  @DisplayName("getAuditEntry returns 200 when found and 404 when missing")
  void testGetAuditEntry() {
    UUID eventId = UUID.randomUUID();
    AuditLogEntry entry =
        new AuditLogEntry(
            1L,
            eventId,
            "ORDER_PLACED",
            "ORDER",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC_USDT",
            "{}",
            "trace-1",
            Instant.now(),
            Instant.now());

    when(auditService.getEntryByEventId(eventId)).thenReturn(Optional.of(entry));

    ResponseEntity<AuditLogEntry> response = controller.getAuditEntry(eventId);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().eventId()).isEqualTo(eventId);

    UUID nonExistent = UUID.randomUUID();
    when(auditService.getEntryByEventId(nonExistent)).thenReturn(Optional.empty());

    ResponseEntity<AuditLogEntry> notFound = controller.getAuditEntry(nonExistent);
    assertThat(notFound.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
  }
}
