package com.dete.simulator.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.AuthDtos;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BotAccountManagerTest {

  private ExchangeRestClient restClient;
  private SimulatorProperties properties;
  private BotAccountManager manager;

  @BeforeEach
  void setUp() {
    restClient = mock(ExchangeRestClient.class);
    properties = new SimulatorProperties();
    properties.setSeedDemo(true);

    manager = new BotAccountManager(restClient, properties);
  }

  @Test
  @DisplayName("Should authenticate bots and seed demo account funds")
  void shouldInitializeBotsAndSeedDemo() {
    UUID makerId = UUID.randomUUID();
    UUID takerId = UUID.randomUUID();
    UUID demoId = UUID.randomUUID();

    when(restClient.login(eq("bot_maker"), any()))
        .thenReturn(
            new AuthDtos.AuthResponse(makerId, "bot_maker", "maker-jwt", "refresh", "Bearer", 900));
    when(restClient.login(eq("bot_taker"), any()))
        .thenReturn(
            new AuthDtos.AuthResponse(takerId, "bot_taker", "taker-jwt", "refresh", "Bearer", 900));
    when(restClient.login(eq("demo"), any()))
        .thenReturn(
            new AuthDtos.AuthResponse(demoId, "demo", "demo-jwt", "refresh", "Bearer", 900));

    boolean success = manager.initialize();

    assertThat(success).isTrue();
    assertThat(manager.getMakerToken()).isEqualTo("maker-jwt");
    assertThat(manager.getTakerToken()).isEqualTo("taker-jwt");
    assertThat(manager.getMakerAccountId()).isEqualTo(makerId);
    assertThat(manager.getTakerAccountId()).isEqualTo(takerId);
    assertThat(manager.getDemoUserId()).isEqualTo(demoId);

    // Verify funding deposits for maker, taker, and demo
    verify(restClient, atLeastOnce()).deposit(eq(makerId), eq("USD"), any(Long.class), any());
    verify(restClient, atLeastOnce()).deposit(eq(takerId), eq("USD"), any(Long.class), any());
    verify(restClient, atLeastOnce()).deposit(eq(demoId), eq("USD"), any(Long.class), any());
    verify(restClient, atLeastOnce()).deposit(eq(demoId), eq("BTC"), any(Long.class), any());
    verify(restClient, atLeastOnce()).deposit(eq(demoId), eq("ETH"), any(Long.class), any());
    verify(restClient, atLeastOnce()).deposit(eq(demoId), eq("SOL"), any(Long.class), any());
  }
}
