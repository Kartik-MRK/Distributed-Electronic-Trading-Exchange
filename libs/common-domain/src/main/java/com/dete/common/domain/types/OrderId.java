package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;

/**
 * Typed wrapper around a UUID representing an Order identifier. Prevents accidental mixing of
 * OrderId with other UUID-based IDs.
 */
public record OrderId(@JsonValue UUID value) {

  @JsonCreator
  public static OrderId of(UUID value) {
    if (value == null) throw new IllegalArgumentException("OrderId value must not be null");
    return new OrderId(value);
  }

  public static OrderId of(String value) {
    return of(UUID.fromString(value));
  }

  public static OrderId generate() {
    return new OrderId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
