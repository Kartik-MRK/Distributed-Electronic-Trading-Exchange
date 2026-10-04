package com.dete.account.service;

import com.dete.common.events.trade.TradeExecutedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class TradeSettlementConsumer {

  private static final Logger log = LoggerFactory.getLogger(TradeSettlementConsumer.class);

  private final AccountService accountService;

  public TradeSettlementConsumer(AccountService accountService) {
    this.accountService = accountService;
  }

  @KafkaListener(
      topics = "trade.executions",
      groupId = "${spring.kafka.consumer.group-id:account-service-group}")
  public void onTradeExecuted(TradeExecutedEvent event) {
    log.info(
        "Received TradeExecutedEvent: tradeId={}, instrument={}, price={}, quantity={}, buyer={}, seller={}",
        event.tradeId(),
        event.instrument(),
        event.price(),
        event.quantity(),
        event.buyAccountId(),
        event.sellAccountId());

    try {
      accountService.settleTrade(event);
    } catch (Exception e) {
      log.error(
          "Failed to process settlement for trade {}: {}", event.tradeId(), e.getMessage(), e);
      // In production, failed settlements go to trade.executions.DLQ
      throw e;
    }
  }
}
