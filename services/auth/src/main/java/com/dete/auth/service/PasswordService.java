package com.dete.auth.service;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class PasswordService {

  // BCrypt strength 12 as per DETE specification
  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  public String hash(String rawPassword) {
    if (rawPassword == null || rawPassword.isBlank()) {
      throw new IllegalArgumentException("Password cannot be blank");
    }
    return encoder.encode(rawPassword);
  }

  public boolean matches(String rawPassword, String hashedPassword) {
    if (rawPassword == null || hashedPassword == null) {
      return false;
    }
    return encoder.matches(rawPassword, hashedPassword);
  }
}
