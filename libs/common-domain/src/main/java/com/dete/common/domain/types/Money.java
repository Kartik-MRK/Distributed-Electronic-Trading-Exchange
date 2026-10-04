package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object representing a monetary amount / balance in fixed-point format (8 decimal places).
 * Floating point operations are strictly prohibited.
 */
public record Money(@JsonValue FixedPoint value) implements Comparable<Money> {

  public static final Money ZERO = new Money(FixedPoint.ZERO);

  public Money {
    if (value == null) {
      throw new IllegalArgumentException("Money value must not be null");
    }
  }

  @JsonCreator
  public static Money ofScaled(long scaled) {
    return new Money(FixedPoint.ofScaled(scaled));
  }

  public static Money ofDouble(double amount) {
    return new Money(FixedPoint.ofDouble(amount));
  }

  public static Money ofWhole(long wholeUnits) {
    return new Money(FixedPoint.ofWhole(wholeUnits));
  }

  public long scaledValue() {
    return value.scaledValue();
  }

  public Money add(Money other) {
    return new Money(this.value.add(other.value()));
  }

  public Money subtract(Money other) {
    return new Money(this.value.subtract(other.value()));
  }

  public boolean isZero() {
    return value.isZero();
  }

  public boolean isPositive() {
    return value.isPositive();
  }

  public boolean isNegative() {
    return value.isNegative();
  }

  public boolean isGreaterThan(Money other) {
    return this.value.isGreaterThan(other.value());
  }

  public boolean isGreaterThanOrEqual(Money other) {
    return this.value.isGreaterThanOrEqual(other.value());
  }

  public boolean isLessThan(Money other) {
    return this.value.isLessThan(other.value());
  }

  public double toDouble() {
    return value.toDouble();
  }

  @Override
  public int compareTo(Money other) {
    return this.value.compareTo(other.value());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
