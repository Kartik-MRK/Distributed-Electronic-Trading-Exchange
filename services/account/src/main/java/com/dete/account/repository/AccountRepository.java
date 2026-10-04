package com.dete.account.repository;

import com.dete.account.model.Account;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AccountRepository {

  private final JdbcTemplate jdbcTemplate;

  public AccountRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public Account createAccount(UUID accountId) {
    Instant now = Instant.now();
    String sql =
        """
        INSERT INTO account.accounts (account_id, created_at)
        VALUES (?, ?)
        ON CONFLICT (account_id) DO NOTHING
        """;
    jdbcTemplate.update(sql, accountId, Timestamp.from(now));
    return new Account(accountId, now);
  }

  public boolean existsById(UUID accountId) {
    String sql = "SELECT count(*) FROM account.accounts WHERE account_id = ?";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, accountId);
    return count != null && count > 0;
  }

  public Optional<Account> findById(UUID accountId) {
    String sql = "SELECT account_id, created_at FROM account.accounts WHERE account_id = ?";
    try {
      Account account = jdbcTemplate.queryForObject(sql, AccountRepository::mapRow, accountId);
      return Optional.ofNullable(account);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  private static Account mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new Account(
        rs.getObject("account_id", UUID.class), rs.getTimestamp("created_at").toInstant());
  }
}
