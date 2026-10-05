package com.dete.order.controller;

import com.dete.order.consumer.OrderEventConsumer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrative DLQ management endpoint allowing operations and regulatory compliance teams
 * to inspect poisoned/failed Kafka records with failure metadata headers and replay them back
 * to production topics.
 */
@RestController
@RequestMapping("/admin/dlq")
public class DlqAdminController {

  private static final Logger log = LoggerFactory.getLogger(DlqAdminController.class);

  public static final List<String> KNOWN_DLQ_TOPICS =
      List.of(
          "order.commands.DLQ",
          "order.events.DLQ",
          "trade.executions.DLQ",
          "ledger.events.DLQ",
          "audit.events.DLQ");

  private final KafkaTemplate<String, String> kafkaTemplate;
  private final String bootstrapServers;

  public DlqAdminController(
      KafkaTemplate<String, String> kafkaTemplate,
      @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
    this.kafkaTemplate = kafkaTemplate;
    this.bootstrapServers = bootstrapServers;
  }

  @GetMapping("/topics")
  public ResponseEntity<Map<String, Object>> getKnownDlqTopics() {
    return ResponseEntity.ok(
        Map.of(
            "topics", KNOWN_DLQ_TOPICS,
            "count", KNOWN_DLQ_TOPICS.size()));
  }

  @GetMapping("/messages")
  public ResponseEntity<Map<String, Object>> peekDlqMessages(
      @RequestParam(defaultValue = "10") int maxMessages) {
    return peekTopicMessages(OrderEventConsumer.TOPIC_ORDER_EVENTS_DLQ, maxMessages);
  }

  @GetMapping("/{topic}")
  public ResponseEntity<Map<String, Object>> peekDlqTopic(
      @PathVariable String topic,
      @RequestParam(defaultValue = "20") int maxMessages) {
    String dlqTopic = topic.endsWith(".DLQ") ? topic : topic + ".DLQ";
    return peekTopicMessages(dlqTopic, maxMessages);
  }

  private ResponseEntity<Map<String, Object>> peekTopicMessages(String topicName, int maxMessages) {
    Properties props = createConsumerProps();
    List<Map<String, Object>> messages = new ArrayList<>();

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      TopicPartition partition = new TopicPartition(topicName, 0);
      consumer.assign(Collections.singletonList(partition));
      consumer.seekToBeginning(Collections.singletonList(partition));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("key", record.key());
        entry.put("value", record.value());
        entry.put("partition", record.partition());
        entry.put("offset", record.offset());
        entry.put("timestamp", record.timestamp());

        Map<String, String> headers = new LinkedHashMap<>();
        for (Header h : record.headers()) {
          if (h.value() != null) {
            headers.put(h.key(), new String(h.value(), StandardCharsets.UTF_8));
          }
        }
        entry.put("headers", headers);
        messages.add(entry);

        if (messages.size() >= maxMessages) {
          break;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to peek DLQ messages from topic {}", topicName, e);
    }

    return ResponseEntity.ok(
        Map.of(
            "topic", topicName,
            "count", messages.size(),
            "messages", messages));
  }

  @PostMapping("/reprocess")
  public ResponseEntity<Map<String, Object>> reprocessDlqMessages(
      @RequestParam(defaultValue = "10") int maxMessages) {
    return reprocessTopicMessages(
        OrderEventConsumer.TOPIC_ORDER_EVENTS_DLQ, OrderEventConsumer.TOPIC_ORDER_EVENTS, maxMessages);
  }

  @PostMapping("/{topic}/reprocess")
  public ResponseEntity<Map<String, Object>> reprocessDlqTopic(
      @PathVariable String topic,
      @RequestParam(defaultValue = "20") int maxMessages) {
    String dlqTopic = topic.endsWith(".DLQ") ? topic : topic + ".DLQ";
    String targetTopic = topic.endsWith(".DLQ") ? topic.substring(0, topic.length() - 4) : topic;
    return reprocessTopicMessages(dlqTopic, targetTopic, maxMessages);
  }

  private ResponseEntity<Map<String, Object>> reprocessTopicMessages(
      String dlqTopic, String targetTopic, int maxMessages) {
    Properties props = createConsumerProps();
    int reprocessed = 0;

    try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
      TopicPartition partition = new TopicPartition(dlqTopic, 0);
      consumer.assign(Collections.singletonList(partition));
      consumer.seekToBeginning(Collections.singletonList(partition));

      ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
      for (ConsumerRecord<String, String> record : records) {
        kafkaTemplate.send(targetTopic, record.key(), record.value());
        reprocessed++;
        if (reprocessed >= maxMessages) {
          break;
        }
      }
    } catch (Exception e) {
      log.error("Failed to reprocess DLQ messages from {} to {}", dlqTopic, targetTopic, e);
      return ResponseEntity.internalServerError()
          .body(Map.of("status", "FAILED", "error", e.getMessage()));
    }

    log.info("Reprocessed {} messages from DLQ {} back to {}", reprocessed, dlqTopic, targetTopic);
    return ResponseEntity.ok(
        Map.of(
            "status", "SUCCESS",
            "sourceTopic", dlqTopic,
            "targetTopic", targetTopic,
            "reprocessedCount", reprocessed));
  }

  private Properties createConsumerProps() {
    Properties props = new Properties();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlq-admin-" + UUID.randomUUID());
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
    return props;
  }
}
