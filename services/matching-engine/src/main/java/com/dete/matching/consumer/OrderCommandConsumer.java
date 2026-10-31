package com.dete.matching.consumer;

import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.engine.model.MatchResult;
import com.dete.matching.publisher.MatchingEventPublisher;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Consumes incoming order commands from 'order.commands' and dispatches them to the
 * MatchingEngineService on the appropriate instrument's single-writer thread.
 */
@Component
public class OrderCommandConsumer {

  private static final Logger log = LoggerFactory.getLogger(OrderCommandConsumer.class);

  public static final String TOPIC_ORDER_COMMANDS = "order.commands";

  @org.springframework.beans.factory.annotation.Autowired
  private com.dete.simulation.control.SimulationControlRepository simulationControl;

  private final MatchingEngineService matchingEngineService;
  private final MatchingEventPublisher eventPublisher;
  private final ObjectMapper objectMapper;

  private volatile boolean liveProcessingEnabled = false;
  private final java.util.Map<String, MatchResult> pendingPublications = new java.util.HashMap<>();

  public OrderCommandConsumer(
      MatchingEngineService matchingEngineService,
      MatchingEventPublisher eventPublisher,
      ObjectMapper objectMapper) {
    this.matchingEngineService = matchingEngineService;
    this.eventPublisher = eventPublisher;
    this.objectMapper = objectMapper;
  }

  public void setLiveProcessingEnabled(boolean enabled) {
    this.liveProcessingEnabled = enabled;
  }

  public boolean isLiveProcessingEnabled() {
    return liveProcessingEnabled;
  }

  public synchronized void clearPendingPublications() {
    pendingPublications.clear();
  }

  @KafkaListener(
      topics = TOPIC_ORDER_COMMANDS,
      groupId = "${matching.consumer.group-id:matching-engine-group}",
      containerFactory = "kafkaListenerContainerFactory")
  public synchronized void onOrderCommand(
      ConsumerRecord<String, String> record, Acknowledgment ack) {
    if (!liveProcessingEnabled) {
      throw new IllegalStateException("Live matching is paused; command must not be acknowledged");
    }

    String recordId = record.topic() + ":" + record.partition() + ":" + record.offset();
    try {
      var node = objectMapper.readTree(record.value());
      String type = node.path("eventType").asText();
      boolean modify = OrderModifyCommand.EVENT_TYPE.equals(type) || node.has("newPrice");
      boolean cancel =
          !modify
              && (OrderCancelCommand.EVENT_TYPE.equals(type)
                  || (!node.has("price") && !node.has("side")));
      OrderPlacedEvent event =
          (!modify && !cancel) ? objectMapper.treeToValue(node, OrderPlacedEvent.class) : null;
      com.dete.simulation.control.MatchingAdmission admission = null;
      if (simulationControl != null) {
        var hash = com.dete.simulation.control.SimulationControlRepository.hash(record.value());
        admission =
            cancel
                ? simulationControl.decideCancellation(
                    record.topic(), record.partition(), record.offset(), hash)
                : simulationControl.decideMatching(
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    event == null ? null : event.simulation(),
                    hash);
      }
      if (admission != null)
        simulationControl.attachCommand(
            record.topic(),
            record.partition(),
            record.offset(),
            admission.context().epochId(),
            java.util.UUID.fromString(node.path("eventId").asText()));
      MatchResult result = pendingPublications.get(recordId);
      if (result == null) {
        if (modify) {
          // Modification is disabled at the public API. Do not release or mutate an existing order.
          result = new MatchResult();
        } else if (cancel) {
          result =
              matchingEngineService
                  .cancelOrder(objectMapper.treeToValue(node, OrderCancelCommand.class))
                  .join();
        } else if (admission != null && !admission.allowed()) {
          result =
              MatchResult.rejected(
                  com.dete.common.events.order.OrderRejectedEvent.of(
                      event.orderId(), admission.reason()));
        } else {
          result =
              matchingEngineService
                  .processOrder(
                      admission == null ? event : event.withSimulation(admission.context()))
                  .join();
        }
        pendingPublications.put(recordId, result);
      }
      if (admission == null) eventPublisher.publishMatchResult(result);
      else {
        var outputs =
            new java.util.ArrayList<
                com.dete.simulation.control.SimulationControlRepository.StoredOutput>();
        for (var output : result.allEvents()) {
          outputs.add(
              new com.dete.simulation.control.SimulationControlRepository.StoredOutput(
                  output.eventId(),
                  output instanceof com.dete.common.events.trade.TradeExecutedEvent
                      ? "trade.executions"
                      : "order.events",
                  objectMapper.writeValueAsString(output)));
        }
        var persisted =
            simulationControl.recordOutputs(
                record.topic(), record.partition(), record.offset(), admission, outputs);
        eventPublisher.publishStoredOutputs(persisted);
        simulationControl.markPublished(
            record.topic(), record.partition(), record.offset(), admission.context().epochId());
      }
      if (ack != null) ack.acknowledge();
      pendingPublications.remove(recordId);
    } catch (Exception e) {
      if (!pendingPublications.containsKey(recordId)) liveProcessingEnabled = false;
      throw new IllegalStateException("Order command was not completed", e);
    }
  }
}
