package com.dete.account.dto;

import java.time.Instant;
import java.util.Map;

public record ReconciliationResult(
    Instant timestamp, boolean balanced, int assetCount, Map<String, AssetReconciliation> assets) {

  public record AssetReconciliation(
      String asset, long balancesSum, long ledgerSum, long drift, boolean balanced) {}
}
