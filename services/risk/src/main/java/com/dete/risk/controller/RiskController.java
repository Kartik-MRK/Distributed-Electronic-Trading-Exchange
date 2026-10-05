package com.dete.risk.controller;

import com.dete.common.domain.enums.Instrument;
import com.dete.common.domain.risk.ValidateOrderRequest;
import com.dete.common.domain.risk.ValidateOrderResponse;
import com.dete.risk.config.RiskProperties;
import com.dete.risk.engine.RiskRuleEvaluator;
import com.dete.risk.model.AccountRiskState;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/risk")
public class RiskController {

  private final RiskRuleEvaluator riskRuleEvaluator;
  private final RiskProperties properties;

  public RiskController(RiskRuleEvaluator riskRuleEvaluator, RiskProperties properties) {
    this.riskRuleEvaluator = riskRuleEvaluator;
    this.properties = properties;
  }

  @GetMapping("/instruments")
  public ResponseEntity<Map<String, Long>> getInstrumentPrices() {
    Map<String, Long> prices = new HashMap<>();
    for (Instrument inst : Instrument.values()) {
      prices.put(inst.name(), riskRuleEvaluator.getLastTradePrice(inst));
    }
    return ResponseEntity.ok(prices);
  }

  @GetMapping("/rules")
  public ResponseEntity<Map<String, Object>> getRules() {
    return ResponseEntity.ok(
        Map.of(
            "maxBtcQty", properties.getMaxBtcQty(),
            "maxEthQty", properties.getMaxEthQty(),
            "maxSolQty", properties.getMaxSolQty(),
            "maxOrderNotional", properties.getMaxOrderNotional(),
            "maxOpenOrdersPerAccount", properties.getMaxOpenOrdersPerAccount(),
            "maxPriceDeviationPct", properties.getMaxPriceDeviationPct(),
            "marketOrderMaxQtyRatio", properties.getMarketOrderMaxQtyRatio()));
  }

  @GetMapping("/accounts/{accountId}")
  public ResponseEntity<Map<String, Object>> getAccountStats(@PathVariable UUID accountId) {
    AccountRiskState state = riskRuleEvaluator.getAccountState(accountId);
    return ResponseEntity.ok(
        Map.of("accountId", accountId, "openOrdersCount", state.getOpenOrdersCount()));
  }

  @PostMapping("/validate")
  public ResponseEntity<ValidateOrderResponse> validateOrderDirect(
      @RequestBody ValidateOrderRequest request) {
    ValidateOrderResponse response = riskRuleEvaluator.evaluate(request);
    return ResponseEntity.ok(response);
  }
}
