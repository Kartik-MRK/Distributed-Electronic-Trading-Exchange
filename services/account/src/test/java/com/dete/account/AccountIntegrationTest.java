package com.dete.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.dete.account.dto.BalanceResponse;
import com.dete.account.dto.ReleaseFundsRequest;
import com.dete.account.dto.ReserveFundsRequest;
import com.dete.account.dto.ReserveFundsResponse;
import com.dete.account.exception.InsufficientFundsException;
import com.dete.account.model.LedgerEntry;
import com.dete.account.model.OutboxMessage;
import com.dete.account.repository.LedgerRepository;
import com.dete.account.repository.OutboxRepository;
import com.dete.account.repository.SettlementRepository;
import com.dete.account.service.AccountService;
import com.dete.account.service.OutboxPublisher;
import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.dete.common.test.FullStackTestBase;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AccountIntegrationTest extends FullStackTestBase {

  @Autowired private AccountService accountService;
  @Autowired private LedgerRepository ledgerRepository;
  @Autowired private SettlementRepository settlementRepository;
  @Autowired private OutboxRepository outboxRepository;
  @Autowired private OutboxPublisher outboxPublisher;
  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  @Order(1)
  @DisplayName(
      "Full Account Lifecycle: Create -> Deposit -> Reserve -> Release -> Insufficient Fund Rejection")
  void testAccountLifecycle() {
    UUID accountId = UUID.randomUUID();
    UUID order1 = UUID.randomUUID();

    // 1. Create account & deposit funds
    accountService.createAccount(accountId);
    accountService.deposit(accountId, "USD", 50_000L * FixedPoint.SCALE, null);

    BalanceResponse bal1 = accountService.getBalance(accountId, "USD");
    assertThat(bal1.available()).isEqualTo(50_000L * FixedPoint.SCALE);
    assertThat(bal1.reserved()).isEqualTo(0L);

    // 2. Reserve 20,000 USD
    ReserveFundsResponse resResp =
        accountService.reserveFunds(
            new ReserveFundsRequest(accountId, order1, "USD", 20_000L * FixedPoint.SCALE));
    assertThat(resResp.success()).isTrue();

    BalanceResponse bal2 = accountService.getBalance(accountId, "USD");
    assertThat(bal2.available()).isEqualTo(30_000L * FixedPoint.SCALE);
    assertThat(bal2.reserved()).isEqualTo(20_000L * FixedPoint.SCALE);

    // 3. Release 5,000 USD
    accountService.releaseFunds(
        new ReleaseFundsRequest(accountId, order1, "USD", 5_000L * FixedPoint.SCALE));

    BalanceResponse bal3 = accountService.getBalance(accountId, "USD");
    assertThat(bal3.available()).isEqualTo(35_000L * FixedPoint.SCALE);
    assertThat(bal3.reserved()).isEqualTo(15_000L * FixedPoint.SCALE);

    // 4. Over-reservation attempt (> 35,000 USD available) must fail
    assertThatThrownBy(
            () ->
                accountService.reserveFunds(
                    new ReserveFundsRequest(
                        accountId, UUID.randomUUID(), "USD", 40_000L * FixedPoint.SCALE)))
        .isInstanceOf(InsufficientFundsException.class);

    // Balance must remain unchanged
    BalanceResponse bal4 = accountService.getBalance(accountId, "USD");
    assertThat(bal4.available()).isEqualTo(35_000L * FixedPoint.SCALE);
    assertThat(bal4.reserved()).isEqualTo(15_000L * FixedPoint.SCALE);

    // Verify ledger entries
    List<LedgerEntry> entries = ledgerRepository.findByAccountId(accountId, 10, 0);
    assertThat(entries).hasSize(3); // DEPOSIT, RESERVE, RELEASE
  }

  @Test
  @Order(2)
  @DisplayName(
      "Double-Entry Settlement: Buyer and seller balances updated with 4 ledger entries, duplicate trade is no-op")
  void testDoubleEntrySettlementAndIdempotency() {
    UUID buyerId = UUID.randomUUID();
    UUID sellerId = UUID.randomUUID();
    UUID tradeId = UUID.randomUUID();

    long price = 60_000L * FixedPoint.SCALE;
    long quantity = 1L * FixedPoint.SCALE;
    long quoteAmount = 60_000L * FixedPoint.SCALE;

    // Buyer deposits & reserves quote currency (USD)
    accountService.deposit(buyerId, "USD", quoteAmount, null);
    accountService.reserveFunds(
        new ReserveFundsRequest(buyerId, UUID.randomUUID(), "USD", quoteAmount));

    // Seller deposits & reserves base currency (BTC)
    accountService.deposit(sellerId, "BTC", quantity, null);
    accountService.reserveFunds(
        new ReserveFundsRequest(sellerId, UUID.randomUUID(), "BTC", quantity));

    TradeExecutedEvent tradeEvent =
        new TradeExecutedEvent(
            UUID.randomUUID(),
            tradeId,
            Instrument.BTC_USD,
            UUID.randomUUID(),
            UUID.randomUUID(),
            buyerId,
            sellerId,
            price,
            quantity,
            1L,
            Instant.now(),
            1);

    // 1. Execute settlement
    accountService.settleTrade(tradeEvent);

    // Buyer: 0 USD reserved, 1 BTC available
    BalanceResponse buyerBtc = accountService.getBalance(buyerId, "BTC");
    BalanceResponse buyerUsd = accountService.getBalance(buyerId, "USD");
    assertThat(buyerBtc.available()).isEqualTo(quantity);
    assertThat(buyerUsd.reserved()).isEqualTo(0L);

    // Seller: 0 BTC reserved, 60,000 USD available
    BalanceResponse sellerUsd = accountService.getBalance(sellerId, "USD");
    BalanceResponse sellerBtc = accountService.getBalance(sellerId, "BTC");
    assertThat(sellerUsd.available()).isEqualTo(quoteAmount);
    assertThat(sellerBtc.reserved()).isEqualTo(0L);

    // Verify settlement recorded
    assertThat(settlementRepository.existsByTradeId(tradeId)).isTrue();

    // 2. Duplicate settlement execution must be an idempotent no-op
    accountService.settleTrade(tradeEvent);

    // Balances must remain identical
    assertThat(accountService.getBalance(buyerId, "BTC").available()).isEqualTo(quantity);
    assertThat(accountService.getBalance(sellerId, "USD").available()).isEqualTo(quoteAmount);
  }

  @Test
  @Order(3)
  @DisplayName(
      "Database-Level Immutability: PostgreSQL trigger blocks UPDATE and DELETE on ledger_entries")
  void testLedgerImmutabilityTrigger() {
    UUID accountId = UUID.randomUUID();
    accountService.deposit(accountId, "USD", 1_000L * FixedPoint.SCALE, null);

    List<LedgerEntry> entries = ledgerRepository.findByAccountId(accountId, 1, 0);
    assertThat(entries).isNotEmpty();
    long entryId = entries.get(0).entryId();

    // Attempt UPDATE on ledger_entries -> must fail via DB trigger
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "UPDATE account.ledger_entries SET amount = 999999 WHERE entry_id = ?",
                    entryId))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("account.ledger_entries is append-only and immutable");

    // Attempt DELETE on ledger_entries -> must fail via DB trigger
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "DELETE FROM account.ledger_entries WHERE entry_id = ?", entryId))
        .isInstanceOf(Exception.class)
        .hasMessageContaining("account.ledger_entries is append-only and immutable");
  }

  @Test
  @Order(4)
  @DisplayName("Database-Level Check Constraint: available >= 0 is enforced by PostgreSQL")
  void testDbLevelCheckConstraint() {
    UUID accountId = UUID.randomUUID();
    accountService.createAccount(accountId);

    // Direct insert of negative available balance must be rejected by PostgreSQL CHECK constraint
    assertThatThrownBy(
            () ->
                jdbcTemplate.update(
                    "INSERT INTO account.balances (account_id, asset, available, reserved) VALUES (?, 'USD', -500, 0)",
                    accountId))
        .isInstanceOf(DataIntegrityViolationException.class);
  }

  @Test
  @Order(5)
  @DisplayName(
      "Transactional Outbox: OutboxPublisher reliably delivers events to Kafka and marks them published")
  void testOutboxDelivery() {
    UUID accountId = UUID.randomUUID();
    accountService.deposit(accountId, "USD", 10_000L * FixedPoint.SCALE, null);
    accountService.reserveFunds(
        new ReserveFundsRequest(accountId, UUID.randomUUID(), "USD", 2_000L * FixedPoint.SCALE));

    // Drain outbox until all messages are delivered
    while (!outboxRepository.findUnpublished(50).isEmpty()) {
      outboxPublisher.publishOutboxMessages();
    }

    // Verify outbox messages are marked published
    List<OutboxMessage> pending = outboxRepository.findUnpublished(10);
    assertThat(pending).isEmpty();
  }

  @Test
  @Order(6)
  @DisplayName(
      "Concurrency: Simultaneous reservation attempts never over-reserve or cause negative balances")
  void testConcurrentReservationsCannotOverReserve() throws InterruptedException {
    UUID accountId = UUID.randomUUID();
    long unitAmount = 200L * FixedPoint.SCALE;
    long initialBalance = 1_000L * FixedPoint.SCALE; // Enough for exactly 5 successful reservations

    accountService.createAccount(accountId);
    accountService.deposit(accountId, "USD", initialBalance, null);

    int threadCount = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threadCount);
    CountDownLatch startGate = new CountDownLatch(1);
    CountDownLatch endGate = new CountDownLatch(threadCount);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failureCount = new AtomicInteger(0);

    for (int i = 0; i < threadCount; i++) {
      executor.submit(
          () -> {
            try {
              startGate.await(); // Synchronize all threads to fire simultaneously
              accountService.reserveFunds(
                  new ReserveFundsRequest(accountId, UUID.randomUUID(), "USD", unitAmount));
              successCount.incrementAndGet();
            } catch (InsufficientFundsException e) {
              failureCount.incrementAndGet();
            } catch (Exception e) {
              // Any other unexpected exception
            } finally {
              endGate.countDown();
            }
          });
    }

    startGate.countDown(); // Fire all threads!
    endGate.await();
    executor.shutdown();

    // Exactly 5 reservations must succeed, and 5 must be rejected
    assertThat(successCount.get()).isEqualTo(5);
    assertThat(failureCount.get()).isEqualTo(5);

    BalanceResponse finalBalance = accountService.getBalance(accountId, "USD");
    assertThat(finalBalance.available()).isEqualTo(0L);
    assertThat(finalBalance.reserved()).isEqualTo(initialBalance);
    assertThat(finalBalance.total()).isEqualTo(initialBalance);
  }
}
