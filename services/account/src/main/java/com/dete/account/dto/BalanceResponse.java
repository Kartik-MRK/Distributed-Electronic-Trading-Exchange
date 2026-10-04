package com.dete.account.dto;

import com.dete.common.domain.types.FixedPoint;
import java.util.UUID;

public record BalanceResponse(
    UUID accountId,
    String asset,
    long available,
    long reserved,
    long total,
    double availableDisplay,
    double reservedDisplay,
    double totalDisplay) {

  public static BalanceResponse of(UUID accountId, String asset, long available, long reserved) {
    long total = available + reserved;
    return new BalanceResponse(
        accountId,
        asset,
        available,
        reserved,
        total,
        FixedPoint.ofScaled(available).toDouble(),
        FixedPoint.ofScaled(reserved).toDouble(),
        FixedPoint.ofScaled(total).toDouble());
  }
}
