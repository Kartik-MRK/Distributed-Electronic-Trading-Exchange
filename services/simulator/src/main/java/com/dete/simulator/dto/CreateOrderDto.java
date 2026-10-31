package com.dete.simulator.dto;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;

public record CreateOrderDto(
    Instrument instrument,
    OrderSide side,
    OrderType orderType,
    Long price,
    long quantity,
    java.util.UUID sessionId) {
  public CreateOrderDto(
      Instrument instrument, OrderSide side, OrderType orderType, Long price, long quantity) {
    this(instrument, side, orderType, price, quantity, null);
  }
}
