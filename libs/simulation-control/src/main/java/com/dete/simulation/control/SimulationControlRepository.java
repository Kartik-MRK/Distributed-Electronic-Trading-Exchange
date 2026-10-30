package com.dete.simulation.control;

import com.dete.common.domain.simulation.MarketClockSnapshot;
import com.dete.common.domain.simulation.SimulationContext;
import com.dete.common.domain.simulation.SimulationTime;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Shared authority. Short transactions serialize clock/admission decisions, never remote calls. */
public class SimulationControlRepository {
  private static final long LOCK = 7_349_113_205L;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  public SimulationControlRepository(JdbcTemplate jdbc, TransactionTemplate template) {
    this.jdbc = jdbc;
    this.tx = new TransactionTemplate(template.getTransactionManager());
    this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    this.tx.setTimeout(5);
  }

  public <T> T locked(Supplier<T> action) {
    return tx.execute(
        status -> {
          jdbc.execute("SELECT pg_advisory_xact_lock(" + LOCK + ")");
          return action.get();
        });
  }

  public MarketClockSnapshot snapshot() {
    return locked(this::snapshotLocked);
  }

  private MarketClockSnapshot snapshotLocked() {
    var rows =
        jdbc.query(
            """
        SELECT c.epoch_id,c.initialized_at,c.elapsed_ms,c.maintenance,c.session_id,
               s.owner_user_id,s.status,s.fencing_token,s.base_elapsed_ms,s.progress_ms,
               s.starts_at,s.expires_at,s.lease_until,s.duration_seconds,s.completion_reason,
               clock_timestamp() AS server_now
        FROM simulator.market_clock c LEFT JOIN simulator.simulation_session s
          ON s.session_id=c.session_id WHERE c.singleton=true
        """,
            (rs, n) -> {
              Instant now = rs.getTimestamp("server_now").toInstant();
              Instant starts = instant(rs.getTimestamp("starts_at"));
              Instant ends = instant(rs.getTimestamp("expires_at"));
              Instant lease = instant(rs.getTimestamp("lease_until"));
              String state = rs.getString("status");
              if (state == null) state = "IDLE";
              boolean active = state.equals("ACTIVE") && !rs.getBoolean("maintenance");
              long base = rs.getLong("base_elapsed_ms");
              long progress = Math.max(rs.getLong("elapsed_ms"), rs.getLong("progress_ms"));
              long elapsed =
                  SimulationTime.elapsed(base, progress, starts, ends, lease, now, active);
              boolean trading =
                  active
                      && ends != null
                      && now.isBefore(ends)
                      && lease != null
                      && now.isBefore(lease);
              if (active && !trading) state = "STOPPING";
              if (rs.getBoolean("maintenance")) state = "MAINTENANCE";
              return new MarketClockSnapshot(
                  rs.getObject("epoch_id", UUID.class),
                  rs.getTimestamp("initialized_at").toInstant(),
                  now,
                  rs.getObject("session_id", UUID.class),
                  rs.getObject("owner_user_id", UUID.class),
                  state,
                  rs.getLong("fencing_token"),
                  elapsed,
                  trading ? Math.max(0, ends.toEpochMilli() - now.toEpochMilli()) : 0,
                  starts,
                  ends,
                  lease,
                  trading,
                  (Integer) rs.getObject("duration_seconds"),
                  rs.getString("completion_reason"));
            });
    if (rows.isEmpty()) {
      return new MarketClockSnapshot(
          null,
          null,
          dbNow(),
          null,
          null,
          "UNINITIALIZED",
          0,
          0,
          0,
          null,
          null,
          null,
          false,
          null,
          "Market initialization required");
    }
    MarketClockSnapshot s = rows.getFirst();
    if (s.sessionId() != null && (s.tradingAllowed() || s.status().equals("STOPPING"))) {
      jdbc.update(
          "UPDATE simulator.simulation_session SET progress_ms=GREATEST(progress_ms,?) WHERE session_id=?",
          s.elapsedMs(),
          s.sessionId());
    }
    return s;
  }

  public SimulationContext requireActiveMutation() {
    return locked(
        () -> {
          MarketClockSnapshot s = snapshotLocked();
          if (!s.tradingAllowed())
            throw new SimulationClosedException("Trading requires an active simulation");
          return s.context();
        });
  }

