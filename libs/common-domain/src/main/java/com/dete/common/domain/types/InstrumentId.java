package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/** Typed wrapper for an Instrument identifier (e.g., BTC_USD, ETH_USD). */
public record InstrumentId(@JsonValue String value) implements Comparable<InstrumentId> {

  public InstrumentId {
    Objects.requireNonNull(value, "InstrumentId must not be null");
    if (value.isBlank()) {
      throw new IllegalArgumentException("InstrumentId must not be blank");
    }
  }

  @JsonCreator
  public static InstrumentId of(String value) {
    return new InstrumentId(value.trim().toUpperCase());
  }

  @Override
  public int compareTo(InstrumentId other) {
    return this.value.compareTo(other.value);
  }

  @Override
  public String toString() {
    return value;
  }
}
