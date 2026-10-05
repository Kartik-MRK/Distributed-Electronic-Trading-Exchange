package com.dete.common.domain.risk;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderType;
import java.util.UUID;

/** Request payload for pre-trade risk validation via gRPC or internal API. */
public record ValidateOrderRequest(
    UUID orderId,
    UUID accountId,
    Instrument instrument,
    OrderSide side,
    OrderType orderType,
    Long price,
    long quantity) {

  public static ValidateOrderRequest of(
      UUID orderId,
      UUID accountId,
      Instrument instrument,
      OrderSide side,
      OrderType orderType,
      Long price,
      long quantity) {
    return new ValidateOrderRequest(
        orderId, accountId, instrument, side, orderType, price, quantity);
  }
}
