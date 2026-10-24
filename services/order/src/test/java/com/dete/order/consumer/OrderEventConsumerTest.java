package com.dete.order.consumer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.dete.order.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;

class OrderEventConsumerTest {
  @Test
  void failedDlqPublicationMustNotAcknowledgeSourceRecord() {
    KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
    when(kafka.send(any(ProducerRecord.class)))
        .thenReturn(
            CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));
    Acknowledgment ack = mock(Acknowledgment.class);
    OrderEventConsumer consumer =
        new OrderEventConsumer(mock(OrderService.class), kafka, new ObjectMapper());
    assertThatThrownBy(
            () ->
                consumer.onOrderEvent(
                    new ConsumerRecord<>("order.events", 0, 1L, "order", "invalid JSON"), ack))
        .isInstanceOf(IllegalStateException.class);
    verify(ack, never()).acknowledge();
  }
}
