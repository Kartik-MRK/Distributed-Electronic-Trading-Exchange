package com.dete.account.repository;

import com.dete.account.model.LedgerEntry;
import com.dete.account.model.LedgerEntryType;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerRepository {

  private final JdbcTemplate jdbcTemplate;

  public LedgerRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public long save(LedgerEntry entry) {
    String sql =
        """
        INSERT INTO account.ledger_entries (account_id, asset, entry_type, amount, reference_id, sequence_num, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        RETURNING entry_id
        """;
    Long entryId =
        jdbcTemplate.queryForObject(
            sql,
            Long.class,
            entry.accountId(),
            entry.asset(),
            entry.entryType().name(),
            entry.amount(),
            entry.referenceId(),
            entry.sequenceNum(),
            Timestamp.from(entry.createdAt()));
    return entryId != null ? entryId : 0L;
  }

  public long getNextSequenceNum(UUID accountId) {
    String sql =
        """
        SELECT COALESCE(MAX(sequence_num), 0) + 1
        FROM account.ledger_entries
        WHERE account_id = ?
        """;
    Long seq = jdbcTemplate.queryForObject(sql, Long.class, accountId);
    return seq != null ? seq : 1L;
  }

  public List<LedgerEntry> findByAccountId(UUID accountId, int limit, int offset) {
    String sql =
        """
        SELECT entry_id, account_id, asset, entry_type, amount, reference_id, sequence_num, created_at
        FROM account.ledger_entries
        WHERE account_id = ?
        ORDER BY sequence_num DESC
        LIMIT ? OFFSET ?
        """;
    return jdbcTemplate.query(sql, LedgerRepository::mapRow, accountId, limit, offset);
  }

  public long countByAccountId(UUID accountId) {
    String sql = "SELECT count(*) FROM account.ledger_entries WHERE account_id = ?";
    Long count = jdbcTemplate.queryForObject(sql, Long.class, accountId);
    return count != null ? count : 0L;
  }

  private static LedgerEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new LedgerEntry(
        rs.getLong("entry_id"),
        rs.getObject("account_id", UUID.class),
        rs.getString("asset"),
        LedgerEntryType.valueOf(rs.getString("entry_type")),
        rs.getLong("amount"),
        rs.getObject("reference_id", UUID.class),
        rs.getLong("sequence_num"),
        rs.getTimestamp("created_at").toInstant());
  }
}
