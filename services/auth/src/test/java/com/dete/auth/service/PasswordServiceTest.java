package com.dete.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PasswordServiceTest {

  private PasswordService passwordService;

  @BeforeEach
  void setUp() {
    passwordService = new PasswordService();
  }

  @Test
  @DisplayName("Should hash password with BCrypt and successfully verify matches")
  void shouldHashAndVerifyPassword() {
    String rawPassword = "SecureTradingPassword123!";

    String hash = passwordService.hash(rawPassword);

    assertThat(hash).isNotNull().startsWith("$2a$12$");
    assertThat(passwordService.matches(rawPassword, hash)).isTrue();
    assertThat(passwordService.matches("WrongPassword", hash)).isFalse();
  }

  @Test
  @DisplayName("Should reject blank passwords during hashing")
  void shouldRejectBlankPassword() {
    assertThatThrownBy(() -> passwordService.hash("")).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> passwordService.hash("   "))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> passwordService.hash(null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("Should return false when verifying null or mismatched inputs")
  void shouldHandleNullInputsGracefully() {
    assertThat(passwordService.matches(null, "$2a$12$somehash")).isFalse();
    assertThat(passwordService.matches("password", null)).isFalse();
  }
}
