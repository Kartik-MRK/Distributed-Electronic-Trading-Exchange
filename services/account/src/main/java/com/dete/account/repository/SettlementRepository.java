package com.dete.account.repository;

import com.dete.account.model.Settlement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class SettlementRepository {

  private final JdbcTemplate jdbcTemplate;

  public SettlementRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean existsByTradeId(UUID tradeId) {
    String sql = "SELECT count(*) FROM account.settlements WHERE trade_id = ?";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, tradeId);
    return count != null && count > 0;
  }

  public Settlement save(Settlement settlement) {
    String sql =
        """
        INSERT INTO account.settlements (settlement_id, trade_id, buyer_id, seller_id, instrument, price, quantity, settled_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        settlement.settlementId(),
        settlement.tradeId(),
        settlement.buyerId(),
        settlement.sellerId(),
        settlement.instrument(),
        settlement.price(),
        settlement.quantity(),
        Timestamp.from(settlement.settledAt()));
    return settlement;
  }

  public List<Settlement> findByAccountId(UUID accountId, int limit, int offset) {
    String sql =
        """
        SELECT settlement_id, trade_id, buyer_id, seller_id, instrument, price, quantity, settled_at
        FROM account.settlements
        WHERE buyer_id = ? OR seller_id = ?
        ORDER BY settled_at DESC
        LIMIT ? OFFSET ?
        """;
    return jdbcTemplate.query(
        sql, SettlementRepository::mapRow, accountId, accountId, limit, offset);
  }

  private static Settlement mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Settlement(
        rs.getObject("settlement_id", UUID.class),
        rs.getObject("trade_id", UUID.class),
        rs.getObject("buyer_id", UUID.class),
        rs.getObject("seller_id", UUID.class),
        rs.getString("instrument"),
        rs.getLong("price"),
        rs.getLong("quantity"),
        rs.getTimestamp("settled_at").toInstant());
  }
}
