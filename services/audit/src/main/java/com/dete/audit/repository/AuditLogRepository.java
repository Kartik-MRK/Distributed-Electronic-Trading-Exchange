package com.dete.audit.repository;

import com.dete.audit.model.AuditLogEntry;
import com.dete.audit.model.AuditQueryCriteria;
import com.dete.common.events.audit.AuditEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class AuditLogRepository {

  private static final Logger log = LoggerFactory.getLogger(AuditLogRepository.class);

  private static final RowMapper<AuditLogEntry> ROW_MAPPER =
      (rs, rowNum) -> {
        Timestamp eventTime = rs.getTimestamp("event_time");
        Timestamp ingestedAt = rs.getTimestamp("ingested_at");
        return new AuditLogEntry(
            rs.getLong("entry_id"),
            rs.getObject("event_id", UUID.class),
            rs.getString("event_type"),
            rs.getString("subject_type"),
            rs.getObject("subject_id", UUID.class),
            rs.getObject("actor_id", UUID.class),
            rs.getString("instrument"),
            rs.getString("payload"),
            rs.getString("trace_id"),
            eventTime != null ? eventTime.toInstant() : null,
            ingestedAt != null ? ingestedAt.toInstant() : null);
      };

  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public AuditLogRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  /**
   * Idempotently inserts an audit event into audit.audit_log. If the event_id already exists, DO
   * NOTHING is performed.
   *
   * @return number of affected rows (1 if inserted, 0 if duplicate ignored)
   */
  public int insert(AuditEvent event) {
    String sql =
        """
        INSERT INTO audit.audit_log (
            event_id, event_type, subject_type, subject_id, actor_id,
            instrument, payload, trace_id, event_time
        ) VALUES (
            ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?
        ) ON CONFLICT (event_id) DO NOTHING
        """;

    String payloadJson = sanitizeJsonPayload(event.payload());
    Timestamp eventTime =
        event.timestamp() != null
            ? Timestamp.from(event.timestamp())
            : Timestamp.from(Instant.now());

    int rows =
        jdbcTemplate.update(
            sql,
            event.eventId(),
            event.eventType(),
            event.subjectType(),
            event.subjectId(),
            event.actorId(),
            event.instrument(),
            payloadJson,
            event.traceId(),
            eventTime);

    if (rows > 0) {
      log.debug("Persisted audit event: id={}, type={}", event.eventId(), event.eventType());
    } else {
      log.debug("Duplicate audit event ignored: id={}", event.eventId());
    }
    return rows;
  }

  public Optional<AuditLogEntry> findByEventId(UUID eventId) {
    String sql =
        """
        SELECT entry_id, event_id, event_type, subject_type, subject_id,
               actor_id, instrument, payload, trace_id, event_time, ingested_at
        FROM audit.audit_log
        WHERE event_id = ?
        """;
    List<AuditLogEntry> results = jdbcTemplate.query(sql, ROW_MAPPER, eventId);
    return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
  }

  public List<AuditLogEntry> findByCriteria(AuditQueryCriteria criteria) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT entry_id, event_id, event_type, subject_type, subject_id,
                   actor_id, instrument, payload, trace_id, event_time, ingested_at
            FROM audit.audit_log
            WHERE 1=1
            """);

    List<Object> params = new ArrayList<>();

    if (criteria.subjectId() != null) {
      sql.append(" AND subject_id = ?");
      params.add(criteria.subjectId());
    }
    if (criteria.instrument() != null && !criteria.instrument().isBlank()) {
      sql.append(" AND instrument = ?");
      params.add(criteria.instrument().trim());
    }
    if (criteria.eventType() != null && !criteria.eventType().isBlank()) {
      sql.append(" AND event_type = ?");
      params.add(criteria.eventType().trim());
    }
    if (criteria.traceId() != null && !criteria.traceId().isBlank()) {
      sql.append(" AND trace_id = ?");
      params.add(criteria.traceId().trim());
    }
    if (criteria.from() != null) {
      sql.append(" AND event_time >= ?");
      params.add(Timestamp.from(criteria.from()));
    }
    if (criteria.to() != null) {
      sql.append(" AND event_time <= ?");
      params.add(Timestamp.from(criteria.to()));
    }

    sql.append(" ORDER BY event_time DESC, entry_id DESC LIMIT ? OFFSET ?");
    params.add(criteria.limit());
    params.add(criteria.offset());

    return jdbcTemplate.query(sql.toString(), ROW_MAPPER, params.toArray());
  }

  private String sanitizeJsonPayload(String rawPayload) {
    if (rawPayload == null || rawPayload.isBlank()) {
      return "{}";
    }
    String trimmed = rawPayload.trim();
    if ((trimmed.startsWith("{") && trimmed.endsWith("}"))
        || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
      return trimmed;
    }
    try {
      return objectMapper.writeValueAsString(rawPayload);
    } catch (Exception e) {
      log.warn("Failed to serialize non-json payload to json string", e);
      return "{}";
    }
  }
}
