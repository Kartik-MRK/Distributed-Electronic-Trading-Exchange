package com.dete.order.service;

import com.dete.common.events.order.OrderRejectedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A durable intent survives order transaction rollback and makes remote side effects recoverable.
 */
@Component
public class FailedOrderCompensator {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public FailedOrderCompensator(
      JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
    this.tx.setTimeout(5);
  }

  @Scheduled(fixedDelay = 1000, initialDelay = 5000)
  public void reconcile() {
    var intents =
        jdbc.query(
            "SELECT i.order_id,i.idempotency_key FROM simulator.order_intent i LEFT JOIN order_svc.orders o USING(order_id) WHERE NOT i.closed AND o.order_id IS NULL LIMIT 100",
            (rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)});
    for (var i : intents)
      tx.executeWithoutResult(
          status -> {
            // An in-flight placement still owns this lock. Never compensate its pending
            // reservation.
            if (!jdbc.queryForObject(
                "SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))",
                Boolean.class,
                i[1].toString())) return;
            if (jdbc.queryForObject(
                    "SELECT count(*) FROM order_svc.orders WHERE order_id=?", Long.class, i[0])
                != 0) return;
            jdbc.execute("SELECT pg_advisory_xact_lock(7349113205)");
            UUID id =
                UUID.nameUUIDFromBytes(
                    ("abort:" + i[0]).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            try {
              var event =
                  new OrderRejectedEvent(
                      id, i[0], "SIMULATION_ADMISSION_ABORTED", Instant.now(), 1);
              jdbc.update(
                  "INSERT INTO order_svc.outbox(outbox_id,topic,key,payload) VALUES (?,'order.events',?,?::jsonb) ON CONFLICT DO NOTHING",
                  id,
                  i[0].toString(),
                  json.writeValueAsString(event));
              jdbc.update("UPDATE simulator.order_intent SET closed=true WHERE order_id=?", i[0]);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
              throw new IllegalStateException(e);
            }
          });
  }
}
