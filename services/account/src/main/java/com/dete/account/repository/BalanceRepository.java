package com.dete.account.repository;

import com.dete.account.model.Balance;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BalanceRepository {

  private final JdbcTemplate jdbcTemplate;

  public BalanceRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Optional<Balance> getBalance(UUID accountId, String asset) {
    String sql =
        """
        SELECT account_id, asset, available, reserved
        FROM account.balances
        WHERE account_id = ? AND asset = ?
        """;
    try {
      Balance balance =
          jdbcTemplate.queryForObject(sql, BalanceRepository::mapRow, accountId, asset);
      return Optional.ofNullable(balance);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public List<Balance> getAllBalances(UUID accountId) {
    String sql =
        """
        SELECT account_id, asset, available, reserved
        FROM account.balances
        WHERE account_id = ?
        ORDER BY asset ASC
        """;
    return jdbcTemplate.query(sql, BalanceRepository::mapRow, accountId);
  }

  public void creditAvailable(UUID accountId, String asset, long amount) {
    String sql =
        """
        INSERT INTO account.balances (account_id, asset, available, reserved)
        VALUES (?, ?, ?, 0)
        ON CONFLICT (account_id, asset)
        DO UPDATE SET available = account.balances.available + EXCLUDED.available
        """;
    jdbcTemplate.update(sql, accountId, asset, amount);
  }

  public int reserve(UUID accountId, String asset, long amount) {
    String sql =
        """
        UPDATE account.balances
        SET available = available - ?, reserved = reserved + ?
        WHERE account_id = ? AND asset = ? AND available >= ?
        """;
    return jdbcTemplate.update(sql, amount, amount, accountId, asset, amount);
  }

  public int release(UUID accountId, String asset, long amount) {
    String sql =
        """
        UPDATE account.balances
        SET available = available + ?, reserved = reserved - ?
        WHERE account_id = ? AND asset = ? AND reserved >= ?
        """;
    return jdbcTemplate.update(sql, amount, amount, accountId, asset, amount);
  }

  public int debitReserved(UUID accountId, String asset, long amount) {
    String sql =
        """
        UPDATE account.balances
        SET reserved = reserved - ?
        WHERE account_id = ? AND asset = ? AND reserved >= ?
        """;
    return jdbcTemplate.update(sql, amount, accountId, asset, amount);
  }

  public int debitAvailable(UUID accountId, String asset, long amount) {
    String sql =
        """
        UPDATE account.balances
        SET available = available - ?
        WHERE account_id = ? AND asset = ? AND available >= ?
        """;
    return jdbcTemplate.update(sql, amount, accountId, asset, amount);
  }

  public java.util.Map<String, Long> sumTotalBalancesByAsset() {
    String sql =
        """
        SELECT asset, COALESCE(SUM(available + reserved), 0) AS total_balance
        FROM account.balances
        GROUP BY asset
        """;
    return jdbcTemplate.query(
        sql,
        rs -> {
          java.util.Map<String, Long> map = new java.util.HashMap<>();
          while (rs.next()) {
            map.put(rs.getString("asset"), rs.getLong("total_balance"));
          }
          return map;
        });
  }

  private static Balance mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Balance(
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        rs.getLong("available"),
        rs.getLong("reserved"));
  }
}
