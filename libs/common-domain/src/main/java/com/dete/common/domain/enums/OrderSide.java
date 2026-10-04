package com.dete.common.domain.enums;

/** Side of a trade order. */
public enum OrderSide {
  BUY,
  SELL;

  public OrderSide opposite() {
    return this == BUY ? SELL : BUY;
  }
}
