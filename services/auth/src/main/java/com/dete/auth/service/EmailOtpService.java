package com.dete.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.stereotype.Service;

@Service
public class EmailOtpService {

  private static final Logger log = LoggerFactory.getLogger(EmailOtpService.class);

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,63}$");

  private static final String RESEND_API_URL = "https://api.resend.com/emails";

  private final SecureRandom secureRandom = new SecureRandom();
  private final MailSender mailSender;
  private final String resendApiKey;
  private final String resendFromEmail;
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public EmailOtpService(
      @Autowired(required = false) MailSender mailSender,
      @Value("${resend.api-key:}") String resendApiKey,
      @Value("${resend.from-email:DETE Security <security@mail.kartik-mrk.me>}")
          String resendFromEmail) {
    this.mailSender = mailSender;
    this.resendApiKey = (resendApiKey != null) ? resendApiKey.trim() : "";
    this.resendFromEmail =
        (resendFromEmail != null && !resendFromEmail.isBlank())
            ? resendFromEmail.trim()
            : "DETE Security <security@mail.kartik-mrk.me>";
    this.httpClient =
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.objectMapper = new ObjectMapper();
  }

  /** Convenience constructor for unit testing */
  public EmailOtpService(MailSender mailSender) {
    this(mailSender, null, null);
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
    String plainText =
        String.format(
            "Hello %s,\n\n"
                + "Your DETE verification code is: %s\n\n"
                + "This one-time passcode will expire in 10 minutes.\n"
                + "Once verified, your account will be pre-funded with 50,000 USDT.\n\n"
                + "If you did not request this code, please disregard this email.\n\n"
                + "DETE Exchange Security Team",
            greeting, otpCode);

    log.info(
        "==> [EMAIL OTP DISPATCH] Initiating verification dispatch for '{}' (Code valid for 10 min)",
        recipientEmail);

    // 1. Dispatch via Resend HTTP API if configured
    if (!resendApiKey.isBlank()) {
      try {
        sendViaResend(recipientEmail, otpCode, greeting, subject, plainText);
        return;
      } catch (Exception e) {
        log.error("Resend API dispatch failed for {}: {}", recipientEmail, e.getMessage(), e);
      }
    }

    // 2. Fallback to Spring MailSender (SMTP) if present
    if (mailSender != null) {
      try {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(plainText);
        mailSender.send(message);
        log.info("Successfully delivered verification email via SMTP to {}", recipientEmail);
      } catch (Exception e) {
        log.warn("SMTP dispatch failed for {}: {}", recipientEmail, e.getMessage());
      }
    }
  }

  private void sendViaResend(
      String recipientEmail,
      String otpCode,
      String greeting,
      String subject,
      String plainText)
      throws Exception {
    String htmlBody = buildHtmlEmail(greeting, otpCode);

    Map<String, Object> payload =
        Map.of(
            "from", resendFromEmail,
            "to", List.of(recipientEmail),
            "subject", subject,
            "text", plainText,
            "html", htmlBody);

    String requestBodyJson = objectMapper.writeValueAsString(payload);

    HttpRequest request =
        HttpRequest.newBuilder()
            .uri(URI.create(RESEND_API_URL))
            .header("Authorization", "Bearer " + resendApiKey)
            .header("Content-Type", "application/json")
            .header("User-Agent", "DETE-Auth-Service/1.0")
            .timeout(Duration.ofSeconds(15))
            .POST(HttpRequest.BodyPublishers.ofString(requestBodyJson))
            .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() >= 200 && response.statusCode() < 300) {
      log.info(
          "Successfully delivered verification email via Resend to {} (HTTP {}, response: {})",
          recipientEmail,
          response.statusCode(),
          response.body());
    } else {
      log.error(
          "Resend API returned non-2xx status for {}: HTTP {} - {}",
          recipientEmail,
          response.statusCode(),
          response.body());
      throw new RuntimeException(
          "Resend API error " + response.statusCode() + ": " + response.body());
    }
  }

  private String buildHtmlEmail(String greeting, String otpCode) {
    return """
        <!DOCTYPE html>
        <html>
        <head>
          <meta charset="utf-8">
          <title>DETE Exchange Verification Code</title>
          <style>
            body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; background-color: #0b0e14; color: #e2e8f0; margin: 0; padding: 24px 0; }
            .container { max-width: 520px; margin: 0 auto; background: #131722; border: 1px solid #232936; border-radius: 12px; overflow: hidden; }
            .header { background: #181d28; padding: 24px 32px; border-bottom: 1px solid #232936; }
            .brand { font-size: 14px; font-weight: 700; color: #22d3ee; letter-spacing: 0.15em; text-transform: uppercase; }
            .content { padding: 32px; }
            .title { font-size: 20px; font-weight: 600; color: #f8fafc; margin-top: 0; margin-bottom: 12px; }
            .text { font-size: 14px; line-height: 1.6; color: #94a3b8; margin-bottom: 20px; }
            .otp-card { background: #0b0e14; border: 1px solid #0284c7; border-radius: 8px; padding: 20px; text-align: center; margin: 24px 0; }
            .otp-code { font-family: 'JetBrains Mono', 'Fira Code', 'Courier New', monospace; font-size: 34px; font-weight: 700; letter-spacing: 10px; color: #38bdf8; }
            .otp-hint { font-size: 12px; color: #64748b; margin-top: 8px; text-transform: uppercase; letter-spacing: 0.05em; }
            .footer { padding: 20px 32px; background: #0e1118; border-top: 1px solid #232936; font-size: 12px; color: #64748b; line-height: 1.5; }
            .highlight { color: #f1f5f9; font-weight: 600; }
          </style>
        </head>
        <body>
          <div class="container">
            <div class="header">
              <div class="brand">DETE Institutional Exchange</div>
            </div>
            <div class="content">
              <h1 class="title">Verification Code</h1>
              <p class="text">Hello <span class="highlight">%s</span>,</p>
              <p class="text">You are completing your registration on the Distributed Electronic Trading Exchange (DETE). Please use the one-time passcode below to verify your email address:</p>
              <div class="otp-card">
                <div class="otp-code">%s</div>
                <div class="otp-hint">Valid for 10 minutes</div>
              </div>
              <p class="text" style="font-size: 13px;">Once verified, your institutional account will be initialized and pre-funded with <strong>50,000 USDT</strong> and <strong>50,000 USD</strong> in test liquidity.</p>
            </div>
            <div class="footer">
              If you did not request this verification code, please ignore this email.<br><br>
              &copy; 2026 Distributed Electronic Trading Exchange. All rights reserved.
            </div>
          </div>
        </body>
        </html>
        """.formatted(greeting, otpCode);
  }
}
