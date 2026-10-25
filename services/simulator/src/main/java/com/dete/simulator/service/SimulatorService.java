package com.dete.simulator.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.simulator.bot.InstrumentBot;
import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.dto.OrderResponseDto;
import io.micrometer.core.instrument.MeterRegistry;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SimulatorService {
  private static final Logger log = LoggerFactory.getLogger(SimulatorService.class);
  private static final int MAX_GRACE_SECONDS = 15;
  private static final long ADVISORY_LOCK_KEY = 7_349_113_205L;

  private final SimulatorProperties properties;
  private final ExchangeRestClient restClient;
  private final BotAccountManager accountManager;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;
  private final MeterRegistry meters;
  private final Map<Instrument, InstrumentBot> bots = new ConcurrentHashMap<>();
  private final AtomicBoolean workerRunning = new AtomicBoolean();
  private final AtomicLong nextBotOrderSlotNanos = new AtomicLong();
  private volatile UUID currentSessionId;
  private volatile UUID currentWorkerOwner;
  private volatile long currentFence;

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
    this.transactions = transactions;
    this.meters = meters;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    // There is deliberately no idle simulator task. Expired rows are reconciled by a start
    // request, which takes the same advisory lock as a new claim before allowing any new work.
    log.info("On-demand market simulator ready; no bot worker is running while idle.");
  }

  public StartResult start(UUID userId, int durationSeconds) {
    if (!properties.isEnabled()) throw new IllegalStateException("Market simulation is disabled");
    if (userId == null) throw new IllegalArgumentException("Authenticated user is required");
    if (durationSeconds != 30 && durationSeconds != 60 && durationSeconds != 120) {
      throw new IllegalArgumentException("Duration must be 30, 60, or 120 seconds");
    }
    // Prepare simulator-owned credentials/liquidity first so the user-selected duration is
    // measured from the actual session claim rather than account setup latency.
    if (!accountManager.initializeSimulationAccounts()) {
      throw new IllegalStateException("Simulator bot accounts could not be initialized");
    }

    Claim claim =
        transactions.execute(
            status -> {
              lockGlobalSession();
              SimulationSession active = findActiveForUpdate();
              if (active != null) {
                Instant recoverableAt = active.expiresAt().plusSeconds(MAX_GRACE_SECONDS);
                if (Instant.now().isBefore(recoverableAt)) return new Claim(null, active);
                // The prior worker is beyond its maximum deadline and grace. Mark it terminal while
                // holding the global lock; bot orders are reconciled below before creating a new
                // row.
                jdbc.update(
                    "UPDATE simulator.simulation_session SET status='STOPPING', updated_at=now() WHERE session_id=?",
                    active.sessionId());
                cancelAllBotOrders();
                jdbc.update(
                    "UPDATE simulator.simulation_session SET status='COMPLETED', completed_at=now(), "
                        + "updated_at=now(), completion_reason='RECOVERED_EXPIRED', lease_until=NULL "
                        + "WHERE session_id=? AND status='STOPPING'",
                    active.sessionId());
                meters.counter("market.simulation.sessions.recovered").increment();
              }

              UUID sessionId = UUID.randomUUID();
              UUID workerOwner = UUID.randomUUID();
              Instant now = Instant.now();
              jdbc.update(
                  "INSERT INTO simulator.simulation_session (session_id, owner_user_id, status, duration_seconds, "
                      + "starts_at, expires_at, worker_owner, lease_until, fencing_token) "
                      + "VALUES (?, ?, 'ACTIVE', ?, ?, ?, ?, ?, 1)",
                  sessionId,
                  userId,
                  durationSeconds,
                  Timestamp.from(now),
                  Timestamp.from(now.plusSeconds(durationSeconds)),
                  workerOwner,
                  Timestamp.from(now.plusSeconds(durationSeconds + MAX_GRACE_SECONDS)));
              return new Claim(new WorkerClaim(sessionId, workerOwner, 1L), null);
            });

    if (claim.existing() != null) {
      meters.counter("market.simulation.start.conflicts").increment();
      return new StartResult(false, claim.existing());
    }
    WorkerClaim worker = claim.worker();
    try {
      buildBots();
      startWorker(worker);
      meters.counter("market.simulation.sessions.started").increment();
      log.info("Market simulation {} started for {} seconds", worker.sessionId(), durationSeconds);
      return new StartResult(true, getStatus());
    } catch (RuntimeException e) {
      meters.counter("market.simulation.sessions.failed").increment();
      jdbc.update(
          "UPDATE simulator.simulation_session SET status='FAILED', completed_at=now(), updated_at=now(), "
              + "completion_reason='START_FAILED', lease_until=NULL WHERE session_id=? AND worker_owner=?",
          worker.sessionId(),
          worker.owner());
      cancelAllBotOrders();
      throw e;
    }
  }

  public SimulationSession getStatus() {
    List<SimulationSession> rows =
        jdbc.query(
            "SELECT session_id, owner_user_id, status, duration_seconds, starts_at, expires_at, "
                + "completed_at, completion_reason FROM simulator.simulation_session "
                + "ORDER BY created_at DESC LIMIT 1",
            (rs, rowNum) ->
                new SimulationSession(
                    rs.getObject("session_id", UUID.class),
                    rs.getObject("owner_user_id", UUID.class),
                    rs.getString("status"),
                    rs.getInt("duration_seconds"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),
                    rs.getTimestamp("completed_at") == null
                        ? null
                        : rs.getTimestamp("completed_at").toInstant(),
                    rs.getString("completion_reason")));
    if (rows.isEmpty()) return null;
    SimulationSession row = rows.get(0);
    if ("ACTIVE".equals(row.status()) && !Instant.now().isBefore(row.expiresAt())) {
      return new SimulationSession(
          row.sessionId(),
          row.ownerUserId(),
          "STOPPING",
          row.durationSeconds(),
          row.startsAt(),
          row.expiresAt(),
          row.completedAt(),
          "DEADLINE_REACHED");
    }
    return row;
  }

  private void startWorker(WorkerClaim claim) {
    if (!workerRunning.compareAndSet(false, true))
      throw new IllegalStateException("A local worker is already running");
    currentSessionId = claim.sessionId();
    currentWorkerOwner = claim.owner();
    currentFence = claim.fence();
    nextBotOrderSlotNanos.set(0L);
    Thread worker = new Thread(() -> runSession(claim), "market-simulation-" + claim.sessionId());
    worker.setDaemon(true);
    worker.start();
  }

  private void runSession(WorkerClaim claim) {
    Instant nextDrift = Instant.EPOCH;
    Instant nextFill = Instant.EPOCH;
    Instant nextRefresh = Instant.EPOCH;
    String reason = "DEADLINE_REACHED";
    try {
      for (SimulatorProperties.InstrumentConfig config : properties.getInstruments()) {
        if (!ownsSession(claim)) return;
        InstrumentBot bot = bots.get(Instrument.fromSymbol(config.getSymbol()));
        if (bot != null) bot.populateOrderBook();
      }
      while (Instant.now().isBefore(sessionExpiry(claim))) {
        if (!renewLease(claim)) return;
        Instant now = Instant.now();
        for (InstrumentBot bot : bots.values()) {
          if (!ownsSession(claim)) return;
          if (!now.isBefore(nextDrift)) bot.driftMidPrice();
          if (!now.isBefore(nextFill)) bot.generateFill();
          if (!now.isBefore(nextRefresh)) bot.refreshOrders();
        }
        if (!now.isBefore(nextDrift))
          nextDrift = now.plusSeconds(properties.getDriftIntervalSeconds());
        if (!now.isBefore(nextFill))
          nextFill = now.plusSeconds(properties.getTradeIntervalSeconds());
        if (!now.isBefore(nextRefresh))
          nextRefresh = now.plusSeconds(properties.getRefreshIntervalSeconds());
        Thread.sleep(500);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      reason = "WORKER_INTERRUPTED";
    } catch (Exception e) {
      reason = "WORKER_FAILED";
      log.error("Market simulation {} failed", claim.sessionId(), e);
    } finally {
      try {
        if (ownsSession(claim)) {
          jdbc.update(
              "UPDATE simulator.simulation_session SET status='STOPPING', updated_at=now() "
                  + "WHERE session_id=? AND worker_owner=? AND fencing_token=? AND status='ACTIVE'",
              claim.sessionId(),
              claim.owner(),
              claim.fence());
          cancelAllBotOrders();
          jdbc.update(
              "UPDATE simulator.simulation_session SET status=?, completed_at=now(), updated_at=now(), "
                  + "completion_reason=?, lease_until=NULL WHERE session_id=? AND worker_owner=? "
                  + "AND fencing_token=? AND status='STOPPING'",
              "WORKER_FAILED".equals(reason) || "WORKER_INTERRUPTED".equals(reason)
                  ? "FAILED"
                  : "COMPLETED",
              reason,
              claim.sessionId(),
              claim.owner(),
              claim.fence());
          log.info("Market simulation {} ended ({})", claim.sessionId(), reason);
        }
      } catch (RuntimeException cleanupError) {
        meters.counter("market.simulation.cleanup.failures").increment();
        log.error(
            "Market simulation {} left in STOPPING for safe order-cleanup recovery",
            claim.sessionId(),
            cleanupError);
      } finally {
        workerRunning.set(false);
        currentSessionId = null;
        currentWorkerOwner = null;
      }
    }
  }

  private Instant sessionExpiry(WorkerClaim claim) {
    List<Instant> rows =
        jdbc.query(
            "SELECT expires_at FROM simulator.simulation_session WHERE session_id=? "
                + "AND worker_owner=? AND fencing_token=? AND status='ACTIVE'",
            (rs, rowNum) -> rs.getTimestamp(1).toInstant(),
            claim.sessionId(),
            claim.owner(),
            claim.fence());
    return rows.isEmpty() ? Instant.EPOCH : rows.get(0);
  }

  private boolean ownsSession(WorkerClaim claim) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM simulator.simulation_session WHERE session_id=? "
                + "AND worker_owner=? AND fencing_token=? AND status='ACTIVE' AND expires_at > now()",
            Integer.class,
            claim.sessionId(),
            claim.owner(),
            claim.fence());
    return count != null && count == 1;
  }

  private boolean renewLease(WorkerClaim claim) {
    return jdbc.update(
            "UPDATE simulator.simulation_session SET lease_until=LEAST(expires_at + interval '15 seconds', "
                + "now() + interval '10 seconds'), updated_at=now() WHERE session_id=? AND worker_owner=? "
                + "AND fencing_token=? AND status='ACTIVE' AND expires_at > now()",
            claim.sessionId(),
            claim.owner(),
            claim.fence())
        == 1;
  }

  private void buildBots() {
    if (!bots.isEmpty()) return;
    for (SimulatorProperties.InstrumentConfig config : properties.getInstruments()) {
      InstrumentBot bot =
          new InstrumentBot(
              config,
              restClient,
              accountManager,
              () ->
                  currentSessionId != null
                      && ownsSession(
                          new WorkerClaim(currentSessionId, currentWorkerOwner, currentFence)),
              this::allowBotOrder,
              meters);
      bots.put(bot.getInstrument(), bot);
    }
  }

  private boolean allowBotOrder() {
    long intervalNanos = 1_000_000_000L / properties.getMaxOrdersPerSecond();
    while (true) {
      long now = System.nanoTime();
      long next = nextBotOrderSlotNanos.get();
      if (now < next) {
        try {
          TimeUnit.NANOSECONDS.sleep(next - now);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return false;
        }
        continue;
      }
      if (nextBotOrderSlotNanos.compareAndSet(next, now + intervalNanos)) return true;
    }
  }

  private void cancelAllBotOrders() {
    if ((accountManager.getMakerToken() == null || accountManager.getTakerToken() == null)
        && !accountManager.initializeSimulationAccounts()) {
      throw new IllegalStateException("Unable to authenticate simulator accounts for cleanup");
    }
    List<Callable<Boolean>> cancellations = new ArrayList<>();
    addCancellations(cancellations, accountManager.getMakerToken());
    addCancellations(cancellations, accountManager.getTakerToken());
    if (!cancellations.isEmpty()) {
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
      try {
        List<Future<Boolean>> results = cleanup.invokeAll(cancellations, 12, TimeUnit.SECONDS);
        for (Future<Boolean> result : results) {
          if (result.isCancelled() || !Boolean.TRUE.equals(result.get())) {
            throw new IllegalStateException("Simulator order cleanup did not complete");
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Simulator order cleanup was interrupted", e);
      } catch (java.util.concurrent.ExecutionException e) {
        throw new IllegalStateException("Simulator order cleanup failed", e.getCause());
      } finally {
        cleanup.shutdownNow();
      }
    }
    bots.clear();
  }

  private void addCancellations(List<Callable<Boolean>> cancellations, String token) {
    if (token == null) return;
    List<OrderResponseDto> active = restClient.getActiveOrdersStrict(token);
    for (OrderResponseDto order : active) {
      if (order.orderId() != null)
        cancellations.add(() -> restClient.cancelOrder(token, order.orderId()));
    }
  }

  private SimulationSession findActiveForUpdate() {
    List<SimulationSession> rows =
        jdbc.query(
            "SELECT session_id, owner_user_id, status, duration_seconds, "
                + "starts_at, expires_at, completed_at, completion_reason FROM simulator.simulation_session "
                + "WHERE status IN ('ACTIVE','STOPPING') ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
            (rs, rowNum) ->
                new SimulationSession(
                    rs.getObject("session_id", UUID.class),
                    rs.getObject("owner_user_id", UUID.class),
                    rs.getString("status"),
                    rs.getInt("duration_seconds"),
                    rs.getTimestamp("starts_at").toInstant(),
                    rs.getTimestamp("expires_at").toInstant(),
                    null,
                    rs.getString("completion_reason")));
    return rows.isEmpty() ? null : rows.get(0);
  }

  private void lockGlobalSession() {
    jdbc.execute("SELECT pg_advisory_xact_lock(" + ADVISORY_LOCK_KEY + ")");
  }

  public record StartResult(boolean started, SimulationSession session) {}

  private record Claim(WorkerClaim worker, SimulationSession existing) {}

  private record WorkerClaim(UUID sessionId, UUID owner, long fence) {}
}
