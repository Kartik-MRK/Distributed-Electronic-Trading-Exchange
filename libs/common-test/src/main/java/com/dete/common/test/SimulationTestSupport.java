package com.dete.common.test;

import com.dete.common.domain.simulation.SimulationContext;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Instant;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** Real control schema for component integration tests that do not boot the simulator service. */
public final class SimulationTestSupport {
  private SimulationTestSupport() {}

  public static synchronized void initialize(JdbcTemplate jdbc) {
    if (jdbc.queryForObject(
        "SELECT to_regclass('simulator.market_clock') IS NULL", Boolean.class)) {
      Path root = Path.of("").toAbsolutePath();
      while (!root.resolve("settings.gradle.kts").toFile().exists()) root = root.getParent();
      try (Connection c = jdbc.getDataSource().getConnection()) {
        c.createStatement().execute("CREATE SCHEMA IF NOT EXISTS simulator");
        c.createStatement().execute("SET search_path TO simulator,public");
        var folder = root.resolve("services/simulator/src/main/resources/db/migration");
        try (var files = java.nio.file.Files.list(folder)) {
          for (var file : files.filter(p -> p.toString().endsWith(".sql")).sorted().toList())
            ScriptUtils.executeSqlScript(c, new FileSystemResource(file));
        }
        c.createStatement().execute("SET search_path TO public");
      } catch (Exception e) {
        throw new IllegalStateException(e);
      }
    }
    if (jdbc.queryForObject("SELECT count(*) FROM simulator.market_clock", Long.class) == 0) {
      UUID epoch = UUID.randomUUID(), session = UUID.randomUUID();
      jdbc.update(
          "INSERT INTO simulator.simulation_session(session_id,owner_user_id,status,duration_seconds,starts_at,expires_at,lease_until,fencing_token,epoch_id) VALUES (?,?,'ACTIVE',600,clock_timestamp(),clock_timestamp()+interval '10 minutes',clock_timestamp()+interval '10 minutes',1,?)",
          session,
          UUID.randomUUID(),
          epoch);
      jdbc.update(
          "INSERT INTO simulator.market_clock(epoch_id,initialized_at,session_id,maintenance) VALUES (?,date_trunc('milliseconds',clock_timestamp()),?,false)",
          epoch,
          session);
    }
  }

  public static SimulationContext context(JdbcTemplate jdbc) {
    return jdbc.queryForObject(
        "SELECT c.epoch_id,c.initialized_at,c.session_id,s.fencing_token FROM simulator.market_clock c JOIN simulator.simulation_session s USING(session_id)",
        (rs, n) ->
            new SimulationContext(
                rs.getObject(1, UUID.class),
                rs.getObject(3, UUID.class),
                rs.getLong(4),
                rs.getTimestamp(2).toInstant(),
                rs.getTimestamp(2).toInstant(),
                0,
                Instant.now()));
  }
}
