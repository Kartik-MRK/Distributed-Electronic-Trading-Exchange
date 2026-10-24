package com.dete.matching.replay;

import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.consumer.OrderCommandConsumer;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

/** Single-owner recovery: reconstruct only commands whose output was acknowledged before commit. */
@Service
public class KafkaReplayService {
  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Value("${matching.consumer.group-id:matching-engine-group}")
  private String groupId;

  @Autowired private KafkaListenerEndpointRegistry registry;
  private final MatchingEngineService engine;
  private final OrderCommandConsumer liveConsumer;
  private final ObjectMapper mapper;

  public KafkaReplayService(
      MatchingEngineService engine, OrderCommandConsumer liveConsumer, ObjectMapper mapper) {
    this.engine = engine;
    this.liveConsumer = liveConsumer;
    this.mapper = mapper;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    // No live commands may race with reconstruction; the listener factory disables auto-start.
    replayFromBeginning();
    registry.start();
  }

  public int replayFromBeginning() {
    synchronized (liveConsumer) {
      liveConsumer.setLiveProcessingEnabled(false);
      Properties props = new Properties();
      props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
      props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
      props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
      props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
      // assign(), rather than subscribe(), never joins or changes the live group's assignment.
      try (KafkaConsumer<String, String> replay = new KafkaConsumer<>(props)) {
        var partitions =
            replay
                .partitionsFor(OrderCommandConsumer.TOPIC_ORDER_COMMANDS, Duration.ofSeconds(30))
                .stream()
                .map(p -> new TopicPartition(p.topic(), p.partition()))
                .collect(Collectors.toSet());
        if (partitions.isEmpty())
          throw new IllegalStateException("Order command topic is unavailable");
        var committed = replay.committed(partitions);
        Map<TopicPartition, Long> targets =
            partitions.stream()
                .collect(
                    Collectors.toMap(
                        p -> p, p -> committed.get(p) == null ? 0L : committed.get(p).offset()));
        return reconstruct(replay, targets);
      }
    }
  }

  /** Fault-injection entry point; the supplied consumer must never commit replay offsets. */
  public int reconstruct(Consumer<String, String> replay, Map<TopicPartition, Long> targets) {
    synchronized (liveConsumer) {
      liveConsumer.setLiveProcessingEnabled(false);
      Set<TopicPartition> partitions = targets.keySet();
      var beginnings = replay.beginningOffsets(partitions);
      var ends = replay.endOffsets(partitions);
      for (var partition : partitions) {
        if (beginnings.get(partition) != 0 || targets.get(partition) > ends.get(partition)) {
          throw new IllegalStateException(
              "Complete command history is unavailable for "
                  + partition
                  + "; restore a verified checkpoint before accepting orders");
        }
      }
      replay.assign(partitions);
      partitions.forEach(p -> replay.seek(p, 0));
      engine.resetAll();
      liveConsumer.clearPendingPublications();
      int count = 0;
      long deadline = System.nanoTime() + Duration.ofMinutes(2).toNanos();
      try {
        while (partitions.stream().anyMatch(p -> replay.position(p) < targets.get(p))) {
          if (System.nanoTime() > deadline) throw new IllegalStateException("Recovery timed out");
          for (var record : replay.poll(Duration.ofMillis(250))) {
            TopicPartition partition = new TopicPartition(record.topic(), record.partition());
            if (record.offset() >= targets.get(partition)) continue;
            var node = mapper.readTree(record.value());
            String type = node.path("eventType").asText();
            if (OrderModifyCommand.EVENT_TYPE.equals(type) || node.has("newPrice")) {
              engine.modifyOrder(mapper.treeToValue(node, OrderModifyCommand.class)).join();
            } else if (OrderCancelCommand.EVENT_TYPE.equals(type)
                || (!node.has("price") && !node.has("side"))) {
              engine.cancelOrder(mapper.treeToValue(node, OrderCancelCommand.class)).join();
            } else {
              engine.processOrder(mapper.treeToValue(node, OrderPlacedEvent.class)).join();
            }
            count++;
          }
        }
      } catch (Exception ex) {
        throw new IllegalStateException(
            "Order book recovery failed; live processing remains disabled", ex);
      }
      liveConsumer.setLiveProcessingEnabled(true);
      return count;
    }
  }
}
