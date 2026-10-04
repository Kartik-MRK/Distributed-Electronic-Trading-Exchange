package com.dete.common.domain.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Fixed-point monetary/quantity representation.
 *
 * <p>All financial values in DETE are represented as scaled longs. The scale is 8 decimal places,
 * meaning 1 BTC = 1_00000000L, 1 USD = 1_00000000L.
 *
 * <p>This avoids ALL floating-point arithmetic in financial calculations, which is non-negotiable
 * in a trading system (floating-point rounding errors can cause real money discrepancies).
 *
 * <p>Example: 0.5 BTC = 50_000_000L (50 million scaled units)
 */
public record FixedPoint(@JsonValue long scaledValue) implements Comparable<FixedPoint> {

  /** The number of decimal places of precision. 8 decimal places = nano-scale for crypto. */
  public static final long SCALE = 100_000_000L; // 10^8

  public static final FixedPoint ZERO = new FixedPoint(0L);
  public static final FixedPoint ONE = new FixedPoint(SCALE);

  @JsonCreator
  public static FixedPoint ofScaled(long scaledValue) {
    return new FixedPoint(scaledValue);
  }

  /**
   * Create from a human-readable double. Only use at boundaries (config/input parsing), NOT in hot
   * path calculations.
   */
  public static FixedPoint ofDouble(double value) {
    return new FixedPoint(Math.round(value * SCALE));
  }

  /** Create from a whole number (e.g., 1000 USD). */
  public static FixedPoint ofWhole(long wholeUnits) {
    return new FixedPoint(wholeUnits * SCALE);
  }

  public FixedPoint add(FixedPoint other) {
    return new FixedPoint(this.scaledValue + other.scaledValue);
  }

  public FixedPoint subtract(FixedPoint other) {
    return new FixedPoint(this.scaledValue - other.scaledValue);
  }

  /**
   * Multiply two fixed-point values. Uses 128-bit intermediate to avoid overflow.
   *
   * <p>a * b / SCALE — e.g., price * quantity / SCALE = notional value.
   */
  public FixedPoint multiply(FixedPoint other) {
    // Use BigInteger for intermediate to avoid long overflow on large values
    java.math.BigInteger result =
        java.math.BigInteger.valueOf(this.scaledValue)
            .multiply(java.math.BigInteger.valueOf(other.scaledValue))
            .divide(java.math.BigInteger.valueOf(SCALE));
    return new FixedPoint(result.longValueExact());
  }

  public boolean isPositive() {
    return scaledValue > 0;
  }

  public boolean isNegative() {
    return scaledValue < 0;
  }

  public boolean isZero() {
    return scaledValue == 0;
  }

  public boolean isGreaterThan(FixedPoint other) {
    return this.scaledValue > other.scaledValue;
  }

  public boolean isGreaterThanOrEqual(FixedPoint other) {
    return this.scaledValue >= other.scaledValue;
  }

  public boolean isLessThan(FixedPoint other) {
    return this.scaledValue < other.scaledValue;
  }

  /** Convert to a human-readable double for display purposes only. */
  public double toDouble() {
    return (double) scaledValue / SCALE;
  }

  @Override
  public int compareTo(FixedPoint other) {
    return Long.compare(this.scaledValue, other.scaledValue);
  }

  @Override
  public String toString() {
    // Format as decimal string: scaledValue / SCALE with up to 8 decimal places
    long whole = scaledValue / SCALE;
    long fraction = Math.abs(scaledValue % SCALE);
    return String.format("%d.%08d", whole, fraction).replaceAll("0+$", "").replaceAll("\\.$", "");
  }
}
