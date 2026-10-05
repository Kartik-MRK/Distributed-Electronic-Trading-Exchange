package com.dete.order.exception;

public class RiskServiceUnavailableException extends PreTradeRiskException {

  public RiskServiceUnavailableException(String message) {
    super(message);
  }

  public RiskServiceUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
