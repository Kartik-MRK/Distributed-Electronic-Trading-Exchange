package com.dete.account.controller;

import com.dete.account.dto.BalanceResponse;
import com.dete.account.dto.CreateAccountRequest;
import com.dete.account.dto.ReleaseFundsRequest;
import com.dete.account.dto.ReleaseFundsResponse;
import com.dete.account.dto.ReserveFundsRequest;
import com.dete.account.dto.ReserveFundsResponse;
import com.dete.account.model.Account;
import com.dete.account.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/accounts")
public class InternalAccountController {

  private final AccountService accountService;

  public InternalAccountController(AccountService accountService) {
    this.accountService = accountService;
  }

  @PostMapping("/create")
  public ResponseEntity<Account> createAccount(@Valid @RequestBody CreateAccountRequest request) {
    Account account = accountService.createAccount(request.accountId());
    return ResponseEntity.status(HttpStatus.CREATED).body(account);
  }

  @PostMapping("/reserve")
  public ResponseEntity<ReserveFundsResponse> reserveFunds(
      @Valid @RequestBody ReserveFundsRequest request) {
    ReserveFundsResponse response = accountService.reserveFunds(request);
    return ResponseEntity.ok(response);
  }

  @PostMapping("/release")
  public ResponseEntity<ReleaseFundsResponse> releaseFunds(
      @Valid @RequestBody ReleaseFundsRequest request) {
    ReleaseFundsResponse response = accountService.releaseFunds(request);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{accountId}/balances/{asset}")
  public ResponseEntity<BalanceResponse> getBalance(
      @PathVariable UUID accountId, @PathVariable String asset) {
    BalanceResponse response = accountService.getBalance(accountId, asset);
    return ResponseEntity.ok(response);
  }

  @GetMapping("/{accountId}/balances")
  public ResponseEntity<List<BalanceResponse>> getAllBalances(@PathVariable UUID accountId) {
    List<BalanceResponse> response = accountService.getAllBalances(accountId);
    return ResponseEntity.ok(response);
  }
}
