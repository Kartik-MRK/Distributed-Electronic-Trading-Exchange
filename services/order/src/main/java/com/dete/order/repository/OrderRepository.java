package com.dete.order.repository;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import com.dete.order.model.OrderRecord;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

  private final JdbcTemplate jdbcTemplate;

  public OrderRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final RowMapper<OrderRecord> ROW_MAPPER =
      (rs, rowNum) ->
          new OrderRecord(
              UUID.fromString(rs.getString("order_id")),
              UUID.fromString(rs.getString("account_id")),
              Instrument.valueOf(rs.getString("instrument")),
              OrderSide.valueOf(rs.getString("side")),
              OrderType.valueOf(rs.getString("order_type")),
              rs.getObject("price") != null ? rs.getLong("price") : null,
              rs.getLong("original_qty"),
              rs.getLong("remaining_qty"),
              rs.getLong("filled_qty"),
              OrderStatus.valueOf(rs.getString("status")),
              UUID.fromString(rs.getString("idempotency_key")),
              rs.getString("reject_reason"),
              rs.getTimestamp("created_at").toInstant(),
              rs.getTimestamp("updated_at").toInstant());

  public void save(OrderRecord order) {
    String sql =
        """
        INSERT INTO order_svc.orders (
            order_id, account_id, instrument, side, order_type, price,
            original_qty, remaining_qty, filled_qty, status, idempotency_key,
            reject_reason, created_at, updated_at
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        order.orderId(),
        order.accountId(),
        order.instrument().name(),
        order.side().name(),
        order.orderType().name(),
        order.price(),
        order.originalQty(),
        order.remainingQty(),
        order.filledQty(),
        order.status().name(),
        order.idempotencyKey(),
        order.rejectReason(),
        Timestamp.from(order.createdAt()),
        Timestamp.from(order.updatedAt()));
  }

  public Optional<OrderRecord> findById(UUID orderId) {
    String sql = "SELECT * FROM order_svc.orders WHERE order_id = ?";
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, orderId));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<OrderRecord> findByIdAndAccountId(UUID orderId, UUID accountId) {
    String sql = "SELECT * FROM order_svc.orders WHERE order_id = ? AND account_id = ?";
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, orderId, accountId));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<OrderRecord> findByIdempotencyKey(UUID idempotencyKey) {
    String sql = "SELECT * FROM order_svc.orders WHERE idempotency_key = ?";
    try {
      return Optional.ofNullable(jdbcTemplate.queryForObject(sql, ROW_MAPPER, idempotencyKey));
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public List<OrderRecord> findActiveByAccountId(UUID accountId) {
    String sql =
        """
        SELECT * FROM order_svc.orders
        WHERE account_id = ? AND status IN ('SUBMITTED', 'ACCEPTED', 'PARTIALLY_FILLED')
        ORDER BY created_at DESC
        """;
    return jdbcTemplate.query(sql, ROW_MAPPER, accountId);
  }

  public List<OrderRecord> findByAccountIdAndStatus(UUID accountId, OrderStatus status) {
    String sql =
        """
        SELECT * FROM order_svc.orders
        WHERE account_id = ? AND status = ?
        ORDER BY created_at DESC
        """;
    return jdbcTemplate.query(sql, ROW_MAPPER, accountId, status.name());
  }

  public List<OrderRecord> findHistoryByAccountId(UUID accountId, int limit, int offset) {
    String sql =
        """
        SELECT * FROM order_svc.orders
        WHERE account_id = ?
        ORDER BY created_at DESC
        LIMIT ? OFFSET ?
        """;
    return jdbcTemplate.query(sql, ROW_MAPPER, accountId, limit, offset);
  }

  public boolean updateStatusAndFills(
      UUID orderId, OrderStatus status, Long filledQty, Long remainingQty, String rejectReason) {
    String sql =
        """
        UPDATE order_svc.orders
        SET status = ?,
            filled_qty = COALESCE(?, filled_qty),
            remaining_qty = COALESCE(?, remaining_qty),
            reject_reason = COALESCE(?, reject_reason),
            updated_at = now()
        WHERE order_id = ?
        """;
    int updated =
        jdbcTemplate.update(sql, status.name(), filledQty, remainingQty, rejectReason, orderId);
    return updated > 0;
  }
}
