package com.dete.common.domain.simulation;

import java.time.Instant;
import java.util.UUID;

public record SimulationContext(
    UUID epochId,
    UUID sessionId,
    long fence,
    Instant initializedAt,
    Instant marketTime,
    long elapsedMs,
    Instant realTime) {}
