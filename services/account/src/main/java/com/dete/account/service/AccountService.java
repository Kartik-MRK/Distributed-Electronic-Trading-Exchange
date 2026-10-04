package com.dete.account.service;

import com.dete.account.dto.BalanceResponse;
import com.dete.account.dto.LedgerEntryResponse;
import com.dete.account.dto.ReleaseFundsRequest;
import com.dete.account.dto.ReleaseFundsResponse;
import com.dete.account.dto.ReserveFundsRequest;
import com.dete.account.dto.ReserveFundsResponse;
import com.dete.account.dto.SettlementResponse;
import com.dete.account.exception.InsufficientFundsException;
import com.dete.account.model.Account;
import com.dete.account.model.Balance;
import com.dete.account.model.LedgerEntry;
import com.dete.account.model.LedgerEntryType;
import com.dete.account.model.OutboxMessage;
import com.dete.account.model.Settlement;
import com.dete.account.repository.AccountRepository;
import com.dete.account.repository.BalanceRepository;
import com.dete.account.repository.LedgerRepository;
import com.dete.account.repository.OutboxRepository;
import com.dete.account.repository.SettlementRepository;
import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.types.FixedPoint;
import com.dete.common.events.account.BalanceReleasedEvent;
import com.dete.common.events.account.BalanceReservedEvent;
import com.dete.common.events.account.BalanceSettledEvent;
import com.dete.common.events.audit.AuditEvent;
import com.dete.common.events.trade.TradeExecutedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

  private static final Logger log = LoggerFactory.getLogger(AccountService.class);
  public static final String TOPIC_LEDGER_EVENTS = "ledger.events";
  public static final String TOPIC_AUDIT_EVENTS = "audit.events";

  private final AccountRepository accountRepository;
  private final BalanceRepository balanceRepository;
  private final LedgerRepository ledgerRepository;
  private final SettlementRepository settlementRepository;
  private final OutboxRepository outboxRepository;
  private final ObjectMapper objectMapper;

  public AccountService(
      AccountRepository accountRepository,
      BalanceRepository balanceRepository,
      LedgerRepository ledgerRepository,
      SettlementRepository settlementRepository,
      OutboxRepository outboxRepository,
      ObjectMapper objectMapper) {
    this.accountRepository = accountRepository;
    this.balanceRepository = balanceRepository;
    this.ledgerRepository = ledgerRepository;
    this.settlementRepository = settlementRepository;
    this.outboxRepository = outboxRepository;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Account createAccount(UUID accountId) {
    return accountRepository.createAccount(accountId);
  }

  @Transactional
  public BalanceResponse deposit(UUID accountId, String asset, long amount, UUID referenceId) {
    if (amount <= 0) {
      throw new IllegalArgumentException("Deposit amount must be positive");
    }
    // Ensure account exists
    accountRepository.createAccount(accountId);

    balanceRepository.creditAvailable(accountId, asset, amount);

    long seq = ledgerRepository.getNextSequenceNum(accountId);
    UUID ref = referenceId != null ? referenceId : UUID.randomUUID();
    LedgerEntry entry =
        LedgerEntry.createNew(accountId, asset, LedgerEntryType.DEPOSIT, amount, ref, seq);
    ledgerRepository.save(entry);

    log.info("Deposited {} {} for account {}", amount, asset, accountId);
    Balance balance =
        balanceRepository
            .getBalance(accountId, asset)
            .orElse(new Balance(accountId, asset, amount, 0L));
    return BalanceResponse.of(
        balance.accountId(), balance.asset(), balance.available(), balance.reserved());
  }

  @Transactional(readOnly = true)
  public BalanceResponse getBalance(UUID accountId, String asset) {
    Balance balance =
        balanceRepository
            .getBalance(accountId, asset)
            .orElse(new Balance(accountId, asset, 0L, 0L));
    return BalanceResponse.of(
        balance.accountId(), balance.asset(), balance.available(), balance.reserved());
  }

  @Transactional(readOnly = true)
  public List<BalanceResponse> getAllBalances(UUID accountId) {
    return balanceRepository.getAllBalances(accountId).stream()
        .map(b -> BalanceResponse.of(b.accountId(), b.asset(), b.available(), b.reserved()))
        .toList();
  }

  @Transactional
  public ReserveFundsResponse reserveFunds(ReserveFundsRequest request) {
    UUID accountId = request.accountId();
    String asset = request.asset();
    long amount = request.amount();
    UUID orderId = request.orderId();

    if (amount <= 0) {
      throw new IllegalArgumentException("Reservation amount must be positive");
    }

    accountRepository.createAccount(accountId);

    int rowsUpdated = balanceRepository.reserve(accountId, asset, amount);
    if (rowsUpdated == 0) {
      Balance current =
          balanceRepository
              .getBalance(accountId, asset)
              .orElse(new Balance(accountId, asset, 0L, 0L));
      throw new InsufficientFundsException(
          String.format(
              "Insufficient available balance to reserve %d %s (available: %d)",
              amount, asset, current.available()));
    }

    long seq = ledgerRepository.getNextSequenceNum(accountId);
    LedgerEntry entry =
        LedgerEntry.createNew(accountId, asset, LedgerEntryType.RESERVE, amount, orderId, seq);
    ledgerRepository.save(entry);

    // Write BalanceReservedEvent to transactional outbox
    BalanceReservedEvent event =
        new BalanceReservedEvent(
            UUID.randomUUID(), accountId, orderId, null, null, amount, Instant.now(), 1);
    saveToOutbox(TOPIC_LEDGER_EVENTS, event);

    // Write AuditEvent to transactional outbox
    AuditEvent auditEvent =
        new AuditEvent(
            UUID.randomUUID(),
            BalanceReservedEvent.EVENT_TYPE,
            "ACCOUNT",
            accountId,
            accountId,
            null,
            String.format(
                "{\"orderId\":\"%s\",\"asset\":\"%s\",\"amount\":%d}", orderId, asset, amount),
            UUID.randomUUID().toString(),
            Instant.now(),
            1);
    saveToOutbox(TOPIC_AUDIT_EVENTS, auditEvent);

    Balance updated = balanceRepository.getBalance(accountId, asset).orElseThrow();
    return new ReserveFundsResponse(
        true,
        "Funds reserved successfully",
        accountId,
        asset,
        updated.available(),
        updated.reserved());
  }

  @Transactional
  public ReleaseFundsResponse releaseFunds(ReleaseFundsRequest request) {
    UUID accountId = request.accountId();
    String asset = request.asset();
    long amount = request.amount();
    UUID orderId = request.orderId();

    if (amount <= 0) {
      throw new IllegalArgumentException("Release amount must be positive");
    }

    int rowsUpdated = balanceRepository.release(accountId, asset, amount);
    if (rowsUpdated == 0) {
      Balance current =
          balanceRepository
              .getBalance(accountId, asset)
              .orElse(new Balance(accountId, asset, 0L, 0L));
      throw new InsufficientFundsException(
          String.format(
              "Insufficient reserved balance to release %d %s (reserved: %d)",
              amount, asset, current.reserved()));
    }

    long seq = ledgerRepository.getNextSequenceNum(accountId);
    LedgerEntry entry =
        LedgerEntry.createNew(accountId, asset, LedgerEntryType.RELEASE, amount, orderId, seq);
    ledgerRepository.save(entry);

    // Write BalanceReleasedEvent to transactional outbox
    BalanceReleasedEvent event =
        new BalanceReleasedEvent(UUID.randomUUID(), accountId, orderId, amount, Instant.now(), 1);
    saveToOutbox(TOPIC_LEDGER_EVENTS, event);

    // Write AuditEvent to transactional outbox
    AuditEvent auditEvent =
        new AuditEvent(
            UUID.randomUUID(),
            BalanceReleasedEvent.EVENT_TYPE,
            "ACCOUNT",
            accountId,
            accountId,
            null,
            String.format(
                "{\"orderId\":\"%s\",\"asset\":\"%s\",\"amount\":%d}", orderId, asset, amount),
            UUID.randomUUID().toString(),
            Instant.now(),
            1);
    saveToOutbox(TOPIC_AUDIT_EVENTS, auditEvent);

    Balance updated = balanceRepository.getBalance(accountId, asset).orElseThrow();
    return new ReleaseFundsResponse(
        true,
        "Funds released successfully",
        accountId,
        asset,
        updated.available(),
        updated.reserved());
  }

  @Transactional
  public void settleTrade(TradeExecutedEvent event) {
    // 1. Idempotency check: if settlement already recorded, skip cleanly
    if (settlementRepository.existsByTradeId(event.tradeId())) {
      log.info("Trade {} already settled. Skipping duplicate execution.", event.tradeId());
      return;
    }

    UUID buyerId = event.buyAccountId();
    UUID sellerId = event.sellAccountId();
    Instrument instrument = event.instrument();
    String baseAsset = instrument.baseAsset();
    String quoteAsset = instrument.quoteAsset();
    long price = event.price();
    long quantity = event.quantity();

    // Notional quote amount = price * quantity / SCALE using 128-bit intermediate
    long quoteAmount =
        FixedPoint.ofScaled(price).multiply(FixedPoint.ofScaled(quantity)).scaledValue();

    // Ensure accounts exist
    accountRepository.createAccount(buyerId);
    accountRepository.createAccount(sellerId);

    // 2. Double-entry balance state updates
    // Buyer: debited quote amount from reserved, credited base asset to available
    balanceRepository.debitReserved(buyerId, quoteAsset, quoteAmount);
    balanceRepository.creditAvailable(buyerId, baseAsset, quantity);

    // Seller: debited base asset from reserved, credited quote asset to available
    balanceRepository.debitReserved(sellerId, baseAsset, quantity);
    balanceRepository.creditAvailable(sellerId, quoteAsset, quoteAmount);

    // 3. Write 4 double-entry ledger entries
    long buyerSeq1 = ledgerRepository.getNextSequenceNum(buyerId);
    ledgerRepository.save(
        LedgerEntry.createNew(
            buyerId, quoteAsset, LedgerEntryType.DEBIT, quoteAmount, event.tradeId(), buyerSeq1));

    long buyerSeq2 = ledgerRepository.getNextSequenceNum(buyerId);
    ledgerRepository.save(
        LedgerEntry.createNew(
            buyerId, baseAsset, LedgerEntryType.CREDIT, quantity, event.tradeId(), buyerSeq2));

    long sellerSeq1 = ledgerRepository.getNextSequenceNum(sellerId);
    ledgerRepository.save(
        LedgerEntry.createNew(
            sellerId, baseAsset, LedgerEntryType.DEBIT, quantity, event.tradeId(), sellerSeq1));

    long sellerSeq2 = ledgerRepository.getNextSequenceNum(sellerId);
    ledgerRepository.save(
        LedgerEntry.createNew(
            sellerId,
            quoteAsset,
            LedgerEntryType.CREDIT,
            quoteAmount,
            event.tradeId(),
            sellerSeq2));

    // 4. Save settlement record
    Settlement settlement =
        Settlement.createNew(
            event.tradeId(), buyerId, sellerId, instrument.symbol(), price, quantity);
    settlementRepository.save(settlement);

    // 5. Write BalanceSettledEvent to transactional outbox
    BalanceSettledEvent settledEvent =
        new BalanceSettledEvent(
            UUID.randomUUID(),
            event.tradeId(),
            buyerId,
            sellerId,
            instrument,
            price,
            quantity,
            Instant.now(),
            1);
    saveToOutbox(TOPIC_LEDGER_EVENTS, settledEvent);

    // 6. Write AuditEvent to transactional outbox
    AuditEvent auditEvent =
        new AuditEvent(
            UUID.randomUUID(),
            BalanceSettledEvent.EVENT_TYPE,
            "TRADE",
            event.tradeId(),
            buyerId,
            instrument.symbol(),
            String.format(
                "{\"tradeId\":\"%s\",\"buyerId\":\"%s\",\"sellerId\":\"%s\",\"price\":%d,\"quantity\":%d,\"quoteAmount\":%d}",
                event.tradeId(), buyerId, sellerId, price, quantity, quoteAmount),
            UUID.randomUUID().toString(),
            Instant.now(),
            1);
    saveToOutbox(TOPIC_AUDIT_EVENTS, auditEvent);

    log.info(
        "Successfully settled trade {}: buyer {} received {} {}, seller {} received {} {}",
        event.tradeId(),
        buyerId,
        quantity,
        baseAsset,
        sellerId,
        quoteAmount,
        quoteAsset);
  }

  @Transactional(readOnly = true)
  public List<LedgerEntryResponse> getLedgerEntries(UUID accountId, int limit, int offset) {
    return ledgerRepository.findByAccountId(accountId, limit, offset).stream()
        .map(LedgerEntryResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<SettlementResponse> getTradeHistory(UUID accountId, int limit, int offset) {
    return settlementRepository.findByAccountId(accountId, limit, offset).stream()
        .map(SettlementResponse::from)
        .toList();
  }

  private void saveToOutbox(String topic, Object event) {
    try {
      String json = objectMapper.writeValueAsString(event);
      outboxRepository.save(OutboxMessage.createNew(topic, json));
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize outbox event: {}", e.getMessage(), e);
      throw new IllegalStateException("Failed to serialize outbox event", e);
    }
  }
}
