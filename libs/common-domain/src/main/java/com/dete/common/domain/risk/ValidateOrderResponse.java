package com.dete.common.domain.risk;

/** Response payload returned by RiskService after evaluating pre-trade risk rules. */
public record ValidateOrderResponse(boolean approved, String rejectionReason) {

  public static ValidateOrderResponse approve() {
    return new ValidateOrderResponse(true, "");
  }

  public static ValidateOrderResponse reject(String reason) {
    return new ValidateOrderResponse(
        false, reason != null ? reason : "Pre-trade risk check failed");
  }
}
