package com.dete.simulator.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.simulator.bot.InstrumentBot;
import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
public class SimulatorService {

  private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);

  private final SimulatorProperties properties;
  private final ExchangeRestClient restClient;
  private final BotAccountManager accountManager;

  private final Map<Instrument, InstrumentBot> bots = new ConcurrentHashMap<>();
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  private final ScheduledExecutorService initExecutor =
      Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "simulator-init");
            t.setDaemon(true);
            return t;
          });

  public SimulatorService(
      SimulatorProperties properties,
      ExchangeRestClient restClient,
      BotAccountManager accountManager) {
    this.properties = properties;
    this.restClient = restClient;
    this.accountManager = accountManager;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (!properties.isEnabled()) {
      log.info("Simulator is disabled by configuration (simulator.enabled=false).");
      return;
    }

    log.info("Simulator service starting. Scheduling background startup attempts...");
    // Attempt initialization with retry loop
    initExecutor.scheduleWithFixedDelay(this::tryInitialize, 2, 5, TimeUnit.SECONDS);
  }

  private void tryInitialize() {
    if (initialized.get()) {
      return;
    }

    try {
      boolean ready = accountManager.initialize();
      if (!ready) {
        log.warn("Exchange dependencies not fully accessible yet. Retrying in 5s...");
        return;
      }

      for (SimulatorProperties.InstrumentConfig config : properties.getInstruments()) {
        try {
          InstrumentBot bot = new InstrumentBot(config, restClient, accountManager);
          bots.put(bot.getInstrument(), bot);
          bot.populateOrderBook();
        } catch (Exception e) {
          log.warn("Error creating bot for {}: {}", config.getSymbol(), e.getMessage());
        }
      }

      initialized.set(true);
      running.set(true);
      log.info("Simulator successfully initialized with {} active bots.", bots.size());
      initExecutor.shutdown();
    } catch (Exception e) {
      log.warn("Exception during simulator initialization attempt: {}", e.getMessage());
    }
  }

  @Scheduled(fixedDelayString = "${simulator.drift-interval-seconds:10}000", initialDelay = 10000)
  public void scheduledPriceDrift() {
    if (!running.get() || !initialized.get()) return;
    for (InstrumentBot bot : bots.values()) {
      try {
        bot.driftMidPrice();
      } catch (Exception e) {
        log.debug(
            "Error during mid price drift for {}: {}",
            bot.getInstrument().symbol(),
            e.getMessage());
      }
    }
  }

  @Scheduled(fixedDelayString = "${simulator.trade-interval-seconds:3}000", initialDelay = 12000)
  public void scheduledTradeFill() {
    if (!running.get() || !initialized.get()) return;
    for (InstrumentBot bot : bots.values()) {
      try {
        bot.generateFill();
      } catch (Exception e) {
        log.debug(
            "Error during trade generation for {}: {}",
            bot.getInstrument().symbol(),
            e.getMessage());
      }
    }
  }

  @Scheduled(fixedDelayString = "${simulator.refresh-interval-seconds:4}000", initialDelay = 15000)
  public void scheduledOrderRefresh() {
    if (!running.get() || !initialized.get()) return;
    for (InstrumentBot bot : bots.values()) {
      try {
        bot.refreshOrders();
      } catch (Exception e) {
        log.debug(
            "Error during order refresh for {}: {}", bot.getInstrument().symbol(), e.getMessage());
      }
    }
  }

  public void start() {
    running.set(true);
    log.info("Simulator resumed.");
  }

  public void stop() {
    running.set(false);
    log.info("Simulator paused.");
  }

  public boolean isRunning() {
    return running.get();
  }

  public boolean isInitialized() {
    return initialized.get();
  }

  public Map<Instrument, InstrumentBot> getBots() {
    return bots;
  }

  public List<BotStatusDto> getBotStatuses() {
    List<BotStatusDto> list = new ArrayList<>();
    for (InstrumentBot bot : bots.values()) {
      list.add(
          new BotStatusDto(
              bot.getInstrument().symbol(),
              bot.getCurrentMidPrice(),
              bot.getActiveBidCount(),
              bot.getActiveAskCount()));
    }
    return list;
  }

  public record BotStatusDto(
      String instrument, double currentMid, int activeBids, int activeAsks) {}
}
