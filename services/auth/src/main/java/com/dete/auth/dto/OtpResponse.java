package com.dete.auth.dto;

public record OtpResponse(String email, String message, long expiresInSeconds, String debugOtp) {

  public static OtpResponse of(
      String email, String message, long expiresInSeconds, String debugOtp) {
    return new OtpResponse(email, message, expiresInSeconds, debugOtp);
  }
}
