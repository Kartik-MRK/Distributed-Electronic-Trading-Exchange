package com.dete.matching.consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.common.domain.enums.*;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.publisher.MatchingEventPublisher;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

class OrderCommandConsumerTest {
  @Test
  void unknownProcessingFailureDisablesMatchingUntilRecovery() {
    var consumer =
        new OrderCommandConsumer(
            mock(MatchingEngineService.class),
            mock(MatchingEventPublisher.class),
            new ObjectMapper());
    consumer.setLiveProcessingEnabled(true);
    var ack = mock(Acknowledgment.class);
    assertThatThrownBy(
            () ->
                consumer.onOrderCommand(
                    new ConsumerRecord<>("order.commands", 0, 1L, "key", "not-json"), ack))
        .isInstanceOf(IllegalStateException.class);
    assertThat(consumer.isLiveProcessingEnabled()).isFalse();
    verifyNoInteractions(ack);
  }

  @Test
  void publicationRetryUsesOriginalResultWithoutRematching() throws Exception {
    var engine = new MatchingEngineService();
    var publisher = mock(MatchingEventPublisher.class);
    var mapper = new ObjectMapper().findAndRegisterModules();
    var consumer = new OrderCommandConsumer(engine, publisher, mapper);
    consumer.setLiveProcessingEnabled(true);
    var ack = mock(Acknowledgment.class);
    var order =
        OrderPlacedEvent.of(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instrument.BTC_USD,
            OrderSide.BUY,
            OrderType.LIMIT,
            100L,
            10L,
            UUID.randomUUID());
    var record =
        new ConsumerRecord<>("order.commands", 0, 7L, "BTC_USD", mapper.writeValueAsString(order));
    doThrow(new IllegalStateException("broker unavailable"))
        .doNothing()
        .when(publisher)
        .publishMatchResult(any());
    try {
      assertThatThrownBy(() -> consumer.onOrderCommand(record, ack))
          .isInstanceOf(IllegalStateException.class);
      verifyNoInteractions(ack);
      consumer.onOrderCommand(record, ack);
      var captor =
          org.mockito.ArgumentCaptor.forClass(com.dete.matching.engine.model.MatchResult.class);
      verify(publisher, times(2)).publishMatchResult(captor.capture());
      assertThat(captor.getAllValues().getFirst()).isSameAs(captor.getAllValues().getLast());
      assertThat(engine.getL2Depth(Instrument.BTC_USD, 10).join().bids().getFirst().volume())
          .isEqualTo(10);
      verify(ack).acknowledge();
    } finally {
      engine.shutdown();
    }
  }

  @Test
  void pausedConsumerNeverAcknowledgesSkippedCommands() {
    var consumer =
        new OrderCommandConsumer(
            mock(MatchingEngineService.class),
            mock(MatchingEventPublisher.class),
            new ObjectMapper());
    var ack = mock(Acknowledgment.class);
    consumer.setLiveProcessingEnabled(false);
    assertThatThrownBy(
            () ->
                consumer.onOrderCommand(
                    new ConsumerRecord<>("order.commands", 0, 1L, "key", "{}"), ack))
        .isInstanceOf(IllegalStateException.class);
    verifyNoInteractions(ack);
  }
}
