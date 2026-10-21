package com.dete.auth.repository;

import com.dete.auth.model.User;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

  private final JdbcTemplate jdbcTemplate;

  private static final RowMapper<User> USER_ROW_MAPPER = UserRepository::mapRow;

  public UserRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public User save(User user) {
    String sql =
        """
        INSERT INTO auth.users (user_id, username, email, password_hash, created_at, is_demo, full_name, date_of_birth, role, is_verified)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        sql,
        user.userId(),
        user.username(),
        user.email(),
        user.passwordHash(),
        Timestamp.from(user.createdAt()),
        user.isDemo(),
        user.fullName(),
        user.dateOfBirth() != null ? Date.valueOf(user.dateOfBirth()) : null,
        user.role() != null ? user.role() : "ROLE_TRADER",
        user.isVerified());
    return user;
  }

  public Optional<User> findById(UUID userId) {
    String sql =
        """
        SELECT user_id, username, email, password_hash, created_at, is_demo, full_name, date_of_birth, role, is_verified
        FROM auth.users
        WHERE user_id = ?
        """;
    try {
      User user = jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, userId);
      return Optional.ofNullable(user);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<User> findByUsername(String username) {
    String sql =
        """
        SELECT user_id, username, email, password_hash, created_at, is_demo, full_name, date_of_birth, role, is_verified
        FROM auth.users
        WHERE username = ?
        """;
    try {
      User user = jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, username);
      return Optional.ofNullable(user);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<User> findByEmail(String email) {
    String sql =
        """
        SELECT user_id, username, email, password_hash, created_at, is_demo, full_name, date_of_birth, role, is_verified
        FROM auth.users
        WHERE email = ?
        """;
    try {
      User user = jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, email);
      return Optional.ofNullable(user);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public Optional<User> findByIdentifier(String identifier) {
    String sql =
        """
        SELECT user_id, username, email, password_hash, created_at, is_demo, full_name, date_of_birth, role, is_verified
        FROM auth.users
        WHERE username = ? OR email = ?
        """;
    try {
      User user = jdbcTemplate.queryForObject(sql, USER_ROW_MAPPER, identifier, identifier);
      return Optional.ofNullable(user);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public boolean existsByUsername(String username) {
    String sql = "SELECT count(*) FROM auth.users WHERE username = ?";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, username);
    return count != null && count > 0;
  }

  public boolean existsByEmail(String email) {
    String sql = "SELECT count(*) FROM auth.users WHERE email = ?";
    Integer count = jdbcTemplate.queryForObject(sql, Integer.class, email);
    return count != null && count > 0;
  }

  private static User mapRow(ResultSet rs, int rowNum) throws SQLException {
    Date dobSql = rs.getDate("date_of_birth");
    LocalDate dob = dobSql != null ? dobSql.toLocalDate() : null;
    String fullName = rs.getString("full_name");
    String role = rs.getString("role");
    boolean isVerified = rs.getBoolean("is_verified");

    return new User(
        rs.getObject("user_id", UUID.class),
        rs.getString("username"),
        rs.getString("email"),
        rs.getString("password_hash"),
        rs.getTimestamp("created_at").toInstant(),
        rs.getBoolean("is_demo"),
        fullName != null ? fullName : rs.getString("username"),
        dob,
        role != null ? role : (rs.getBoolean("is_demo") ? "ROLE_GUEST" : "ROLE_TRADER"),
        isVerified);
  }
}
