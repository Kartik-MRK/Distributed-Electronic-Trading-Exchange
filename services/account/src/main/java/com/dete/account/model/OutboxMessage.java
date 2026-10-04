package com.dete.account.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxMessage(
    UUID outboxId, String topic, String payload, Instant createdAt, boolean published) {

  public static OutboxMessage createNew(String topic, String payload) {
    return new OutboxMessage(UUID.randomUUID(), topic, payload, Instant.now(), false);
  }
}
