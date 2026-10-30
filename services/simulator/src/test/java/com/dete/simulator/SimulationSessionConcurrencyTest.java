package com.dete.simulator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.dete.simulator.client.ExchangeRestClient;
import com.dete.simulator.config.SimulatorProperties;
import com.dete.simulator.service.BotAccountManager;
import com.dete.simulator.service.SimulatorService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(
    classes = SimulatorApplication.class,
    properties = "security.jwt.allow-development-key=true")
@Testcontainers(disabledWithoutDocker = true)
class SimulationSessionConcurrencyTest {
  @Container
  static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("simulator_test")
          .withUsername("dete")
          .withPassword("dete_test_secret");

  @DynamicPropertySource
  static void datasource(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
    registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
    registry.add("spring.flyway.user", POSTGRES::getUsername);
    registry.add("spring.flyway.password", POSTGRES::getPassword);
  }

  @Autowired private SimulatorService simulatorService;
  @Autowired private SimulatorProperties properties;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private TransactionTemplate transactions;
  @Autowired private MeterRegistry meters;
  @MockBean private BotAccountManager accountManager;
  @MockBean private ExchangeRestClient mockRestClient;

  @org.junit.jupiter.api.BeforeEach
  void initializeEpoch() throws Exception {
    // Let any previous test worker finish before resetting this isolated database.
    Thread.sleep(1500);
    properties.setInstruments(java.util.List.of());
    jdbc.execute("TRUNCATE simulator.market_clock,simulator.simulation_session CASCADE");
    jdbc.update(
        "INSERT INTO simulator.market_clock(epoch_id,initialized_at,maintenance) VALUES (?,date_trunc('milliseconds',clock_timestamp()),false)",
        UUID.randomUUID());
  }

  @Test
  void separateServiceInstancesCannotClaimConcurrentSessions() throws Exception {
    when(accountManager.initializeSimulationAccounts()).thenReturn(true);
    SimulatorService secondInstance =
        new SimulatorService(
            properties, mockRestClient, accountManager, jdbc, transactions, meters);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    var pool = Executors.newFixedThreadPool(2);
    try {
      var first = pool.submit(() -> raceStart(ready, start, simulatorService));
      var second = pool.submit(() -> raceStart(ready, start, secondInstance));
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();
      var results = List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
      assertThat(results.stream().filter(SimulatorService.StartResult::started).count())
          .isEqualTo(1);
      assertThat(
              jdbc.queryForObject(
                  "SELECT count(*) FROM simulator.simulation_session WHERE status='ACTIVE'",
                  Integer.class))
          .isEqualTo(1);
    } finally {
      start.countDown();
      pool.shutdownNow();
      jdbc.update(
          "UPDATE simulator.simulation_session SET status='COMPLETED', completed_at=now() WHERE status='ACTIVE'");
    }
  }

  @Test
  void expiredSessionIsReconciledBeforeANewClaim() {
    when(accountManager.initializeSimulationAccounts()).thenReturn(true);
    var control = new com.dete.simulation.control.SimulationControlRepository(jdbc, transactions);
    var old = control.claim(UUID.randomUUID(), 60).snapshot();
    control.activate(old.sessionId(), old.fence());
    jdbc.update(
        "UPDATE simulator.simulation_session SET lease_until=clock_timestamp()-interval '1 second',progress_ms=1234 WHERE session_id=?",
        old.sessionId());
    var result = simulatorService.start(UUID.randomUUID(), 60);
    assertThat(result.started()).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT status FROM simulator.simulation_session WHERE session_id=?",
                String.class,
                old.sessionId()))
        .isEqualTo("COMPLETED");
    assertThat(control.snapshot().elapsedMs()).isGreaterThanOrEqualTo(1234);
    jdbc.update(
        "UPDATE simulator.simulation_session SET lease_until=clock_timestamp()-interval '1 second' WHERE session_id=?",
        result.session().sessionId());
  }

  private SimulatorService.StartResult raceStart(
      CountDownLatch ready, CountDownLatch start, SimulatorService service)
      throws InterruptedException {
    ready.countDown();
    start.await();
    return service.start(UUID.randomUUID(), 60);
  }
}
