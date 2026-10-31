package com.dete.account.repository;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Called under AccountService's account locks and transaction, alongside balances and ledger. */
@Repository
public class OrderReservationRepository {
  private final JdbcTemplate jdbc;

  public OrderReservationRepository(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public void create(UUID order, UUID account, String asset, long amount) {
    jdbc.update(
        "INSERT INTO account.order_reservations(order_id,account_id,asset,original_amount,remaining_amount) VALUES (?,?,?,?,?)",
        order,
        account,
        asset,
        amount,
        amount);
  }

  public void spend(UUID order, UUID account, String asset, long amount, long quantity) {
    int count =
        jdbc.update(
            "UPDATE account.order_reservations SET remaining_amount=remaining_amount-?,spent_amount=spent_amount+?,settled_qty=settled_qty+? WHERE order_id=? AND account_id=? AND asset=? AND remaining_amount>=? AND NOT closed",
            amount,
            amount,
            quantity,
            order,
            account,
            asset,
            amount);
    if (count != 1)
      throw new IllegalStateException("Trade cannot spend another order's reservation: " + order);
  }

  public void release(UUID order, UUID account, String asset, long amount) {
    int count =
        jdbc.update(
            "UPDATE account.order_reservations SET remaining_amount=remaining_amount-?,released_amount=released_amount+? WHERE order_id=? AND account_id=? AND asset=? AND remaining_amount>=? AND NOT closed",
            amount,
            amount,
            order,
            account,
            asset,
            amount);
    if (count != 1)
      throw new IllegalStateException("Release exceeds this order's reservation: " + order);
  }
}
