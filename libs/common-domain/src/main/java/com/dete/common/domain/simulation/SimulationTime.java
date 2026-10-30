package com.dete.common.domain.simulation;

import java.time.Instant;

/** Pure time arithmetic, independent of browser time and Kafka delivery time. */
public final class SimulationTime {
  private SimulationTime() {}

  public static long elapsed(
      long base,
      long highWatermark,
      Instant started,
      Instant deadline,
      Instant lease,
      Instant now,
      boolean active) {
    if (!active || started == null || deadline == null || lease == null || !now.isBefore(lease))
      return highWatermark;
    long delta =
        Math.max(0, Math.min(now.toEpochMilli(), deadline.toEpochMilli()) - started.toEpochMilli());
    return Math.max(highWatermark, Math.addExact(base, delta));
  }

  public static Instant windowStart(Instant epoch, long elapsedMs, long intervalMs) {
    if (elapsedMs < 0 || intervalMs <= 0) throw new IllegalArgumentException("Invalid candle time");
    return epoch.plusMillis((elapsedMs / intervalMs) * intervalMs);
  }
}
