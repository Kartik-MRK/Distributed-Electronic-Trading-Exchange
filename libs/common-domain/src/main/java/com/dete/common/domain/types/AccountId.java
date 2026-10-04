package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.UUID;

/** Typed wrapper for an Account (User) identifier. */
public record AccountId(@JsonValue UUID value) {

  @JsonCreator
  public static AccountId of(UUID value) {
    if (value == null) throw new IllegalArgumentException("AccountId must not be null");
    return new AccountId(value);
  }

  public static AccountId of(String value) {
    return of(UUID.fromString(value));
  }

  public static AccountId generate() {
    return new AccountId(UUID.randomUUID());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
