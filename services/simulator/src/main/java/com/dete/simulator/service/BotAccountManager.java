package com.dete.simulator.service;

import com.dete.common.domain.types.FixedPoint;
import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.AuthDtos;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BotAccountManager {

  private static final Logger log = LoggerFactory.getLogger(BotAccountManager.class);

  private final ExchangeRestClient restClient;
  private final SimulatorProperties properties;

  private final BotSession makerSession =
      new BotSession("bot_maker", "bot_maker@dete.io", "BotMakerPass123!");
  private final BotSession takerSession =
      new BotSession("bot_taker", "bot_taker@dete.io", "BotTakerPass123!");
  private UUID demoUserId;

  public BotAccountManager(ExchangeRestClient restClient, SimulatorProperties properties) {
    this.restClient = restClient;
    this.properties = properties;
  }

  public synchronized boolean initialize() {
    log.info("Initializing simulator bot accounts and balances...");
    boolean makerReady = ensureBotSession(makerSession);
    boolean takerReady = ensureBotSession(takerSession);

    if (makerReady) {
      fundBotAccount(makerSession.userId);
    }
    if (takerReady) {
      fundBotAccount(takerSession.userId);
    }

    if (properties.isSeedDemo()) {
      seedDemoAccount();
    }

    return makerReady && takerReady;
  }

  private boolean ensureBotSession(BotSession session) {
    AuthDtos.AuthResponse auth = restClient.login(session.username, session.password);
    if (auth == null) {
      log.info("Creating account for {}...", session.username);
      restClient.register(session.username, session.email, session.password);
      auth = restClient.login(session.username, session.password);
    }

    if (auth != null) {
      session.userId = auth.userId();
      session.accessToken = auth.accessToken();
      session.expiresAt = Instant.now().plusSeconds(Math.max(60, auth.expiresIn() - 60));
      log.info("Bot account '{}' authenticated with userId {}", session.username, session.userId);
      return true;
    } else {
      log.warn("Failed to authenticate bot account '{}'", session.username);
      return false;
    }
  }

  private void fundBotAccount(UUID accountId) {
    if (accountId == null) return;
    long scale = FixedPoint.SCALE;
    restClient.deposit(accountId, "USD", 100_000_000L * scale, UUID.randomUUID());
    restClient.deposit(accountId, "BTC", 10_000L * scale, UUID.randomUUID());
    restClient.deposit(accountId, "ETH", 100_000L * scale, UUID.randomUUID());
    restClient.deposit(accountId, "SOL", 1_000_000L * scale, UUID.randomUUID());
    log.info("Funded bot account {} with initial market maker liquidity", accountId);
  }

  public void seedDemoAccount() {
    try {
      AuthDtos.AuthResponse auth = restClient.login("demo", "DemoPassword123!");
      if (auth == null) {
        restClient.register("demo", "demo@dete.io", "DemoPassword123!");
        auth = restClient.login("demo", "DemoPassword123!");
      }

      if (auth != null) {
        demoUserId = auth.userId();
        long scale = FixedPoint.SCALE;
        restClient.deposit(demoUserId, "USD", 10_000L * scale, UUID.randomUUID());
        restClient.deposit(demoUserId, "BTC", 1L * scale, UUID.randomUUID());
        restClient.deposit(demoUserId, "ETH", 5L * scale, UUID.randomUUID());
        restClient.deposit(demoUserId, "SOL", 50L * scale, UUID.randomUUID());
        log.info("Seeded Demo user (ID: {}) with 10,000 USD, 1 BTC, 5 ETH, 50 SOL", demoUserId);
      } else {
        log.warn("Unable to log in as demo user to seed funds");
      }
    } catch (Exception e) {
      log.warn("Failed to seed demo account: {}", e.getMessage());
    }
  }

  public String getMakerToken() {
    refreshSessionIfNeeded(makerSession);
    return makerSession.accessToken;
  }

  public String getTakerToken() {
    refreshSessionIfNeeded(takerSession);
    return takerSession.accessToken;
  }

  public UUID getMakerAccountId() {
    return makerSession.userId;
  }

  public UUID getTakerAccountId() {
    return takerSession.userId;
  }

  public UUID getDemoUserId() {
    return demoUserId;
  }

  private void refreshSessionIfNeeded(BotSession session) {
    if (session.accessToken == null
        || session.expiresAt == null
        || Instant.now().isAfter(session.expiresAt)) {
      ensureBotSession(session);
    }
  }

  private static class BotSession {
    final String username;
    final String email;
    final String password;
    UUID userId;
    String accessToken;
    Instant expiresAt;

    BotSession(String username, String email, String password) {
      this.username = username;
      this.email = email;
      this.password = password;
    }
  }
}
