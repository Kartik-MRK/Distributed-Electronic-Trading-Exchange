package com.dete.account.controller;

import com.dete.account.dto.ReconciliationResult;
import com.dete.account.service.ReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts/reconciliation")
public class ReconciliationController {

  private final ReconciliationService reconciliationService;

  public ReconciliationController(ReconciliationService reconciliationService) {
    this.reconciliationService = reconciliationService;
  }

  @PostMapping("/run")
  public ResponseEntity<ReconciliationResult> runReconciliation() {
    ReconciliationResult result = reconciliationService.runReconciliation();
    return ResponseEntity.ok(result);
  }

  @GetMapping("/status")
  public ResponseEntity<ReconciliationResult> getStatus() {
    return ResponseEntity.ok(reconciliationService.getLastResult());
  }
}
