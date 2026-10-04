package com.dete.account.repository;

import com.dete.account.model.OutboxMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

  private final JdbcTemplate jdbcTemplate;

  public OutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public OutboxMessage save(OutboxMessage message) {
    String sql =
        """
        INSERT INTO account.outbox (outbox_id, topic, payload, created_at, published)
        VALUES (?, ?, ?::jsonb, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        message.outboxId(),
        message.topic(),
        message.payload(),
        Timestamp.from(message.createdAt()),
        message.published());
    return message;
  }

  public List<OutboxMessage> findUnpublished(int limit) {
    String sql =
        """
        SELECT outbox_id, topic, payload::text, created_at, published
        FROM account.outbox
        WHERE published = false
        ORDER BY created_at ASC
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """;
    return jdbcTemplate.query(sql, OutboxRepository::mapRow, limit);
  }

  public void markPublished(UUID outboxId) {
    String sql = "UPDATE account.outbox SET published = true WHERE outbox_id = ?";
    jdbcTemplate.update(sql, outboxId);
  }

  private static OutboxMessage mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new OutboxMessage(
        rs.getObject("outbox_id", UUID.class),
        rs.getString("topic"),
        rs.getString("payload"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getBoolean("published"));
  }
}
