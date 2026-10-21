package com.dete.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mail.MailSender;
import org.springframework.mail.SimpleMailMessage;

class EmailOtpServiceTest {

  private MailSender mailSender;
  private EmailOtpService emailOtpService;

  @BeforeEach
  void setUp() {
    mailSender = mock(MailSender.class);
    emailOtpService = new EmailOtpService(mailSender);
  }

  @Test
  @DisplayName("generateOtpCode should produce a 6-digit numeric string")
  void shouldGenerateSixDigitNumericCode() {
    for (int i = 0; i < 50; i++) {
      String code = emailOtpService.generateOtpCode();
      assertThat(code).hasSize(6).matches("^\\d{6}$");
    }
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "trader@dete.io",
        "john.doe@gmail.com",
        "alice+test@exchange.crypto",
        "investor_99@finance.co.uk"
      })
  @DisplayName("isValidEmail should accept well-formed email addresses")
  void shouldAcceptValidEmails(String email) {
    assertThat(emailOtpService.isValidEmail(email)).isTrue();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "",
        "   ",
        "invalid-email",
        "@missingusername.com",
        "missingatsign.com",
        "spaces in@address.com"
      })
  @DisplayName("isValidEmail should reject malformed email addresses")
  void shouldRejectMalformedEmails(String email) {
    assertThat(emailOtpService.isValidEmail(email)).isFalse();
  }

  @Test
  @DisplayName("dispatchOtpEmail sends message through mailSender if present")
  void shouldSendMessageThroughMailSender() {
    emailOtpService.dispatchOtpEmail("user@example.com", "654321", "Test Trader");
    verify(mailSender).send(any(SimpleMailMessage.class));
  }
}
