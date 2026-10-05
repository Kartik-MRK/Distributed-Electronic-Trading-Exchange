package com.dete.order.repository;

import com.dete.order.model.OutboxRecord;
import java.sql.Array;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderOutboxRepository {

  private final JdbcTemplate jdbcTemplate;

  public OrderOutboxRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final RowMapper<OutboxRecord> ROW_MAPPER =
      (rs, rowNum) ->
          new OutboxRecord(
              UUID.fromString(rs.getString("outbox_id")),
              rs.getString("topic"),
              rs.getString("key"),
              rs.getString("payload"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getBoolean("published"));

  public void save(String topic, String key, String payload) {
    String sql =
        """
        INSERT INTO order_svc.outbox (topic, key, payload)
        VALUES (?, ?, ?::jsonb)
        """;
    jdbcTemplate.update(sql, topic, key, payload);
  }

  public List<OutboxRecord> fetchUnpublished(int limit) {
    String sql =
        """
        SELECT outbox_id, topic, key, payload, created_at, published
        FROM order_svc.outbox
        WHERE published = false
        ORDER BY created_at ASC
        LIMIT ?
        FOR UPDATE SKIP LOCKED
        """;
    return jdbcTemplate.query(sql, ROW_MAPPER, limit);
  }

  public void markAsPublished(List<UUID> outboxIds) {
    if (outboxIds == null || outboxIds.isEmpty()) {
      return;
    }
    String sql =
        """
        UPDATE order_svc.outbox
        SET published = true
        WHERE outbox_id = ANY(?)
        """;
    jdbcTemplate.update(
        sql,
        (ps) -> {
          Array array = ps.getConnection().createArrayOf("uuid", outboxIds.toArray());
          ps.setArray(1, array);
        });
  }
}
