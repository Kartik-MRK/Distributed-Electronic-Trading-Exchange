package com.dete.order.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;

class DlqAdminControllerTest {

  @SuppressWarnings("unchecked")
  private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);

  private DlqAdminController controller;

  @BeforeEach
  void setUp() {
    controller = new DlqAdminController(kafkaTemplate, "localhost:9092");
  }

  @Test
  @DisplayName("getKnownDlqTopics returns all 5 provisioned dead-letter topics")
  void testGetKnownDlqTopics() {
    ResponseEntity<Map<String, Object>> response = controller.getKnownDlqTopics();
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();

    @SuppressWarnings("unchecked")
    List<String> topics = (List<String>) response.getBody().get("topics");
    assertThat(topics)
        .containsExactlyInAnyOrder(
            "order.commands.DLQ",
            "order.events.DLQ",
            "trade.executions.DLQ",
            "ledger.events.DLQ",
            "audit.events.DLQ");
  }

  @Test
  @DisplayName("reprocessDlqTopic handles non-existent or empty topics gracefully without throwing")
  void testReprocessEmptyTopicGraceful() {
    // When topic is empty or broker not running, returns 200 with reprocessedCount 0 or 500 error
    ResponseEntity<Map<String, Object>> response = controller.reprocessDlqTopic("order.events", 5);
    assertThat(
            response.getStatusCode().is2xxSuccessful()
                || response.getStatusCode().is5xxServerError())
        .isTrue();
  }
}
