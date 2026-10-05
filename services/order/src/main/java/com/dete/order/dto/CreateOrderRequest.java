package com.dete.order.dto;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateOrderRequest(
    @NotNull(message = "Instrument is required") Instrument instrument,
    @NotNull(message = "Order side (BUY/SELL) is required") OrderSide side,
    @NotNull(message = "Order type (LIMIT/MARKET/IOC/FOK) is required") OrderType orderType,
    Long price,
    @Positive(message = "Quantity must be strictly positive") long quantity) {

  public boolean isValid() {
    if (orderType == OrderType.LIMIT || orderType == OrderType.IOC || orderType == OrderType.FOK) {
      return price != null && price > 0 && quantity > 0;
    }
    return quantity > 0;
  }
}