  public Claim claim(UUID userId, int durationSeconds) {
    if (userId == null) throw new IllegalArgumentException("Authenticated user is required");
    if (!List.of(60, 120, 300, 600).contains(durationSeconds))
      throw new IllegalArgumentException("Duration must be 60, 120, 300, or 600 seconds");
    return locked(
        () -> {
          MarketClockSnapshot current = snapshotLocked();
          if (current.epochId() == null || current.status().equals("MAINTENANCE"))
            throw new IllegalStateException("Market is not initialized or is under maintenance");
          if (List.of("STARTING", "ACTIVE", "STOPPING").contains(current.status()))
            return new Claim(false, current);
          UUID id = UUID.randomUUID();
          long fence =
              jdbc.queryForObject("SELECT nextval('simulator.simulation_fence')", Long.class);
          Instant now = dbNow();
          jdbc.update(
              """
          INSERT INTO simulator.simulation_session(session_id,owner_user_id,status,duration_seconds,
          starts_at,expires_at,worker_owner,lease_until,fencing_token,epoch_id,base_elapsed_ms,progress_ms)
          VALUES (?,?,'STARTING',?,?,?,?,?,?,?,?,?)
          """,
              id,
              userId,
              durationSeconds,
              Timestamp.from(now),
              Timestamp.from(now.plusSeconds(durationSeconds)),
              id,
              Timestamp.from(now.plusSeconds(30)),
              fence,
              current.epochId(),
              current.elapsedMs(),
              current.elapsedMs());
          jdbc.update("UPDATE simulator.market_clock SET session_id=? WHERE singleton=true", id);
          return new Claim(true, snapshotLocked());
        });
  }

  public MarketClockSnapshot activate(UUID id, long fence) {
    return locked(
        () -> {
          MarketClockSnapshot s = requireOwner(id, fence);
          if (!s.status().equals("STARTING")
              || s.leaseUntil() == null
              || !s.serverNow().isBefore(s.leaseUntil()))
            throw new SimulationClosedException("Session preparation lease expired");
          Instant now = dbNow();
          jdbc.update(
              """
          UPDATE simulator.simulation_session SET status='ACTIVE',starts_at=?,expires_at=?,lease_until=?,updated_at=clock_timestamp()
          WHERE session_id=? AND fencing_token=?
          """,
              Timestamp.from(now),
              Timestamp.from(now.plusSeconds(s.durationSeconds())),
              Timestamp.from(now.plusSeconds(2)),
              id,
              fence);
          return snapshotLocked();
        });
  }

  public MarketClockSnapshot checkpoint(UUID id, long fence) {
    return locked(
        () -> {
          MarketClockSnapshot s = requireOwner(id, fence);
          if (s.tradingAllowed()) {
            jdbc.update(
                "UPDATE simulator.simulation_session SET lease_until=clock_timestamp()+interval '2 seconds',updated_at=clock_timestamp() WHERE session_id=? AND fencing_token=?",
                id,
                fence);
          }
          return snapshotLocked();
        });
  }

  public MarketClockSnapshot beginStopping(UUID id, long fence, String reason) {
    return locked(
        () -> {
          MarketClockSnapshot s = requireOwner(id, fence);
          if (List.of("COMPLETED", "FAILED").contains(s.status())) return s;
          jdbc.update(
              """
          UPDATE simulator.simulation_session SET status='STOPPING',progress_ms=?,completion_reason=?,
          lease_until=NULL,updated_at=clock_timestamp() WHERE session_id=? AND fencing_token=?
          """,
              s.elapsedMs(),
              reason,
              id,
              fence);
          return snapshotLocked();
        });
  }

  public MarketClockSnapshot complete(UUID id, long fence) {
    return locked(
        () -> {
          MarketClockSnapshot s = requireOwner(id, fence);
          if (List.of("COMPLETED", "FAILED").contains(s.status())) return s;
          if (!s.status().equals("STOPPING"))
            throw new IllegalStateException("Session is not finalizing");
          jdbc.update(
              "UPDATE simulator.market_clock SET elapsed_ms=GREATEST(elapsed_ms,?) WHERE singleton=true",
              s.elapsedMs());
          jdbc.update(
              "UPDATE simulator.simulation_session SET status='COMPLETED',completed_at=clock_timestamp(),lease_until=NULL WHERE session_id=? AND fencing_token=?",
              id,
              fence);
          return snapshotLocked();
        });
  }

  private MarketClockSnapshot requireOwner(UUID id, long fence) {
    MarketClockSnapshot s = snapshotLocked();
    if (!id.equals(s.sessionId()) || fence != s.fence())
      throw new SimulationClosedException("Stale session worker");
    return s;
  }

  private Instant dbNow() {
    return jdbc.queryForObject("SELECT clock_timestamp()", Timestamp.class).toInstant();
  }

  private static Instant instant(Timestamp t) {
    return t == null ? null : t.toInstant();
  }

  public record Claim(boolean started, MarketClockSnapshot snapshot) {}
}
