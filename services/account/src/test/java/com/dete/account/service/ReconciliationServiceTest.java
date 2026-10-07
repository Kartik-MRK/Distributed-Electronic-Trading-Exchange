package com.dete.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.account.dto.ReconciliationResult;
import com.dete.account.repository.BalanceRepository;
import com.dete.account.repository.LedgerRepository;
import com.dete.account.repository.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ReconciliationServiceTest {

  private BalanceRepository balanceRepository;
  private LedgerRepository ledgerRepository;
  private OutboxRepository outboxRepository;
  private ObjectMapper objectMapper;
  private ReconciliationService reconciliationService;

  @BeforeEach
  void setUp() {
    balanceRepository = mock(BalanceRepository.class);
    ledgerRepository = mock(LedgerRepository.class);
    outboxRepository = mock(OutboxRepository.class);
    objectMapper = new ObjectMapper();
    reconciliationService =
        new ReconciliationService(
            balanceRepository, ledgerRepository, outboxRepository, objectMapper);
  }

  @Test
  @DisplayName("Reconciliation passes when balances and ledger sums match perfectly")
  void shouldPassWhenBalancesMatchLedger() {
    when(balanceRepository.sumTotalBalancesByAsset())
        .thenReturn(Map.of("USD", 10_000L, "BTC", 500L));
    when(ledgerRepository.sumNetLedgerByAsset()).thenReturn(Map.of("USD", 10_000L, "BTC", 500L));

    ReconciliationResult result = reconciliationService.runReconciliation();

    assertThat(result.balanced()).isTrue();
    assertThat(result.assetCount()).isEqualTo(2);
    assertThat(result.assets().get("USD").drift()).isEqualTo(0L);
    assertThat(result.assets().get("USD").balanced()).isTrue();
    assertThat(result.assets().get("BTC").drift()).isEqualTo(0L);
    assertThat(result.assets().get("BTC").balanced()).isTrue();
  }

  @Test
  @DisplayName("Reconciliation flags drift and publishes alert on mismatch")
  void shouldFlagDiscrepancyAndPublishAlert() {
    when(balanceRepository.sumTotalBalancesByAsset())
        .thenReturn(Map.of("USD", 10_500L, "BTC", 500L));
    when(ledgerRepository.sumNetLedgerByAsset()).thenReturn(Map.of("USD", 10_000L, "BTC", 500L));

    ReconciliationResult result = reconciliationService.runReconciliation();

    assertThat(result.balanced()).isFalse();
    assertThat(result.assets().get("USD").drift()).isEqualTo(500L);
    assertThat(result.assets().get("USD").balanced()).isFalse();
    assertThat(result.assets().get("BTC").drift()).isEqualTo(0L);
    assertThat(result.assets().get("BTC").balanced()).isTrue();

    // Verify alert message saved to outbox
    verify(outboxRepository)
        .save(
            org.mockito.ArgumentMatchers.argThat(
                msg ->
                    msg.topic().equals(ReconciliationService.TOPIC_SYSTEM_ALERTS)
                        && msg.payload().contains("BALANCE_DRIFT")
                        && msg.payload().contains("USD")));
  }
}
