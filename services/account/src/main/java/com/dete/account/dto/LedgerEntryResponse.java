package com.dete.account.dto;

import com.dete.account.model.LedgerEntry;
import com.dete.common.domain.types.FixedPoint;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
    long entryId,
    UUID accountId,
    String asset,
    String entryType,
    long amount,
    double amountDisplay,
    UUID referenceId,
    long sequenceNum,
    Instant createdAt) {

  public static LedgerEntryResponse from(LedgerEntry entry) {
    return new LedgerEntryResponse(
        entry.entryId(),
        entry.accountId(),
        entry.asset(),
        entry.entryType().name(),
        entry.amount(),
        FixedPoint.ofScaled(entry.amount()).toDouble(),
        entry.referenceId(),
        entry.sequenceNum(),
        entry.createdAt());
  }
}
