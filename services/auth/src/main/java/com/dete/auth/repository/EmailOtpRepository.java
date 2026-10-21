package com.dete.auth.repository;

import com.dete.auth.model.EmailOtp;
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
public class EmailOtpRepository {

  private final JdbcTemplate jdbcTemplate;

  private static final RowMapper<EmailOtp> OTP_ROW_MAPPER = EmailOtpRepository::mapRow;

  public EmailOtpRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public EmailOtp save(EmailOtp otp) {
    // Invalidate existing pending OTPs for the same email
    String invalidateSql =
        """
        UPDATE auth.email_otps
        SET consumed = true
        WHERE email = ? AND consumed = false
        """;
    jdbcTemplate.update(invalidateSql, otp.email());

    String insertSql =
        """
        INSERT INTO auth.email_otps (otp_id, email, otp_code, full_name, date_of_birth, password_hash, expires_at, consumed, created_at)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;
    jdbcTemplate.update(
        insertSql,
        otp.otpId(),
        otp.email(),
        otp.otpCode(),
        otp.fullName(),
        otp.dateOfBirth() != null ? Date.valueOf(otp.dateOfBirth()) : null,
        otp.passwordHash(),
        Timestamp.from(otp.expiresAt()),
        otp.consumed(),
        Timestamp.from(otp.createdAt()));
    return otp;
  }

  public Optional<EmailOtp> findValidOtp(String email, String otpCode) {
    String sql =
        """
        SELECT otp_id, email, otp_code, full_name, date_of_birth, password_hash, expires_at, consumed, created_at
        FROM auth.email_otps
        WHERE email = ? AND otp_code = ? AND consumed = false AND expires_at > now()
        ORDER BY created_at DESC
        LIMIT 1
        """;
    try {
      EmailOtp otp = jdbcTemplate.queryForObject(sql, OTP_ROW_MAPPER, email, otpCode);
      return Optional.ofNullable(otp);
    } catch (EmptyResultDataAccessException e) {
      return Optional.empty();
    }
  }

  public void markConsumed(UUID otpId) {
    String sql = "UPDATE auth.email_otps SET consumed = true WHERE otp_id = ?";
    jdbcTemplate.update(sql, otpId);
  }

  private static EmailOtp mapRow(ResultSet rs, int rowNum) throws SQLException {
    Date dobSql = rs.getDate("date_of_birth");
    LocalDate dob = dobSql != null ? dobSql.toLocalDate() : null;

    return new EmailOtp(
        rs.getObject("otp_id", UUID.class),
        rs.getString("email"),
        rs.getString("otp_code"),
        rs.getString("full_name"),
        dob,
        rs.getString("password_hash"),
        rs.getTimestamp("expires_at").toInstant(),
        rs.getBoolean("consumed"),
        rs.getTimestamp("created_at").toInstant());
  }
}
