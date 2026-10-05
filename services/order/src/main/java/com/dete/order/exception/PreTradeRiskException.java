package com.dete.order.exception;

public class PreTradeRiskException extends RuntimeException {
  public PreTradeRiskException(String message) {
    super(message);
  }

  public PreTradeRiskException(String message, Throwable cause) {
    super(message, cause);
  }
}
