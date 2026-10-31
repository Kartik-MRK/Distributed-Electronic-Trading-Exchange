package com.dete.simulator.service;

import static org.assertj.core.api.Assertions.*;

import com.dete.common.test.SimulationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class SessionFinalizerIntegrationTest {
  @Container
  static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:16-alpine");

  static JdbcTemplate jdbc;
  static SessionFinalizer finalizer;

  @BeforeAll
  static void schema() throws Exception {
    var ds = new DriverManagerDataSource(PG.getJdbcUrl(), PG.getUsername(), PG.getPassword());
    jdbc = new JdbcTemplate(ds);
    SimulationTestSupport.initialize(jdbc);
    Path root = Path.of("").toAbsolutePath();
    while (!root.resolve("settings.gradle.kts").toFile().exists()) root = root.getParent();
    for (String service : new String[] {"account", "order"}) {
      try (var files =
          Files.list(root.resolve("services/" + service + "/src/main/resources/db/migration"))) {
        for (var file : files.sorted().toList()) jdbc.execute(Files.readString(file));
      }
    }
    finalizer =
        new SessionFinalizer(
            jdbc,
            new ObjectMapper().findAndRegisterModules(),
            new DataSourceTransactionManager(ds));
  }

  @Test
  void finalizationWaitsForEveryTradeSinkReceipt() {
    UUID session = UUID.randomUUID(), event = UUID.randomUUID();
    var context = SimulationTestSupport.context(jdbc);
    jdbc.update(
        "INSERT INTO simulator.matching_output(epoch_id,topic,partition_id,record_offset,ordinal,session_id,event_id,event_topic,payload) VALUES (?,'order.commands',0,1,0,?,?,'trade.executions','{}')",
        context.epochId(),
        session,
        event);
    assertThat(finalizer.drained(session)).isFalse();
    for (String consumer : new String[] {"account", "risk"})
      jdbc.update("INSERT INTO simulator.consumer_receipt VALUES (?,?)", consumer, event);
    assertThat(finalizer.drained(session)).isFalse();
    jdbc.update("INSERT INTO simulator.consumer_receipt VALUES ('market-data',?)", event);
    assertThat(finalizer.drained(session)).isTrue();
  }

  @Test
  void rolledBackPlacementGetsOneDurableCompensation() {
    UUID session = UUID.randomUUID(), order = UUID.randomUUID();
    var c = SimulationTestSupport.context(jdbc);
    jdbc.update(
        "INSERT INTO simulator.order_intent(order_id,idempotency_key,epoch_id,session_id,fencing_token,account_id,asset,amount,request_hash) VALUES (?,?,?,?,1,?,'USD',100,'test')",
        order,
        UUID.randomUUID(),
        c.epochId(),
        session,
        UUID.randomUUID());
    finalizer.expireOrders(session);
    finalizer.expireOrders(session);
    assertThat(
            jdbc.queryForObject(
                "SELECT closed FROM simulator.order_intent WHERE order_id=?", Boolean.class, order))
        .isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM order_svc.outbox WHERE payload->>'orderId'=?",
                Integer.class,
                order.toString()))
        .isEqualTo(1);
    assertThat(finalizer.drained(session)).isFalse();
  }
}
