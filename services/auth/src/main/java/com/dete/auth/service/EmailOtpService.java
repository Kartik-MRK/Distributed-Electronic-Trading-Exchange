package com.dete.auth.service;

import java.security.SecureRandom;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpService {

  private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");

  private final SecureRandom secureRandom = new SecureRandom();
  private final MailSender mailSender;

  public EmailOtpService(@Autowired(required = false) MailSender mailSender) {
    this.mailSender = mailSender;
  }

  public boolean isValidEmail(String email) {
    if (email == null || email.isBlank()) {
      return false;
    }
    return EMAIL_PATTERN.matcher(email.trim()).matches();
  }

  public String generateOtpCode() {
    int code = 100000 + secureRandom.nextInt(900000);
    return String.valueOf(code);
  }

  public void dispatchOtpEmail(String recipientEmail, String otpCode, String fullName) {
    String greeting = (fullName != null && !fullName.isBlank()) ? fullName.trim() : "Trader";
    String subject = "DETE Exchange — Verification Code: " + otpCode;
    String messageBody =
        String.format(
            "Hello %s,\n\n"
                + "Your DETE verification code is: %s\n\n"
                + "This one-time passcode will expire in 10 minutes.\n"
                + "If you did not request this code, please disregard this email.\n\n"
                + "DETE Exchange Security Team",
            greeting, otpCode);

    log.info(
        "==> [EMAIL OTP DISPATCH] Verification OTP for '{}' is [{}] (Valid for 10 minutes)",
        recipientEmail,
        otpCode);

    if (mailSender != null) {
      try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(messageBody);
        mailSender.send(message);
        log.info("Successfully delivered verification email via SMTP to {}", recipientEmail);
      } catch (Exception e) {
        log.warn(
            "SMTP dispatch failed for {} (will fallback to logged OTP): {}",
            recipientEmail,
            e.getMessage());
      }
    }
  }
}
