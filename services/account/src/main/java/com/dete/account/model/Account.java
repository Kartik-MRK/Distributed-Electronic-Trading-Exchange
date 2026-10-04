package com.dete.account.model;

import java.time.Instant;
import java.util.UUID;

public record Account(UUID accountId, Instant createdAt) {
  public static Account createNew(UUID accountId) {
    return new Account(accountId, Instant.now());
  }
}
