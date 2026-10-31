package com.dete.simulator.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.simulation.MarketClockSnapshot;
import com.dete.simulation.control.SimulationControlRepository;
import com.dete.simulator.bot.InstrumentBot;
import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.OrderResponseDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SimulatorService {
  private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
  private static final int BOT_CLEANUP_TIMEOUT_SECONDS = 60;
  @org.springframework.beans.factory.annotation.Autowired private SessionFinalizer finalizer;
  private final SimulatorProperties properties;
  private final ExchangeRestClient restClient;
  private final BotAccountManager accountManager;
  private final JdbcTemplate jdbc;
  private final SimulationControlRepository control;
  private final MeterRegistry meters;
  private final Map<Instrument, InstrumentBot> bots = new ConcurrentHashMap<>();
  private final AtomicBoolean workerRunning = new AtomicBoolean();
  private final AtomicBoolean trading = new AtomicBoolean();
  private final AtomicLong nextBotOrderSlotNanos = new AtomicLong();

  public SimulatorService(
      SimulatorProperties properties,
      ExchangeRestClient restClient,
      BotAccountManager accountManager,
      JdbcTemplate jdbc,
      TransactionTemplate transactions,
      MeterRegistry meters) {
    this.properties = properties;
    this.restClient = restClient;
    this.accountManager = accountManager;
    this.jdbc = jdbc;
    this.control = new SimulationControlRepository(jdbc, transactions);
    this.meters = meters;
  }

  public MarketClockSnapshot getClock() {
    return control.snapshot();
  }

  public SimulationSession getStatus() {
    return session(control.snapshot());
  }

  private SimulationSession session(MarketClockSnapshot s) {
    if (s.sessionId() == null) return null;
    return new SimulationSession(
        s.sessionId(),
        s.ownerUserId(),
        s.status(),
        s.durationSeconds(),
        s.startsAt(),
        s.expiresAt(),
        null,
        s.completionReason());
  }

  public synchronized StartResult start(UUID userId, int seconds) {
    if (!properties.isEnabled()) throw new IllegalStateException("Market simulation is disabled");
    var previous = control.snapshot();
    if (previous.status().equals("STOPPING")
        || (previous.status().equals("STARTING")
            && previous.leaseUntil() != null
            && !previous.serverNow().isBefore(previous.leaseUntil()))) {
      if (workerRunning.get()) return new StartResult(false, session(previous));
      control.beginStopping(previous.sessionId(), previous.fence(), "RECOVERED_EXPIRED");
      finalizeSession(previous);
      control.complete(previous.sessionId(), previous.fence());
    }
    var claim = control.claim(userId, seconds);
    if (!claim.started()) return new StartResult(false, session(claim.snapshot()));
    var s = claim.snapshot();
    try {
      if (!accountManager.initializeSimulationAccounts())
        throw new IllegalStateException("Bot accounts unavailable");
      buildBots(s);
      var active = control.activate(s.sessionId(), s.fence());
      workerRunning.set(true);
      trading.set(true);
      nextBotOrderSlotNanos.set(0);
      Thread.ofPlatform()
          .daemon()
          .name("market-simulation-" + s.sessionId())
          .start(() -> runSession(active));
      meters.counter("market.simulation.sessions.started").increment();
      return new StartResult(true, session(active));
    } catch (RuntimeException e) {
      control.beginStopping(s.sessionId(), s.fence(), "START_FAILED");
      throw e;
    }
  }

  private void runSession(MarketClockSnapshot session) {
    var heartbeat =
        Executors.newSingleThreadScheduledExecutor(
            r -> {
              var t = new Thread(r, "market-clock-heartbeat");
              t.setDaemon(true);
              return t;
            });
    heartbeat.scheduleAtFixedRate(
        () -> {
          try {
            if (!control.checkpoint(session.sessionId(), session.fence()).tradingAllowed())
              trading.set(false);
          } catch (RuntimeException e) {
            trading.set(false);
            log.error("Market clock heartbeat failed", e);
          }
        },
        0,
        250,
        TimeUnit.MILLISECONDS);
    String reason = "DEADLINE_REACHED";
    try {
      for (int level = 0; level < 15 && trading.get(); level++) {
        for (var config : properties.getInstruments()) {
          if (!trading.get()) break;
          bots.get(Instrument.fromSymbol(config.getSymbol())).seedLevel(level);
        }
      }
      long drift = 0, fill = 0, refresh = 0;
      while (trading.get()) {
        long now = System.nanoTime();
        for (var config : properties.getInstruments()) {
          var bot = bots.get(Instrument.fromSymbol(config.getSymbol()));
          if (!trading.get()) break;
          if (now >= drift) bot.driftMidPrice();
          if (now >= fill) bot.generateFill();
          if (now >= refresh) bot.refreshOrders();
        }
        if (now >= drift)
          drift = now + TimeUnit.SECONDS.toNanos(properties.getDriftIntervalSeconds());
        if (now >= fill)
          fill = now + TimeUnit.SECONDS.toNanos(properties.getTradeIntervalSeconds());
        if (now >= refresh)
          refresh = now + TimeUnit.SECONDS.toNanos(properties.getRefreshIntervalSeconds());
        Thread.sleep(100);
      }
    } catch (Exception e) {
      reason = "WORKER_FAILED";
      log.error("Simulation worker failed", e);
    } finally {
      trading.set(false);
      heartbeat.shutdownNow();
      try {
        control.beginStopping(session.sessionId(), session.fence(), reason);
        finalizeSession(session);
        control.complete(session.sessionId(), session.fence());
      } catch (RuntimeException e) {
        meters.counter("market.simulation.cleanup.failures").increment();
        log.error("Session remains STOPPING for recovery", e);
      } finally {
        workerRunning.set(false);
        bots.clear();
      }
    }
  }

  private void finalizeSession(MarketClockSnapshot session) {
    if (finalizer != null) finalizer.finish(session);
    else cancelAllBotOrders();
  }

  private void buildBots(MarketClockSnapshot session) {
    bots.clear();
    for (var config : properties.getInstruments()) {
      var bot =
          new InstrumentBot(
              config,
              restClient,
              accountManager,
              () -> {
                if (!trading.get()) return false;
                var current = control.snapshot();
                return current.tradingAllowed()
                    && session.sessionId().equals(current.sessionId())
                    && session.fence() == current.fence();
              },
              this::allowBotOrder,
              meters);
      long reference = restClient.getRiskReferencePrice(bot.getInstrument());
      var saved =
          jdbc.query(
              "SELECT price FROM simulator.market_resume_price WHERE epoch_id=? AND instrument=?",
              (rs, n) -> rs.getLong(1),
              session.epochId(),
              bot.getInstrument().name());
      long startingPrice = saved.isEmpty() ? reference : saved.getFirst();
      if (startingPrice > 0) bot.setCurrentMidPrice(startingPrice);
      bot.bindSession(session.sessionId());
      bots.put(bot.getInstrument(), bot);
    }
  }

  private boolean allowBotOrder() {
    long interval = 1_000_000_000L / properties.getMaxOrdersPerSecond();
    while (trading.get()) {
      long now = System.nanoTime(), next = nextBotOrderSlotNanos.get();
      if (now >= next && nextBotOrderSlotNanos.compareAndSet(next, now + interval)) return true;
      try {
        TimeUnit.NANOSECONDS.sleep(
            Math.min(TimeUnit.MILLISECONDS.toNanos(100), Math.max(1, next - now)));
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  private void cancelAllBotOrders() {
    if ((accountManager.getMakerToken() == null || accountManager.getTakerToken() == null)
        && !accountManager.initializeSimulationAccounts()) {
      throw new IllegalStateException("Unable to authenticate simulator accounts for cleanup");
    }
    ThreadPoolExecutor cleanup =
        new ThreadPoolExecutor(
            24,
            24,
            0,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(144),
            runnable -> {
              Thread thread = new Thread(runnable, "market-simulation-cleanup");
              thread.setDaemon(true);
              return thread;
            },
            new ThreadPoolExecutor.AbortPolicy());
    java.util.Set<UUID> cancellationRequested = ConcurrentHashMap.newKeySet();
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(BOT_CLEANUP_TIMEOUT_SECONDS);
    try {
      while (true) {
        List<Callable<Boolean>> cancellations = new ArrayList<>();
        List<UUID> orderIds = new ArrayList<>();
        int activeCount =
            addCancellations(
                    cancellations, orderIds, cancellationRequested, accountManager.getMakerToken())
                + addCancellations(
                    cancellations, orderIds, cancellationRequested, accountManager.getTakerToken());
        if (activeCount == 0) break;
        if (!cancellations.isEmpty()) {
          long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
          if (remainingMillis <= 0) {
            throw new IllegalStateException(
                "Simulator order cleanup did not settle within one minute");
          }
          List<Future<Boolean>> results =
              cleanup.invokeAll(
                  cancellations,
                  Math.min(TimeUnit.SECONDS.toMillis(12), remainingMillis),
                  TimeUnit.MILLISECONDS);
          for (int index = 0; index < results.size(); index++) {
            Future<Boolean> result = results.get(index);
            if (!result.isCancelled() && Boolean.TRUE.equals(result.get())) {
              cancellationRequested.add(orderIds.get(index));
            }
          }
        }
        if (System.nanoTime() >= deadline) {
          throw new IllegalStateException(
              "Simulator order cleanup did not settle within one minute");
        }
        long remainingMillis = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        Thread.sleep(Math.min(500, Math.max(1, remainingMillis)));
      }
      // Order-service state is updated from Kafka. Give the risk consumer a short catch-up window
      // after the final cancellation is visible before reusing the bot accounts.
      Thread.sleep(1_000);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Simulator order cleanup was interrupted", e);
    } catch (java.util.concurrent.ExecutionException e) {
      throw new IllegalStateException("Simulator order cleanup failed", e.getCause());
    } finally {
      cleanup.shutdownNow();
    }
    bots.clear();
  }

  private int addCancellations(
      List<Callable<Boolean>> cancellations,
      List<UUID> orderIds,
      java.util.Set<UUID> cancellationRequested,
      String token) {
    if (token == null) return 0;
    List<OrderResponseDto> active = restClient.getActiveOrdersStrict(token);
    for (OrderResponseDto order : active) {
      if (order.orderId() != null && !cancellationRequested.contains(order.orderId())) {
        orderIds.add(order.orderId());
        cancellations.add(() -> restClient.cancelOrder(token, order.orderId()));
      }
    }
    return active.size();
  }

  public record StartResult(boolean started, SimulationSession session) {}
}
