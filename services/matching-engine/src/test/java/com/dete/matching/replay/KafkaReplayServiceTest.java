package com.dete.matching.replay;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.dete.common.domain.enums.*;
import com.dete.common.events.order.OrderPlacedEvent;
import com.dete.matching.consumer.OrderCommandConsumer;
import com.dete.matching.publisher.MatchingEventPublisher;
import com.dete.matching.service.MatchingEngineService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

class KafkaReplayServiceTest {
  @Test
  void recoveryStopsAtCommittedWatermarkEvenIfPollReturnsNewCommands() throws Exception {
    var engine = new MatchingEngineService();
    var mapper = new ObjectMapper().findAndRegisterModules();
    var publisher = mock(MatchingEventPublisher.class);
    var live = new OrderCommandConsumer(engine, publisher, mapper);
    var replay = new KafkaReplayService(engine, live, mapper);
    var partition = new TopicPartition("order.commands", 0);
    try (var kafka = new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)) {
      kafka.updateBeginningOffsets(Map.of(partition, 0L));
      kafka.updateEndOffsets(Map.of(partition, 2L));
      var committed =
          OrderPlacedEvent.of(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              100L,
              10L,
              UUID.randomUUID());
      var uncommitted =
          OrderPlacedEvent.of(
              UUID.randomUUID(),
              UUID.randomUUID(),
              Instrument.BTC_USD,
              OrderSide.BUY,
              OrderType.LIMIT,
              100L,
              20L,
              UUID.randomUUID());
      String first = mapper.writeValueAsString(committed);
      String second = mapper.writeValueAsString(uncommitted);
      kafka.schedulePollTask(
          () -> {
            kafka.addRecord(new ConsumerRecord<>(partition.topic(), 0, 0L, "BTC_USD", first));
            kafka.addRecord(new ConsumerRecord<>(partition.topic(), 0, 1L, "BTC_USD", second));
          });
      assertThat(replay.reconstruct(kafka, Map.of(partition, 1L))).isEqualTo(1);
      assertThat(engine.getL2Depth(Instrument.BTC_USD, 10).join().bids().getFirst().volume())
          .isEqualTo(10);
      assertThat(live.isLiveProcessingEnabled()).isTrue();
      verifyNoInteractions(publisher);
    } finally {
      engine.shutdown();
    }
  }

  @Test
  void missingHistoryAndMalformedCommandsKeepTradingDisabled() {
    var engine = new MatchingEngineService();
    var mapper = new ObjectMapper();
    var live = new OrderCommandConsumer(engine, mock(MatchingEventPublisher.class), mapper);
    var replay = new KafkaReplayService(engine, live, mapper);
    var partition = new TopicPartition("order.commands", 0);
    try (var kafka = new MockConsumer<String, String>(OffsetResetStrategy.EARLIEST)) {
      kafka.updateBeginningOffsets(Map.of(partition, 10L));
      kafka.updateEndOffsets(Map.of(partition, 11L));
      assertThatThrownBy(() -> replay.reconstruct(kafka, Map.of(partition, 11L)))
          .isInstanceOf(IllegalStateException.class);
      assertThat(live.isLiveProcessingEnabled()).isFalse();
      kafka.updateBeginningOffsets(Map.of(partition, 0L));
      kafka.updateEndOffsets(Map.of(partition, 1L));
      kafka.schedulePollTask(
          () ->
              kafka.addRecord(
                  new ConsumerRecord<>(partition.topic(), 0, 0L, "BTC_USD", "invalid")));
      assertThatThrownBy(() -> replay.reconstruct(kafka, Map.of(partition, 1L)))
          .isInstanceOf(IllegalStateException.class);
      assertThat(live.isLiveProcessingEnabled()).isFalse();
    } finally {
      engine.shutdown();
    }
  }
}
