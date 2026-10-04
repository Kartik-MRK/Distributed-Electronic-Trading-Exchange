package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;

/** Typed wrapper for a Trade execution identifier. */
public record TradeId(@JsonValue UUID value) {

  @JsonCreator
  public static TradeId of(UUID value) {
    if (value == null) throw new IllegalArgumentException("TradeId must not be null");
    return new TradeId(value);
  }

  public static TradeId of(String value) {
    return of(UUID.fromString(value));
  }

  public static TradeId generate() {
    return new TradeId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
