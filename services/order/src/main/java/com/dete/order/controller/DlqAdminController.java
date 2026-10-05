package com.dete.order.controller;

import com.dete.order.consumer.OrderEventConsumer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin/dlq")
public class DlqAdminController {

  private static final Logger log = LoggerFactory.getLogger(DlqAdminController.class);

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String bootstrapServers;

  public DlqAdminController(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    this.kafkaTemplate = kafkaTemplate;
    this.bootstrapServers = bootstrapServers;
  }

  @GetMapping("/messages")
  public ResponseEntity<Map<String, Object>> peekDlqMessages(
      @RequestParam(defaultValue = "10") int maxMessages) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-peek-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    List<String> messages = new ArrayList<>();
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      TopicPartition partition = new TopicPartition(OrderEventConsumer.TOPIC_ORDER_EVENTS_DLQ, 0);
      consumer.assign(Collections.singletonList(partition));
      consumer.seekToBeginning(Collections.singletonList(partition));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        messages.add(record.value());
        if (messages.size() >= maxMessages) {
          break;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to peek DLQ messages", e);
    }

    return ResponseEntity.ok(
        Map.of(
            "topic",
            OrderEventConsumer.TOPIC_ORDER_EVENTS_DLQ,
            "count",
            messages.size(),
            "messages",
            messages));
  }

  @PostMapping("/reprocess")
  public ResponseEntity<Map<String, Object>> reprocessDlqMessages(
      @RequestParam(defaultValue = "10") int maxMessages) {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-reprocessor-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");

    int reprocessed = 0;
    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      TopicPartition partition = new TopicPartition(OrderEventConsumer.TOPIC_ORDER_EVENTS_DLQ, 0);
      consumer.assign(Collections.singletonList(partition));
      consumer.seekToBeginning(Collections.singletonList(partition));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        // Republish to main topic
        kafkaTemplate.send(OrderEventConsumer.TOPIC_ORDER_EVENTS, record.key(), record.value());
        reprocessed++;
        if (reprocessed >= maxMessages) {
          break;
        }
      }
    } catch (Exception e) {
      log.error("Failed to reprocess DLQ messages", e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "FAILED", "error", e.getMessage()));
    }

    log.info(
        "Reprocessed {} messages from DLQ back to {}",
        reprocessed,
        OrderEventConsumer.TOPIC_ORDER_EVENTS);
    return ResponseEntity.ok(
        Map.of(
            "status",
            "SUCCESS",
            "reprocessedCount",
            reprocessed,
            "targetTopic",
            OrderEventConsumer.TOPIC_ORDER_EVENTS));
  }
}
