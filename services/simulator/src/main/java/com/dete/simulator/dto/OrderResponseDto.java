package com.dete.simulator.dto;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.enums.OrderSide;
import com.dete.common.domain.enums.OrderStatus;
import com.dete.common.domain.enums.OrderType;
import java.util.UUID;

public record OrderResponseDto(
    UUID orderId,
    UUID accountId,
    Instrument instrument,
    OrderSide side,
    OrderType orderType,
    OrderStatus status,
    Long price,
    long quantity,
    long filledQuantity,
    long remainingQuantity) {}
