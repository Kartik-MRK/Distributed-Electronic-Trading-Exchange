package com.dete.auth.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record OtpResponse(String email, String message, long expiresInSeconds, String debugOtp) {

  public static OtpResponse of(
      String email, String message, long expiresInSeconds, String debugOtp) {
    return new OtpResponse(email, message, expiresInSeconds, debugOtp);
  }

  public static OtpResponse of(String email, String message, long expiresInSeconds) {
    return new OtpResponse(email, message, expiresInSeconds, null);
  }
}
