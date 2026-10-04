package com.dete.matching.replay;

import com.dete.common.events.order.OrderCancelCommand;
import com.dete.common.events.order.OrderModifyCommand;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.consumer.OrderCommandConsumer;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Service that reconstructs in-memory OrderBook state upon engine restart by replaying events from
 * the Kafka 'order.commands' log without emitting output events.
 */
@Service
public class KafkaReplayService {

  private static final Logger log = LoggerFactory.getLogger(KafkaReplayService.class);

  @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
  private String bootstrapServers;

  @Value("${matching.replay.on-startup:false}")
  private boolean replayOnStartup;

  private final MatchingEngineService matchingEngineService;
  private final OrderCommandConsumer orderCommandConsumer;
  private final ObjectMapper objectMapper;

  public KafkaReplayService(
      MatchingEngineService matchingEngineService,
      OrderCommandConsumer orderCommandConsumer,
      ObjectMapper objectMapper) {
    this.matchingEngineService = matchingEngineService;
    this.orderCommandConsumer = orderCommandConsumer;
    this.objectMapper = objectMapper;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onApplicationReady() {
    if (replayOnStartup) {
      log.info("Starting automatic Kafka event replay on startup...");
      replayFromBeginning();
    }
  }

  /**
   * Replays all messages from beginning of 'order.commands' up to current high watermarks. Returns
   * total number of commands replayed.
   */
  public int replayFromBeginning() {
    // Pause live consumer while replaying
    orderCommandConsumer.setLiveProcessingEnabled(false);
    matchingEngineService.resetAll();

    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "replay-group-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    int replayedCount = 0;

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      List<PartitionInfo> partitionInfos =
          consumer.partitionsFor(OrderCommandConsumer.TOPIC_ORDER_COMMANDS);
      if (partitionInfos == null || partitionInfos.isEmpty()) {
        log.info(
            "No partitions found for topic {}, replay complete.",
            OrderCommandConsumer.TOPIC_ORDER_COMMANDS);
        orderCommandConsumer.setLiveProcessingEnabled(true);
        return 0;
      }

      List<TopicPartition> partitions =
          partitionInfos.stream().map(p -> new TopicPartition(p.topic(), p.partition())).toList();

      consumer.assign(partitions);
      consumer.seekToBeginning(partitions);

      Map<TopicPartition, Long> endOffsets = consumer.endOffsets(partitions);
      boolean hasMore = true;

      while (hasMore) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
        if (records.isEmpty()) {
          // Check if all partitions reached end offsets
          boolean allCaughtUp = true;
          for (TopicPartition tp : partitions) {
            long currentPos = consumer.position(tp);
            long endPos = endOffsets.getOrDefault(tp, 0L);
            if (currentPos < endPos) {
              allCaughtUp = false;
              break;
            }
          }
          if (allCaughtUp) {
            hasMore = false;
          }
          continue;
        }

        for (ConsumerRecord<String, String> record : records) {
          try {
            JsonNode node = objectMapper.readTree(record.value());
            String eventType = node.has("eventType") ? node.get("eventType").asText() : "";

            if (OrderModifyCommand.EVENT_TYPE.equals(eventType)
                || (node.has("newPrice") && node.has("newQuantity"))) {
              OrderModifyCommand command = objectMapper.treeToValue(node, OrderModifyCommand.class);
              matchingEngineService.modifyOrder(command).join();
            } else if (OrderCancelCommand.EVENT_TYPE.equals(eventType)
                || (node.has("orderId")
                    && !node.has("price")
                    && !node.has("newPrice")
                    && !node.has("side"))) {
              OrderCancelCommand command = objectMapper.treeToValue(node, OrderCancelCommand.class);
              matchingEngineService.cancelOrder(command).join();
            } else {
              OrderPlacedEvent event = objectMapper.treeToValue(node, OrderPlacedEvent.class);
              matchingEngineService.processOrder(event).join();
            }
            replayedCount++;
          } catch (Exception e) {
            log.error(
                "Failed to replay message at partition {} offset {}",
                record.partition(),
                record.offset(),
                e);
          }
        }
      }

      log.info("Kafka replay completed successfully. Total commands replayed: {}", replayedCount);
    } catch (Exception e) {
      log.error("Error during Kafka event replay", e);
    } finally {
      orderCommandConsumer.setLiveProcessingEnabled(true);
    }

    return replayedCount;
  }
}
