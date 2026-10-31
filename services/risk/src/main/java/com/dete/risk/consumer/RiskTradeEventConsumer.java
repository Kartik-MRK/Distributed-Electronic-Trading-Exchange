package com.dete.risk.consumer;

import com.dete.common.domain.enums.Instrument;
import com.dete.risk.engine.RiskRuleEvaluator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes TradeExecutedEvents from trade.executions to keep real-time last trade prices up to
 * date.
 */
@Component
public class RiskTradeEventConsumer {

  private static final Logger log = LoggerFactory.getLogger(RiskTradeEventConsumer.class);
  public static final String TOPIC_TRADE_EXECUTIONS = "trade.executions";

  @org.springframework.beans.factory.annotation.Autowired
  private com.dete.simulation.control.SimulationControlRepository simulationControl;

  private final RiskRuleEvaluator riskRuleEvaluator;
  private final ObjectMapper objectMapper;

  public RiskTradeEventConsumer(RiskRuleEvaluator riskRuleEvaluator, ObjectMapper objectMapper) {
    this.riskRuleEvaluator = riskRuleEvaluator;
    this.objectMapper = objectMapper;
  }

  @KafkaListener(
      topics = TOPIC_TRADE_EXECUTIONS,
      groupId = "${spring.kafka.consumer.group-id:risk-service-group}")
  public void onTradeExecuted(ConsumerRecord<String, String> record) {
    try {
      String json = record.value();
      JsonNode node = objectMapper.readTree(json);

      if (node.has("instrument") && node.has("price")) {
        Instrument instrument = Instrument.valueOf(node.get("instrument").asText());
        long price = node.get("price").asLong();
        riskRuleEvaluator.updateLastTradePrice(instrument, price);
        log.debug("Updated last trade price from Kafka for {}: {}", instrument, price);
      }
      if (simulationControl != null)
        simulationControl.receipt("risk", java.util.UUID.fromString(node.path("eventId").asText()));
    } catch (Exception e) {
      throw new IllegalStateException("Risk trade event failed", e);
    }
  }
}
