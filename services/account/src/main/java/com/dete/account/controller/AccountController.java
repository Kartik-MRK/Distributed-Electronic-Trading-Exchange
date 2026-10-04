package com.dete.account.controller;

import com.dete.account.dto.BalanceResponse;
import com.dete.account.dto.DepositRequest;
import com.dete.account.dto.LedgerEntryResponse;
import com.dete.account.dto.SettlementResponse;
import com.dete.account.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

  private final AccountService accountService;

  public AccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @GetMapping("/me/balances")
  public ResponseEntity<List<BalanceResponse>> getMyBalances(
      @AuthenticationPrincipal UUID accountId) {
    List<BalanceResponse> balances = accountService.getAllBalances(accountId);
    return ResponseEntity.ok(balances);
  }

  @GetMapping("/me/ledger")
  public ResponseEntity<List<LedgerEntryResponse>> getMyLedgerEntries(
      @AuthenticationPrincipal UUID accountId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    List<LedgerEntryResponse> entries = accountService.getLedgerEntries(accountId, limit, offset);
    return ResponseEntity.ok(entries);
  }

  @GetMapping("/me/trades")
  public ResponseEntity<List<SettlementResponse>> getMyTrades(
      @AuthenticationPrincipal UUID accountId,
      @RequestParam(defaultValue = "50") int limit,
      @RequestParam(defaultValue = "0") int offset) {
    List<SettlementResponse> trades = accountService.getTradeHistory(accountId, limit, offset);
    return ResponseEntity.ok(trades);
  }

  @PostMapping("/deposit")
  public ResponseEntity<BalanceResponse> deposit(@Valid @RequestBody DepositRequest request) {
    BalanceResponse response =
        accountService.deposit(
            request.accountId(), request.asset(), request.amount(), request.referenceId());
    return ResponseEntity.ok(response);
  }
}
