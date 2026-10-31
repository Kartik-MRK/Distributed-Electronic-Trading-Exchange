package com.dete.account.service;

import com.dete.account.model.*;
import com.dete.account.repository.*;
import com.dete.common.events.account.BalanceReleasedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Release price improvement only after all fills for a terminal order have settled. */
@Component
@ConditionalOnProperty(
    name = "simulation.reservation-reconciliation.enabled",
    matchIfMissing = true)
public class ReservationReconciler {
  private final JdbcTemplate jdbc;
  private final AccountRepository accounts;
  private final BalanceRepository balances;
  private final LedgerRepository ledger;
  private final OutboxRepository outbox;
  private final ObjectMapper mapper;
  private final TransactionTemplate tx;

  public ReservationReconciler(
      JdbcTemplate jdbc,
      AccountRepository accounts,
      BalanceRepository balances,
      LedgerRepository ledger,
      OutboxRepository outbox,
      ObjectMapper mapper,
      PlatformTransactionManager tx) {
    this.jdbc = jdbc;
    this.accounts = accounts;
    this.balances = balances;
    this.ledger = ledger;
    this.outbox = outbox;
    this.mapper = mapper;
    this.tx = new TransactionTemplate(tx);
  }

  @Scheduled(fixedDelay = 1000, initialDelay = 5000)
  public void reconcile() {
    var ids =
        jdbc.query(
            "SELECT order_id FROM account.order_reservations WHERE NOT closed ORDER BY order_id LIMIT 500",
            (rs, n) -> rs.getObject(1, UUID.class));
    for (var id : ids) reconcile(id);
  }

  public void reconcile(UUID id) {
    tx.executeWithoutResult(
        status -> {
          UUID owner =
              jdbc.queryForObject(
                  "SELECT account_id FROM account.order_reservations WHERE order_id=?",
                  UUID.class,
                  id);
          accounts.lockAccounts(owner);
          var ready =
              jdbc.query(
                  """
          SELECT r.account_id,r.asset,r.remaining_amount FROM account.order_reservations r
          LEFT JOIN order_svc.orders o ON o.order_id=r.order_id
          LEFT JOIN simulator.order_intent i ON i.order_id=r.order_id
          WHERE r.order_id=? AND NOT r.closed AND
          ((o.status IN ('FILLED','CANCELLED','REJECTED') AND o.filled_qty=r.settled_qty)
           OR (o.order_id IS NULL AND i.closed AND r.settled_qty=0))
          """,
                  (rs, n) ->
                      new Remaining(rs.getObject(1, UUID.class), rs.getString(2), rs.getLong(3)),
                  id);
          if (ready.isEmpty()) return;
          var r = ready.getFirst();
          if (r.amount() > 0) {
            if (balances.release(r.owner(), r.asset(), r.amount()) != 1)
              throw new IllegalStateException("Reservation/balance mismatch");
            UUID ref =
                UUID.nameUUIDFromBytes(
                    ("reservation-close:" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            ledger.save(
                LedgerEntry.createNew(
                    r.owner(),
                    r.asset(),
                    LedgerEntryType.RELEASE,
                    r.amount(),
                    ref,
                    ledger.getNextSequenceNum(r.owner())));
            try {
              outbox.save(
                  OutboxMessage.createNew(
                      "ledger.events",
                      mapper.writeValueAsString(
                          new BalanceReleasedEvent(
                              ref, r.owner(), id, r.amount(), Instant.now(), 1))));
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
              throw new IllegalStateException(e);
            }
          }
          jdbc.update(
              "UPDATE account.order_reservations SET closed=true,released_amount=released_amount+remaining_amount,remaining_amount=0 WHERE order_id=?",
              id);
        });
  }

  private record Remaining(UUID owner, String asset, long amount) {}
}
