package com.dete.simulator.service;

import java.time.Instant;
import java.util.UUID;

public record SimulationSession(
    UUID sessionId,
    UUID ownerUserId,
    String status,
    int durationSeconds,
    Instant startsAt,
    Instant expiresAt,
    Instant completedAt,
    String completionReason) {}
