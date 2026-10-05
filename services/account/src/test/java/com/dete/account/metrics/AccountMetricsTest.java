package com.dete.account.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AccountMetricsTest {

  private MeterRegistry registry;
  private AccountMetrics accountMetrics;

  @BeforeEach
  void setUp() {
    registry = new SimpleMeterRegistry();
    accountMetrics = new AccountMetrics(registry);
  }

  @Test
  @DisplayName("Should increment ledger_entries_total with entry type tag")
  void shouldRecordLedgerEntry() {
    accountMetrics.recordLedgerEntry("DEPOSIT");
    accountMetrics.recordLedgerEntry("SETTLE");
    accountMetrics.recordLedgerEntry("SETTLE");

    double depositCount =
        registry
            .get("ledger_entries_total")
            .tag("type", "DEPOSIT")
            .counter()
            .count();

    double settleCount =
        registry
            .get("ledger_entries_total")
            .tag("type", "SETTLE")
            .counter()
            .count();

    assertThat(depositCount).isEqualTo(1.0);
    assertThat(settleCount).isEqualTo(2.0);
  }

  @Test
  @DisplayName("Should record consumer lag in AccountMetrics")
  void shouldRecordConsumerLag() {
    accountMetrics.recordConsumerLag("trade.executions", "account-group", 5L);
    assertThat(
            registry
                .get("kafka_consumer_lag")
                .tag("topic", "trade.executions")
                .tag("group", "account-group")
                .gauge()
                .value())
        .isEqualTo(5.0);
  }
}
