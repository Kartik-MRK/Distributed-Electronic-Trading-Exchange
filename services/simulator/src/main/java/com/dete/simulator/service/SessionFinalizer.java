package com.dete.simulator.service;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.simulation.MarketClockSnapshot;
import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderRejectedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Durable cleanup work and explicit downstream receipts form the session completion barrier. */
@Component
public class SessionFinalizer {
  private final JdbcTemplate jdbc;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public SessionFinalizer(
      JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager) {
    this.jdbc = jdbc;
    this.json = json;
    this.tx = new TransactionTemplate(manager);
    this.tx.setTimeout(10);
  }

  public void finish(MarketClockSnapshot session) {
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
    do {
      expireOrders(session.sessionId());
      if (drained(session.sessionId())) return;
      try {
        Thread.sleep(250);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new IllegalStateException("Finalization interrupted", e);
      }
    } while (System.nanoTime() < deadline);
    throw new IllegalStateException(
        "Waiting for matching, settlement, reservations, and market-data acknowledgments");
  }

  void expireOrders(UUID session) {
    var intents =
        jdbc.query(
            "SELECT order_id,idempotency_key FROM simulator.order_intent WHERE session_id=? AND NOT closed",
            (rs, n) -> new UUID[] {rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)},
            session);
    for (var intent : intents)
      tx.executeWithoutResult(
          status -> {
            // Serialize against the original order transaction before declaring it an orphan.
            jdbc.queryForList(
                "SELECT pg_advisory_xact_lock(hashtextextended(?,0))", intent[1].toString());
            var orders =
                jdbc.query(
                    "SELECT account_id,instrument,status FROM order_svc.orders WHERE order_id=?",
                    (rs, n) ->
                        new OrderState(
                            rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3)),
                    intent[0]);
            if (orders.isEmpty()) {
              jdbc.execute("SELECT pg_advisory_xact_lock(7349113205)");
              UUID id = stable("abort:", intent[0]);
              insertOutbox(
                  id,
                  "order.events",
                  intent[0].toString(),
                  new OrderRejectedEvent(
                      id, intent[0], "SIMULATION_ADMISSION_ABORTED", Instant.now(), 1));
              jdbc.update(
                  "UPDATE simulator.order_intent SET closed=true WHERE order_id=?", intent[0]);
            } else {
              var order = orders.getFirst();
              if (order.status().equals("ACCEPTED") || order.status().equals("PARTIALLY_FILLED")) {
                UUID id = stable("expire:", intent[0]);
                insertOutbox(
                    id,
                    "order.commands",
                    order.instrument(),
                    new OrderCancelCommand(
                        id,
                        intent[0],
                        order.account(),
                        Instrument.valueOf(order.instrument()),
                        Instant.now(),
                        1));
              }
              if (java.util.List.of("FILLED", "CANCELLED", "REJECTED").contains(order.status()))
                jdbc.update(
                    "UPDATE simulator.order_intent SET closed=true WHERE order_id=?", intent[0]);
            }
          });
  }

  public boolean drained(UUID session) {
    if (count(
            "SELECT count(*) FROM simulator.order_intent WHERE session_id=? AND NOT closed",
            session)
        > 0) return false;
    if (count(
            "SELECT count(*) FROM simulator.matching_admission WHERE session_id=? AND NOT published",
            session)
        > 0) return false;
    if (count(
            "SELECT count(*) FROM account.order_reservations r JOIN simulator.order_intent i USING(order_id) WHERE i.session_id=? AND NOT r.closed",
            session)
        > 0) return false;
    // A published command may still be waiting in Kafka: require its matching journal
    // acknowledgment.
    if (count(
            """
        SELECT count(*) FROM order_svc.outbox o JOIN simulator.order_intent i
        ON i.order_id=(o.payload->>'orderId')::uuid
        WHERE i.session_id=? AND (NOT o.published OR (o.topic='order.commands' AND NOT EXISTS
          (SELECT 1 FROM simulator.matching_admission a WHERE a.command_event_id=(o.payload->>'eventId')::uuid AND a.published)))
        """,
            session)
        > 0) return false;
    // Both output streams must have been applied; a DLQ/consumer failure cannot look like success.
    if (count(
            """
        SELECT count(*) FROM simulator.matching_output o
        CROSS JOIN LATERAL unnest(CASE WHEN o.event_topic='trade.executions'
          THEN ARRAY['account','market-data','risk'] ELSE ARRAY['order','risk'] END) required(consumer_name)
        WHERE o.session_id=? AND NOT EXISTS (SELECT 1 FROM simulator.consumer_receipt r
          WHERE r.event_id=o.event_id AND r.consumer_name=required.consumer_name)
        """,
            session)
        > 0) return false;
    if (count(
            """
        SELECT count(*) FROM order_svc.outbox o JOIN simulator.order_intent i ON i.order_id=(o.payload->>'orderId')::uuid
        WHERE i.session_id=? AND o.topic='order.events' AND NOT EXISTS
          (SELECT 1 FROM simulator.consumer_receipt r WHERE r.event_id=(o.payload->>'eventId')::uuid AND r.consumer_name='risk')
        """,
            session)
        > 0) return false;
    return jdbc.queryForObject(
            "SELECT count(*) FROM account.outbox WHERE NOT published", Long.class)
        == 0;
  }

  private long count(String sql, UUID id) {
    return jdbc.queryForObject(sql, Long.class, id);
  }

  private UUID stable(String prefix, UUID id) {
    return UUID.nameUUIDFromBytes((prefix + id).getBytes(StandardCharsets.UTF_8));
  }

  private void insertOutbox(UUID id, String topic, String key, Object event) {
    try {
      jdbc.update(
          "INSERT INTO order_svc.outbox(outbox_id,topic,key,payload) VALUES (?,?,?,?::jsonb) ON CONFLICT(outbox_id) DO NOTHING",
          id,
          topic,
          key,
          json.writeValueAsString(event));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException(e);
    }
  }

  private record OrderState(UUID account, String instrument, String status) {}
}
