package com.dete.order.model;

import java.time.Instant;
import java.util.UUID;

/** Representation of an outbox event stored in order_svc.outbox. */
public record OutboxRecord(
    UUID outboxId,
    String topic,
    String key,
    String payload,
    Instant createdAt,
    boolean published) {}
