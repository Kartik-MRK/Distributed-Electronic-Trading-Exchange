package com.dete.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.account.dto.BalanceResponse;
import com.dete.account.dto.ReleaseFundsRequest;
import com.dete.account.dto.ReleaseFundsResponse;
import com.dete.account.dto.ReserveFundsRequest;
import com.dete.account.dto.ReserveFundsResponse;
import com.dete.account.exception.InsufficientFundsException;
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
import com.dete.common.events.trade.TradeExecutedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceUnitTest {

  @Mock private AccountRepository accountRepository;
  @Mock private BalanceRepository balanceRepository;
  @Mock private LedgerRepository ledgerRepository;
  @Mock private SettlementRepository settlementRepository;
  @Mock private OutboxRepository outboxRepository;

  private final ObjectMapper objectMapper =
      new ObjectMapper().registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule());
  private AccountService accountService;

  @BeforeEach
  void setUp() {
    accountService =
        new AccountService(
            accountRepository,
            balanceRepository,
            ledgerRepository,
            settlementRepository,
            outboxRepository,
            objectMapper);
  }

  @Test
  @DisplayName("Deposit: credits available balance and writes DEPOSIT ledger entry")
  void shouldDepositSuccessfully() {
    UUID accountId = UUID.randomUUID();
    String asset = "USD";
    long amount = 10_000L * FixedPoint.SCALE;
    UUID ref = UUID.randomUUID();

    when(ledgerRepository.getNextSequenceNum(accountId)).thenReturn(1L);
    when(balanceRepository.getBalance(accountId, asset))
        .thenReturn(Optional.of(new Balance(accountId, asset, amount, 0L)));

    BalanceResponse response = accountService.deposit(accountId, asset, amount, ref);

    assertThat(response.available()).isEqualTo(amount);
    assertThat(response.reserved()).isEqualTo(0L);

    verify(accountRepository).createAccount(accountId);
    verify(balanceRepository).creditAvailable(accountId, asset, amount);

    ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
    verify(ledgerRepository).save(captor.capture());
    LedgerEntry entry = captor.getValue();
    assertThat(entry.accountId()).isEqualTo(accountId);
    assertThat(entry.asset()).isEqualTo(asset);
    assertThat(entry.entryType()).isEqualTo(LedgerEntryType.DEPOSIT);
    assertThat(entry.amount()).isEqualTo(amount);
    assertThat(entry.referenceId()).isEqualTo(ref);
    assertThat(entry.sequenceNum()).isEqualTo(1L);
  }

  @Test
  @DisplayName(
      "ReserveFunds: atomically reserves funds, writes ledger entry, and produces outbox events")
  void shouldReserveFundsSuccessfully() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    String asset = "USD";
    long amount = 5_000L * FixedPoint.SCALE;

    when(balanceRepository.reserve(accountId, asset, amount)).thenReturn(1);
    when(ledgerRepository.getNextSequenceNum(accountId)).thenReturn(2L);
    when(balanceRepository.getBalance(accountId, asset))
        .thenReturn(Optional.of(new Balance(accountId, asset, 5_000L * FixedPoint.SCALE, amount)));

    ReserveFundsRequest request = new ReserveFundsRequest(accountId, orderId, asset, amount);
    ReserveFundsResponse response = accountService.reserveFunds(request);

    assertThat(response.success()).isTrue();
    assertThat(response.available()).isEqualTo(5_000L * FixedPoint.SCALE);
    assertThat(response.reserved()).isEqualTo(amount);

    verify(balanceRepository).reserve(accountId, asset, amount);
    verify(ledgerRepository).save(any(LedgerEntry.class));
    // 2 outbox events: BalanceReservedEvent (ledger.events) and AuditEvent (audit.events)
    verify(outboxRepository, times(2)).save(any(OutboxMessage.class));
  }

  @Test
  @DisplayName("ReserveFunds: throws InsufficientFundsException when available balance is too low")
  void shouldRejectReservationWhenInsufficientFunds() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    String asset = "USD";
    long amount = 100_000L * FixedPoint.SCALE;

    when(balanceRepository.reserve(accountId, asset, amount)).thenReturn(0);
    when(balanceRepository.getBalance(accountId, asset))
        .thenReturn(Optional.of(new Balance(accountId, asset, 1_000L * FixedPoint.SCALE, 0L)));

    ReserveFundsRequest request = new ReserveFundsRequest(accountId, orderId, asset, amount);

    assertThatThrownBy(() -> accountService.reserveFunds(request))
        .isInstanceOf(InsufficientFundsException.class)
        .hasMessageContaining("Insufficient available balance");

    verify(ledgerRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }

  @Test
  @DisplayName(
      "ReleaseFunds: atomically releases reserved funds, writes ledger entry, and produces outbox events")
  void shouldReleaseFundsSuccessfully() {
    UUID accountId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    String asset = "USD";
    long amount = 2_000L * FixedPoint.SCALE;

    when(balanceRepository.release(accountId, asset, amount)).thenReturn(1);
    when(ledgerRepository.getNextSequenceNum(accountId)).thenReturn(3L);
    when(balanceRepository.getBalance(accountId, asset))
        .thenReturn(Optional.of(new Balance(accountId, asset, 10_000L * FixedPoint.SCALE, 0L)));

    ReleaseFundsRequest request = new ReleaseFundsRequest(accountId, orderId, asset, amount);
    ReleaseFundsResponse response = accountService.releaseFunds(request);

    assertThat(response.success()).isTrue();
    verify(balanceRepository).release(accountId, asset, amount);
    verify(ledgerRepository).save(any(LedgerEntry.class));
    verify(outboxRepository, times(2)).save(any(OutboxMessage.class));
  }

  @Test
  @DisplayName(
      "SettleTrade: double-entry settlement updates balances, writes 4 ledger entries and settlement record")
  void shouldSettleTradeDoubleEntry() {
    UUID tradeId = UUID.randomUUID();
    UUID buyerId = UUID.randomUUID();
    UUID sellerId = UUID.randomUUID();
    Instrument instrument = Instrument.BTC_USD; // Base: BTC, Quote: USD
    long price = 60_000L * FixedPoint.SCALE;
    long quantity = 1L * FixedPoint.SCALE;
    long quoteAmount = 60_000L * FixedPoint.SCALE;

    TradeExecutedEvent event =
        new TradeExecutedEvent(
            UUID.randomUUID(),
            tradeId,
            instrument,
            UUID.randomUUID(),
            UUID.randomUUID(),
            buyerId,
            sellerId,
            price,
            quantity,
            1L,
            Instant.now(),
            1);

    when(settlementRepository.existsByTradeId(tradeId)).thenReturn(false);
    when(ledgerRepository.getNextSequenceNum(buyerId)).thenReturn(1L, 2L);
    when(ledgerRepository.getNextSequenceNum(sellerId)).thenReturn(1L, 2L);

    accountService.settleTrade(event);

    // 1. Buyer balance updates: debit reserved quote (USD), credit available base (BTC)
    verify(balanceRepository).debitReserved(buyerId, "USD", quoteAmount);
    verify(balanceRepository).creditAvailable(buyerId, "BTC", quantity);

    // 2. Seller balance updates: debit reserved base (BTC), credit available quote (USD)
    verify(balanceRepository).debitReserved(sellerId, "BTC", quantity);
    verify(balanceRepository).creditAvailable(sellerId, "USD", quoteAmount);

    // 3. Exactly 4 ledger entries saved
    verify(ledgerRepository, times(4)).save(any(LedgerEntry.class));

    // 4. Exactly 1 settlement record saved
    verify(settlementRepository).save(any(Settlement.class));

    // 5. Outbox events published (BalanceSettledEvent + AuditEvent)
    verify(outboxRepository, times(2)).save(any(OutboxMessage.class));
  }

  @Test
  @DisplayName("SettleTrade: duplicate tradeId is an idempotent no-op")
  void shouldSkipDuplicateSettlementIdempotently() {
    UUID tradeId = UUID.randomUUID();
    TradeExecutedEvent event =
        new TradeExecutedEvent(
            UUID.randomUUID(),
            tradeId,
            Instrument.BTC_USD,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            60_000L * FixedPoint.SCALE,
            1L * FixedPoint.SCALE,
            1L,
            Instant.now(),
            1);

    when(settlementRepository.existsByTradeId(tradeId)).thenReturn(true);

    accountService.settleTrade(event);

    verify(balanceRepository, never()).debitReserved(any(), any(), anyLong());
    verify(balanceRepository, never()).creditAvailable(any(), any(), anyLong());
    verify(ledgerRepository, never()).save(any());
    verify(settlementRepository, never()).save(any());
    verify(outboxRepository, never()).save(any());
  }
}
