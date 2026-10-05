package com.dete.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.audit.model.AuditLogEntry;
import com.dete.audit.model.AuditQueryCriteria;
import com.dete.audit.repository.AuditLogRepository;
import com.dete.common.events.audit.AuditEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AuditServiceUnitTest {

  private AuditLogRepository auditLogRepository;
  private AuditService auditService;

  @BeforeEach
  void setUp() {
    auditLogRepository = mock(AuditLogRepository.class);
    auditService = new AuditService(auditLogRepository);
  }

  @Test
  @DisplayName("recordAuditEvent throws on null input or missing mandatory fields")
  void testValidation() {
    assertThatThrownBy(() -> auditService.recordAuditEvent(null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("AuditEvent cannot be null");

    AuditEvent noId =
        new AuditEvent(
            null,
            "ORDER_PLACED",
            "ORDER",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC_USDT",
            "{}",
            "trace-1",
            Instant.now(),
            1);
    assertThatThrownBy(() -> auditService.recordAuditEvent(noId))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("event.eventId cannot be null");

    AuditEvent noType =
        new AuditEvent(
            UUID.randomUUID(),
            null,
            "ORDER",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC_USDT",
            "{}",
            "trace-1",
            Instant.now(),
            1);
    assertThatThrownBy(() -> auditService.recordAuditEvent(noType))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("event.eventType cannot be null");
  }

  @Test
  @DisplayName("recordAuditEvent returns true when inserted and false when duplicate")
  void testRecordAuditEvent() {
    AuditEvent event =
        new AuditEvent(
            UUID.randomUUID(),
            "ORDER_PLACED",
            "ORDER",
            UUID.randomUUID(),
            UUID.randomUUID(),
            "BTC_USDT",
            "{\"price\":50000}",
            "trace-123",
            Instant.now(),
            1);

    when(auditLogRepository.insert(event)).thenReturn(1);
    assertThat(auditService.recordAuditEvent(event)).isTrue();
    verify(auditLogRepository).insert(event);

    when(auditLogRepository.insert(event)).thenReturn(0);
    assertThat(auditService.recordAuditEvent(event)).isFalse();
  }

  @Test
  @DisplayName("getEntryByEventId delegates to repository")
  void testGetEntryByEventId() {
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
            "trace-123",
            Instant.now(),
            Instant.now());

    when(auditLogRepository.findByEventId(eventId)).thenReturn(Optional.of(entry));

    Optional<AuditLogEntry> result = auditService.getEntryByEventId(eventId);
    assertThat(result).isPresent();
    assertThat(result.get().eventId()).isEqualTo(eventId);
  }

  @Test
  @DisplayName("queryAuditLogs delegates criteria to repository")
  void testQueryAuditLogs() {
    AuditQueryCriteria criteria =
        new AuditQueryCriteria(
            UUID.randomUUID(), "BTC_USDT", "ORDER_PLACED", null, null, null, 10, 0);
    List<AuditLogEntry> expectedList = List.of();

    when(auditLogRepository.findByCriteria(criteria)).thenReturn(expectedList);

    List<AuditLogEntry> result = auditService.queryAuditLogs(criteria);
    assertThat(result).isSameAs(expectedList);
    verify(auditLogRepository).findByCriteria(criteria);
  }
}
