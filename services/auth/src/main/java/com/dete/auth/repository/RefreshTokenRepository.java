package com.dete.auth.repository;

import com.dete.auth.model.RefreshToken;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenRepository {

  private final JdbcTemplate jdbcTemplate;

  private static final RowMapper<RefreshToken> REFRESH_TOKEN_ROW_MAPPER =
      RefreshTokenRepository::mapRow;

  public RefreshTokenRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public RefreshToken save(RefreshToken token) {
    String sql =
        """
        INSERT INTO auth.refresh_tokens (token_id, user_id, token_hash, expires_at, revoked, created_at)
        VALUES (?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        token.tokenId(),
        token.userId(),
        token.tokenHash(),
        Timestamp.from(token.expiresAt()),
        token.revoked(),
        Timestamp.from(token.createdAt()));
    return token;
  }

  public Optional<RefreshToken> findByTokenHash(String tokenHash) {
    String sql =
        """
        SELECT token_id, user_id, token_hash, expires_at, revoked, created_at
        FROM auth.refresh_tokens
        WHERE token_hash = ?
        """;
    try {
      RefreshToken token = jdbcTemplate.queryForObject(sql, REFRESH_TOKEN_ROW_MAPPER, tokenHash);
      return Optional.ofNullable(token);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public int revokeByTokenId(UUID tokenId) {
    String sql = "UPDATE auth.refresh_tokens SET revoked = true WHERE token_id = ?";
    return jdbcTemplate.update(sql, tokenId);
  }

  public int revokeAllByUserId(UUID userId) {
    String sql = "UPDATE auth.refresh_tokens SET revoked = true WHERE user_id = ?";
    return jdbcTemplate.update(sql, userId);
  }

  public int deleteExpiredOrRevoked() {
    String sql = "DELETE FROM auth.refresh_tokens WHERE expires_at < now() OR revoked = true";
    return jdbcTemplate.update(sql);
  }

  private static RefreshToken mapRow(ResultSet rs, int rowNum) throws SQLException {
    return new RefreshToken(
        rs.getObject("token_id", UUID.class),
        rs.getObject("user_id", UUID.class),
        rs.getString("token_hash"),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getBoolean("revoked"),
        rs.getTimestamp("created_at").toInstant());
  }
}
