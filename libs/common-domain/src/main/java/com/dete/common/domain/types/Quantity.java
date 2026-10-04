package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Value object representing an asset quantity in fixed-point format (8 decimal places). Floating
 * point operations are strictly prohibited.
 */
public record Quantity(@JsonValue FixedPoint value) implements Comparable<Quantity> {

  public static final Quantity ZERO = new Quantity(FixedPoint.ZERO);

  public Quantity {
    if (value == null) {
      throw new IllegalArgumentException("Quantity value must not be null");
    }
    if (value.isNegative()) {
      throw new IllegalArgumentException("Quantity cannot be negative: " + value);
    }
  }

  @JsonCreator
  public static Quantity ofScaled(long scaled) {
    return new Quantity(FixedPoint.ofScaled(scaled));
  }

  public static Quantity ofDouble(double qty) {
    return new Quantity(FixedPoint.ofDouble(qty));
  }

  public static Quantity ofWhole(long wholeUnits) {
    return new Quantity(FixedPoint.ofWhole(wholeUnits));
  }

  public long scaledValue() {
    return value.scaledValue();
  }

  public Quantity add(Quantity other) {
    return new Quantity(this.value.add(other.value()));
  }

  public Quantity subtract(Quantity other) {
    FixedPoint res = this.value.subtract(other.value());
    if (res.isNegative()) {
      throw new IllegalArgumentException("Quantity subtraction resulted in negative value");
    }
    return new Quantity(res);
  }

  public boolean isZero() {
    return value.isZero();
  }

  public boolean isPositive() {
    return value.isPositive();
  }

  public boolean isGreaterThan(Quantity other) {
    return this.value.isGreaterThan(other.value());
  }

  public boolean isGreaterThanOrEqual(Quantity other) {
    return this.value.isGreaterThanOrEqual(other.value());
  }

  public boolean isLessThan(Quantity other) {
    return this.value.isLessThan(other.value());
  }

  public double toDouble() {
    return value.toDouble();
  }

  @Override
  public int compareTo(Quantity other) {
    return this.value.compareTo(other.value());
  }

  @Override
  public String toString() {
    return value.toString();
  }
}
