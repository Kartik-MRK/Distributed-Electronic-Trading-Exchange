package com.dete.marketdata.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.time.Duration;

/** Standard OHLCV candle aggregation intervals for the Market Data Service. */
public enum Interval {
  ONE_MINUTE("1m", Duration.ofMinutes(1).toMillis()),
  FIVE_MINUTES("5m", Duration.ofMinutes(5).toMillis()),
  FIFTEEN_MINUTES("15m", Duration.ofMinutes(15).toMillis()),
  ONE_HOUR("1h", Duration.ofHours(1).toMillis()),
  ONE_DAY("1d", Duration.ofDays(1).toMillis());

  private final String code;
  private final long durationMs;

  Interval(String code, long durationMs) {
    this.code = code;
    this.durationMs = durationMs;
  }

  @JsonValue
  public String getCode() {
    return code;
  }

  public long getDurationMs() {
    return durationMs;
  }

  @JsonCreator
  public static Interval fromCode(String code) {
    if (code == null) {
      return ONE_MINUTE;
    }
    for (Interval i : values()) {
      if (i.code.equalsIgnoreCase(code.trim()) || i.name().equalsIgnoreCase(code.trim())) {
        return i;
      }
    }
    throw new IllegalArgumentException(
        "Unsupported interval: " + code + ". Supported: 1m, 5m, 15m, 1h, 1d");
  }
}
