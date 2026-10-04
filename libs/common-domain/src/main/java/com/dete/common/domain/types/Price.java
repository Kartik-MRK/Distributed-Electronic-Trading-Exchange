package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object representing a unit price in fixed-point format (8 decimal places). Floating point
 * operations are strictly prohibited.
 */
public record Price(@JsonValue FixedPoint value) implements Comparable<Price> {

  public static final Price ZERO = new Price(FixedPoint.ZERO);

  public Price {
    if (value == null) {
      throw new IllegalArgumentException("Price value must not be null");
    }
    if (value.isNegative()) {
      throw new IllegalArgumentException("Price cannot be negative: " + value);
    }
  }

  @JsonCreator
  public static Price ofScaled(long scaled) {
    return new Price(FixedPoint.ofScaled(scaled));
  }

  public static Price ofDouble(double price) {
    return new Price(FixedPoint.ofDouble(price));
  }

  public static Price ofWhole(long wholeUnits) {
    return new Price(FixedPoint.ofWhole(wholeUnits));
  }

  public long scaledValue() {
    return value.scaledValue();
  }

  public Money multiply(Quantity quantity) {
    return Money.ofScaled(this.value.multiply(quantity.value()).scaledValue());
  }

  public boolean isZero() {
    return value.isZero();
  }

  public boolean isPositive() {
    return value.isPositive();
  }

  public boolean isGreaterThan(Price other) {
    return this.value.isGreaterThan(other.value());
  }

  public boolean isGreaterThanOrEqual(Price other) {
    return this.value.isGreaterThanOrEqual(other.value());
  }

  public boolean isLessThan(Price other) {
    return this.value.isLessThan(other.value());
  }

  public double toDouble() {
    return value.toDouble();
  }

  @Override
  public int compareTo(Price other) {
    return this.value.compareTo(other.value());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
