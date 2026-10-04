package com.dete.account.model;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntry(
    long entryId,
    UUID accountId,
    String asset,
    LedgerEntryType entryType,
    long amount,
    UUID referenceId,
    long sequenceNum,
    Instant createdAt) {

  public static LedgerEntry createNew(
      UUID accountId,
      String asset,
      LedgerEntryType entryType,
      long amount,
      UUID referenceId,
      long sequenceNum) {
    return new LedgerEntry(
        0L, accountId, asset, entryType, amount, referenceId, sequenceNum, Instant.now());
  }
}
