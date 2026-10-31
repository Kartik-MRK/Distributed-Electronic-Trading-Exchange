package com.dete.simulation.control;

import static org.assertj.core.api.Assertions.*;

import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SimulationControlIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  static JdbcTemplate jdbc;
  static SimulationControlRepository control;

  @BeforeAll
  static void schema() {
    var ds =
        new DriverManagerDataSource(
            POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    jdbc = new JdbcTemplate(ds);
    jdbc.execute("CREATE SCHEMA simulator");
    jdbc.execute("ALTER ROLE " + POSTGRES.getUsername() + " SET search_path TO simulator,public");
    Path root = Path.of("").toAbsolutePath();
    while (!root.resolve("settings.gradle.kts").toFile().exists()) root = root.getParent();
    var scripts = root.resolve("services/simulator/src/main/resources/db/migration");
    new ResourceDatabasePopulator(
            new FileSystemResource(scripts.resolve("V1__create_simulation_sessions.sql")),
            new FileSystemResource(scripts.resolve("V2__simulation_market_clock.sql")),
            new FileSystemResource(scripts.resolve("V3__simulation_matching_admissions.sql")))
        .execute(ds);
    control =
        new SimulationControlRepository(
            jdbc, new TransactionTemplate(new DataSourceTransactionManager(ds)));
  }

  @BeforeEach
  void reset() {
    jdbc.execute("TRUNCATE simulator.market_clock,simulator.simulation_session CASCADE");
    jdbc.update(
        "INSERT INTO simulator.market_clock(epoch_id,initialized_at,maintenance) VALUES (?,date_trunc('milliseconds',clock_timestamp()),false)",
        UUID.randomUUID());
  }

  @Test
  void recordedAdmissionSurvivesExpiryButLateCommandsAreRejected() {
    var s = control.claim(UUID.randomUUID(), 60).snapshot();
    control.activate(s.sessionId(), s.fence());
    var submitted = control.requireActiveMutation();
    var admitted = control.decideMatching("order.commands", 0, 1, submitted, "aaa");
    assertThat(admitted.allowed()).isTrue();
    control.beginStopping(s.sessionId(), s.fence(), "TEST");
    assertThat(control.decideMatching("order.commands", 0, 1, submitted, "aaa"))
        .isEqualTo(admitted);
    assertThat(control.decideMatching("order.commands", 0, 2, submitted, "bbb").allowed())
        .isFalse();
    assertThatThrownBy(() -> control.decideMatching("order.commands", 0, 1, submitted, "wrong"))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void pausedMutationsFailAndNormalDeadlineCommitsOnce() {
    assertThatThrownBy(control::requireActiveMutation)
        .isInstanceOf(SimulationClosedException.class);
    var claim = control.claim(UUID.randomUUID(), 300);
    assertThat(claim.started()).isTrue();
    assertThat(claim.snapshot().status()).isEqualTo("STARTING");
    control.activate(claim.snapshot().sessionId(), claim.snapshot().fence());
    assertThat(control.requireActiveMutation().epochId()).isEqualTo(claim.snapshot().epochId());
    jdbc.update(
        "UPDATE simulator.simulation_session SET starts_at=clock_timestamp()-interval '300 seconds',expires_at=clock_timestamp(),lease_until=clock_timestamp()+interval '2 seconds'");
    var stopped =
        control.beginStopping(
            claim.snapshot().sessionId(), claim.snapshot().fence(), "DEADLINE_REACHED");
    assertThat(stopped.elapsedMs()).isBetween(299990L, 300010L);
    var complete = control.complete(stopped.sessionId(), stopped.fence());
    assertThat(control.complete(stopped.sessionId(), stopped.fence()).elapsedMs())
        .isEqualTo(complete.elapsedMs());
    assertThat(control.snapshot().elapsedMs()).isEqualTo(complete.elapsedMs());
    var next = control.claim(UUID.randomUUID(), 300);
    assertThat(next.snapshot().elapsedMs()).isEqualTo(complete.elapsedMs());
  }

  @Test
  void leaseLossFreezesProgressAndDoesNotAdmitOrders() {
    var s = control.claim(UUID.randomUUID(), 60).snapshot();
    control.activate(s.sessionId(), s.fence());
    jdbc.update(
        "UPDATE simulator.simulation_session SET progress_ms=1234,lease_until=clock_timestamp()-interval '1 second'");
    assertThat(control.snapshot().elapsedMs()).isEqualTo(1234);
    assertThat(control.checkpoint(s.sessionId(), s.fence()).tradingAllowed()).isFalse();
    assertThatThrownBy(control::requireActiveMutation)
        .isInstanceOf(SimulationClosedException.class);
  }

  @Test
  void concurrentClaimsProduceOneSessionAcrossInstances() throws Exception {
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(
              () -> {
                gate.await();
                return control.claim(UUID.randomUUID(), 60);
              });
      var b =
          pool.submit(
              () -> {
                gate.await();
                return control.claim(UUID.randomUUID(), 300);
              });
      gate.countDown();
      assertThat(a.get(5, TimeUnit.SECONDS).started() ^ b.get(5, TimeUnit.SECONDS).started())
          .isTrue();
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM simulator.simulation_session", Integer.class))
          .isEqualTo(1);
    }
  }

  @Test
  void unsupportedDurationAndStaleWorkersAreRejected() {
    assertThatThrownBy(() -> control.claim(UUID.randomUUID(), 30))
        .isInstanceOf(IllegalArgumentException.class);
    var s = control.claim(UUID.randomUUID(), 60).snapshot();
    assertThatThrownBy(() -> control.activate(s.sessionId(), s.fence() + 1))
        .isInstanceOf(SimulationClosedException.class);
  }

  @Test
  void oldIntentCannotAdmitRiskInNewSession() {
    var first = control.claim(UUID.randomUUID(), 60).snapshot();
    control.activate(first.sessionId(), first.fence());
    var order = UUID.randomUUID();
    control.recordOrderIntent(
        order,
        UUID.randomUUID(),
        UUID.randomUUID(),
        "USD",
        100,
        "test",
        control.requireActiveMutation());
    control.beginStopping(first.sessionId(), first.fence(), "TEST");
    control.complete(first.sessionId(), first.fence());
    var next = control.claim(UUID.randomUUID(), 60).snapshot();
    control.activate(next.sessionId(), next.fence());
    assertThatThrownBy(() -> control.duringActiveOrder(order, () -> true))
        .isInstanceOf(SimulationClosedException.class);
  }
}
