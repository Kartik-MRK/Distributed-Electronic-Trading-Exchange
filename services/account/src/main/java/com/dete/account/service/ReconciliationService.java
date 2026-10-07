package com.dete.account.service;

import com.dete.account.dto.ReconciliationResult;
import com.dete.account.dto.ReconciliationResult.AssetReconciliation;
import com.dete.account.repository.BalanceRepository;
import com.dete.account.repository.LedgerRepository;
import com.dete.account.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);
  public static final String TOPIC_SYSTEM_ALERTS = "system.alerts";

  private final BalanceRepository balanceRepository;
  private final LedgerRepository ledgerRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  private volatile ReconciliationResult lastResult;

  public ReconciliationService(
      BalanceRepository balanceRepository,
      LedgerRepository ledgerRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper) {
    this.balanceRepository = balanceRepository;
    this.ledgerRepository = ledgerRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
    this.lastResult = new ReconciliationResult(Instant.now(), true, 0, Collections.emptyMap());
  }

  @Scheduled(cron = "${account.reconciliation.cron:0 0 0 * * ?}")
  public void scheduledReconciliation() {
    log.info("Starting scheduled nightly ledger reconciliation run...");
    runReconciliation();
  }

  public ReconciliationResult runReconciliation() {
    Instant now = Instant.now();
    Map<String, Long> balancesByAsset = balanceRepository.sumTotalBalancesByAsset();
    Map<String, Long> ledgerByAsset = ledgerRepository.sumNetLedgerByAsset();

    Set<String> allAssets = new HashSet<>();
    allAssets.addAll(balancesByAsset.keySet());
    allAssets.addAll(ledgerByAsset.keySet());

    boolean overallBalanced = true;
    Map<String, AssetReconciliation> assetResults = new HashMap<>();

    for (String asset : allAssets) {
      long balancesSum = balancesByAsset.getOrDefault(asset, 0L);
      long ledgerSum = ledgerByAsset.getOrDefault(asset, 0L);
      long drift = balancesSum - ledgerSum;
      boolean isAssetBalanced = (drift == 0L);

      if (!isAssetBalanced) {
        overallBalanced = false;
        log.error(
            "CRITICAL: Balance drift detected for asset {}: balancesSum={}, ledgerSum={}, drift={}",
            asset,
            balancesSum,
            ledgerSum,
            drift);

        publishAlert(asset, balancesSum, ledgerSum, drift);
      }

      assetResults.put(
          asset, new AssetReconciliation(asset, balancesSum, ledgerSum, drift, isAssetBalanced));
    }

    if (overallBalanced) {
      log.info(
          "Ledger reconciliation PASSED: {} assets checked, 0 drift detected.", allAssets.size());
    }

    ReconciliationResult result =
        new ReconciliationResult(now, overallBalanced, allAssets.size(), assetResults);
    this.lastResult = result;
    return result;
  }

  public ReconciliationResult getLastResult() {
    return lastResult;
  }

  private void publishAlert(String asset, long balancesSum, long ledgerSum, long drift) {
    try {
      Map<String, Object> alertPayload =
          Map.of(
              "alertId",
              UUID.randomUUID().toString(),
              "alertType",
              "BALANCE_DRIFT",
              "severity",
              "CRITICAL",
              "asset",
              asset,
              "balancesSum",
              balancesSum,
              "ledgerSum",
              ledgerSum,
              "drift",
              drift,
              "timestamp",
              Instant.now().toString());
      String json = objectMapper.writeValueAsString(alertPayload);
      outboxRepository.save(
          com.dete.account.model.OutboxMessage.createNew(TOPIC_SYSTEM_ALERTS, json));
    } catch (Exception e) {
      log.error("Failed to enqueue system alert for reconciliation drift on asset {}", asset, e);
    }
  }
}
