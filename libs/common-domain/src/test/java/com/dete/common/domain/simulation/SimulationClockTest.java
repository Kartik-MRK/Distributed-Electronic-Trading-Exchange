package com.dete.common.domain.simulation;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class SimulationClockTest {
  private final Instant start = Instant.parse("2026-10-30T10:23:17.123Z");

  @Test
  void activeMinutesAccumulateButIdleDaysDoNot() {
    assertEquals(
        300000,
        SimulationTime.elapsed(
            0,
            0,
            start,
            start.plusSeconds(300),
            start.plusSeconds(301),
            start.plusSeconds(300),
            true));
    Instant resumed = start.plusSeconds(172800);
    assertEquals(
        600000,
        SimulationTime.elapsed(
            300000,
            300000,
            resumed,
            resumed.plusSeconds(300),
            resumed.plusSeconds(301),
            resumed.plusSeconds(300),
            true));
    assertEquals(
        600000,
        SimulationTime.elapsed(
            300000,
            600000,
            resumed,
            resumed.plusSeconds(300),
            resumed.plusSeconds(301),
            resumed.plusSeconds(172800),
            false));
  }

  @Test
  void expiredLeaseFreezesAtPersistedHighWatermark() {
    assertEquals(
        1250,
        SimulationTime.elapsed(
            0,
            1250,
            start,
            start.plusSeconds(300),
            start.plusSeconds(2),
            start.plusSeconds(100),
            true));
    assertEquals(
        1250,
        SimulationTime.elapsed(
            0,
            1250,
            start,
            start.plusSeconds(300),
            start.plusSeconds(2),
            start.plusMillis(1100),
            true));
  }

  @Test
  void candleBoundariesAreRelativeToInitialization() {
    assertEquals(start, SimulationTime.windowStart(start, 59999, 60000));
    assertEquals(start.plusSeconds(60), SimulationTime.windowStart(start, 60000, 60000));
    assertEquals(start.plusSeconds(240), SimulationTime.windowStart(start, 299999, 60000));
    assertThrows(
        IllegalArgumentException.class, () -> SimulationTime.windowStart(start, -1, 60000));
  }
}
